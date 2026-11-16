# 迭代二模块五 流转记录数据层

本模块实现流转意向存取、状态条件更新、带原因取消和历史查询，为成员 2 的 FlowService 提供数据接口。
复用原 flow_record 表及 finished_at 触发器，不修改初始化脚本，不实现 GUI 或完整业务层。

## 实体与状态

`FlowRecord` 对应 flow_id、item_id、seller_id、buyer_id、flow_type、status、remark、created_at、finished_at。
额外提供 itemTitle、sellerNickname、buyerNickname 当前查询投影，不写入流转表，也不是交易发生时的不可变快照。
交易方式复用 TradeType。新实体默认为 PENDING、EXCHANGE，留言为空串，完成时间为 null。

`FlowStatus`：PENDING（待确认）只能转到 DEAL（已成交）或 CANCELLED（已取消），两个终态不能再转移。
实体 `confirm()`、`cancel(reason)` 校验当前状态并修改内存状态与完成时间，不自动保存。
实体不允许卖家和买家相同；完整权限、卖家与物品所有者一致、交易方式匹配等规则由 Service 校验。

## DAO 方法

五个冻结的普通方法保持原签名：

| 方法 | 行为 |
| --- | --- |
| `insert(FlowRecord)` | 创建 PENDING 意向，返回新主键，不修改传入对象 |
| `findById(int)` | 查询完整记录与当前展示投影，查不到返回 Optional.empty |
| `updateStatus(int,FlowStatus)` | 将 PENDING 条件更新为 DEAL/CANCELLED，返回受影响行数 |
| `existsPending(int itemId,int buyerId)` | 按物品与买家检查待确认意向 |
| `findByUser(int userId,FlowStatus)` | 返回用户作为买家或卖家参与的历史 List，null 状态不限 |

本轮补充的方法：

| 方法 | 用途 |
| --- | --- |
| `cancelOtherPending(int itemId,int exceptFlowId)` | 已成交后取消同物品的其他 PENDING 意向 |
| `cancel(int flowId,String reason)` | 状态与原因同一 SQL 更新，原因必须非空白 |
| `findByUser(int,FlowStatus,int pageNo,int pageSize)` | 双方参与历史的分页重载 |
| `findByBuyer(int,FlowStatus,int pageNo,int pageSize)` | “我发起的”分页 |
| `findBySeller(int,FlowStatus,int pageNo,int pageSize)` | “我收到的”分页 |

十个方法全部提供 Connection 首参数重载；DAO 不关闭、提交或回滚外部连接。
工厂入口：`DaoFactory.getFlowRecordDao()`。

## 新增、重试与状态冲突

`insert` 仅用于创建未完成意向，status 必须为 PENDING、finishedAt 必须为 null。
可指定创建时间，保存到秒；null 使用数据库当前时间。
同物品同买家已有 PENDING 时，单条条件 INSERT 拒绝重复创建，抛 DataAccessException。
取消后的历史保留，原意向不再 PENDING 时可以重新创建。
初始 SQL 中已有的成交记录照常读取；本方法不是历史数据导入接口。

`updateStatus` 仅接受 DEAL/CANCELLED；PENDING 或 null 目标抛 IllegalArgumentException。
记录不存在、已经结束，或者同物品已存在另一条 DEAL 时，成交更新返回 0，不覆盖完成时间。
状态条件直接在 SQL 中执行，Service 必须检查受影响行数，不能把 0 当作成功。

`cancelOtherPending` 只有在 exceptFlowId 存在、属于 itemId 且已 DEAL 时才执行。
保留记录无效、属于其他物品或尚未成交时返回 0；不会误取消该物品的所有意向。
正常调用返回 0 也可能只是没有其他意向，因此不应把此方法的 0 一概当作成交失败。
它不会修改其他物品、已成交或已取消记录。

## 取消原因与完成时间

优先使用 `cancel(flowId,reason)` 持久化用户主动取消的原因。
原留言非空时保存为 `原留言 + 换行 + 取消原因：原因`，原留言为空时不加开头换行。
原因使用参数绑定，中文、引号等保留为普通文本；终态重复调用返回 0，不重复附加原因。
沿用已有 remark 存储原因，避免额外数据库迁移；交接时需同步此文本约定。

基础 `updateStatus(...,CANCELLED)` 与自动取消方法只改状态，不自动添加原因。
DEAL/CANCELLED 的数据库完成时间由原有触发器维护，精度为秒。
重试不会改变终态和原完成时间。

## 历史查询

```java
FlowRecordDao flows = DaoFactory.getFlowRecordDao();
PageResult<FlowRecord> sent = flows.findByBuyer(userId, null, 1, 20);
PageResult<FlowRecord> received = flows.findBySeller(userId, FlowStatus.PENDING, 1, 20);
List<FlowRecord> all = flows.findByUser(userId, null);
```

历史包含已成交和已取消记录，关闭连接或重启后仍保留。
状态筛选作用于整个“买家或卖家”条件，避免 OR 优先级造成状态过滤遗漏。
所有历史按 created_at DESC、flow_id DESC 排序；页码从 1 开始，每页 10–100，offset 使用 long。
用户没有记录或请求页超出范围时返回空列表，分页仍保留总数与请求页码。
建议界面使用分页方法；原 List 方法为保持冻结契约而保留。

## 成交数据事务示例

以下展示成员 2 在 Service 内如何使用同一连接。角色权限、账号状态等业务校验应由 Service 完成；
信用分规则、日志与提交后的事件通知也由 Service 结合其他 DAO 编排。

```java
try (Connection connection = DBUtil.getInstance().getConnection()) {
    connection.setAutoCommit(false);
    try {
        FlowRecord record = flows.findById(connection, flowId)
                .orElseThrow(() -> new IllegalStateException("记录不存在"));
        if (record.getSellerId() != operatorId) {
            throw new IllegalStateException("没有确认权限");
        }
        if (!items.existsOnSale(connection, record.getItemId())) {
            throw new IllegalStateException("物品已下架或已流转");
        }
        if (flows.updateStatus(connection, flowId, FlowStatus.DEAL) != 1) {
            throw new IllegalStateException("流转状态已变化");
        }
        if (items.updateStatus(connection, record.getItemId(), ItemStatus.FLOWED) != 1) {
            throw new IllegalStateException("物品更新失败");
        }
        flows.cancelOtherPending(connection, record.getItemId(), flowId);
        connection.commit();
    } catch (RuntimeException | SQLException failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        throw failure;
    }
}
```

事务读取也应使用同一 Connection，普通方法会打开另一条连接。
需要捕获 RuntimeException（包括 DataAccessException）后回滚，不能只捕获 SQLException。
业务层应将示例中的通用异常转换为小组约定的业务错误码和用户提示。

## 接口补充与当前边界

- 保留五个普通方法，补充连接重载与上述五项能力，填补原事务模板缺少 cancelOtherPending 的问题。
- 不改变 FlowService 既定职责，不在 DAO 中判断调用者身份、物品所有权或登录权限。
- DAO 提供状态条件更新、重复待确认意向和同物品重复成交的保护；Service 仍须校验前置条件并管理完整事务。
- 新增查询投影与取消原因存储约定需要同步给成员 2、3；原 schema 不变。
- 评价、统计与操作日志数据层尚待开发。

## 验证记录

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

新增实体测试 6 项、DAO 测试 17 项，加上既有 99 项，共 122 项。
覆盖记录映射与持久化、重复意向、终态保护、成交唯一性、取消范围、原因参数绑定、买卖方历史与分页。
故障测试通过临时触发器模拟第二步物品更新失败，以及后续外键失败，验证调用方回滚能够恢复记录、物品和其他意向。
独立只读审查未发现阻塞问题；按审查建议强化了“保留记录属于另一物品时不得误取消”的测试夹具。
