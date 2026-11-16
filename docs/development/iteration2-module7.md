# 迭代二模块七 统计数据层

本模块实现分类统计、年度月份统计、按状态计数和平台概览，供 StatService 与统计界面接入。
所有方法只读，原数据库表、视图和初始化 SQL 保持不变。

## 数据口径

物品数来自 item，每件物品计一次，按当前 ON_SALE/OFF_SHELF/FLOWED 状态分组。
意向记录数来自 flow_record，每条记录计一次，区分 PENDING/DEAL/CANCELLED。
一件物品的多个意向不会增加物品数。展示成交数量应使用 dealFlowCount，不能把包含待确认和取消的 flowCount 当作成交件数。

分类按物品当前直接归属分组，每个实际分类返回一行，包括零值分类。
父分类不重复汇总子分类，避免把各行相加时重复计数。若界面需要一级分类汇总，可由 Service 结合 CategoryDao 的 parentId 另行计算。
存在无分类物品或其流转记录时，额外返回 categoryId 为 null、名称为“未分类”的组。
分类删除后原业务数据不会在统计中消失。分类名称与归属是当前数据，不是历史分类快照。

时间口径：

| 数据 | 使用时间 |
| --- | --- |
| 物品当前状态的数量 | 物品 created_at，表示该时间段发布的物品当前处于何种状态 |
| 待确认意向 | flow_record.created_at |
| 成交或取消记录 | finished_at；旧数据缺失完成时间时退回 created_at |

因此，某物品在去年发布、今年成交时，物品发布数量属于去年，成交记录数量属于今年。
月报是按上述事件时间归月的当前数据统计，不是每个月末的历史库存快照。
系统尚未保存完整状态变更历史，无法凭当前表还原任意过去日期的库存。

## 接口与值对象

四个冻结普通方法保持原签名：

| 方法 | 返回 |
| --- | --- |
| `countByCategory()` | List<CategoryStat>，分类排序与分类字典一致，未分类组最后 |
| `countByMonth(String year)` | List<MonthStat>，固定按 1–12 月排列，空月份补零 |
| `countItems(ItemStatus status)` | int，null 状态表示物品总数 |
| `countFlows(FlowStatus status)` | int，null 状态表示全部意向记录数 |

每个方法新增 StatQuery 范围参数重载；新增 getOverview() 与 getOverview(StatQuery)。
十个普通方法都提供 Connection 首参数重载，支持同一事务中的一致读取。
工厂入口为 `DaoFactory.getStatDao()`。

`CategoryStat` 包含 categoryId、categoryName、counts。
`MonthStat` 包含 YearMonth 类型的 month 和 counts，可用 `getMonth().toString()` 显示 yyyy-MM。
`StatOverview` 包含 counts、userCount、averageCreditScore。
上述值对象及返回列表不可变。

共享的 StatCounts 提供：

```text
getOnSaleItemCount()       在架物品
getOffShelfItemCount()     下架物品
getFlowedItemCount()       已流转物品
getItemCount()             以上三类物品之和
getPendingFlowCount()      待确认意向
getDealFlowCount()         成交记录
getCancelledFlowCount()    取消记录
getFlowCount()             以上三类意向记录之和
```

值对象数量使用 long，禁止负数和总数溢出。
原 countItems/countFlows 的 int 返回类型保留；超出 int 范围时明确抛数据访问异常，不静默截断。

## 范围筛选与调用

```java
StatDao stats = DaoFactory.getStatDao();
StatQuery query = StatQuery.builder()
        .from(LocalDate.of(2026, 1, 1))
        .to(LocalDate.of(2026, 12, 31))
        .userId(2)
        .build();

List<CategoryStat> categories = stats.countByCategory(query);
List<MonthStat> months = stats.countByMonth("2026", query);
StatOverview overview = stats.getOverview(query);
long deals = overview.getCounts().getDealFlowCount();
int onSale = stats.countItems(ItemStatus.ON_SALE, query);
```

StatQuery 为不可变 Builder 快照，from、to、userId 都可以为 null。
日期采用闭区间，沿用项目秒精度，从开始日 00:00:00 到结束日 23:59:59；只指定一端时另一端不限。
年份须为 0001–9999 的四位 ASCII 数字；非法年份、倒置日期、非正用户编号和 null 查询对象被拒绝。
年度月报与额外日期范围取交集，交集为空仍返回十二个零值月份。

userId 为 null 表示全平台。指定用户时：物品限定其发布的物品，流转限定其作为买家或卖家参与的记录。
该用户参与购买别人的物品时，其流转记录会计入，但那件物品不会算作他发布的物品。
Service 应从登录身份决定个人范围，并校验查看全平台数据的权限，不能仅信任界面传来的 userId。

概览中的用户数和平均信用为当前账号快照，包括管理员与禁用账号。
这两个指标仅随 userId 范围变化，不随物品/流转时间范围变化；平均信用与评价星级不同。
无用户时返回 0 和 0.0。若后续需要“期间新注册用户数”，应另加明确指标，不能复用当前用户数。

## 一致性与资源管理

分类查询将物品与流转分别聚合后合并，防止一对多联表造成倍增。
每份分类结果、月份结果和概览均由单条 SQL 读取，保证该结果内部使用同一数据库快照。
不同普通方法使用独立连接；需要多份结果共享快照时，Service 应使用同一连接的读事务：

```java
try (Connection connection = DBUtil.getInstance().getConnection()) {
    connection.setAutoCommit(false);
    try {
        StatOverview overview = stats.getOverview(connection, query);
        List<CategoryStat> categories = stats.countByCategory(connection, query);
        List<MonthStat> months = stats.countByMonth(connection, "2026", query);
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

DAO 不提交、回滚或关闭外部连接，也可在 SQLite query_only 连接上运行。
年份、日期、用户和状态值全部绑定到 PreparedStatement，SQL 构造只组合固定片段。

## 交接边界

- 本轮补充了 CategoryStat/MonthStat 的字段、StatCounts、StatQuery、StatOverview 与范围接口，应同步给成员 2、3。
- 未实现 StatService、StatReport 组装、CSV 文件导出、减碳系数读取/估算和活跃用户排行。
- 后续减碳估算须明确使用成交流转或已流转物品指标，不能误用全部意向数。
- 操作日志数据层已在后续模块八实现；统计功能的业务权限与界面集成仍需对应负责人完成。

## 验证

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

新增统计 DAO 测试 17 项、值对象测试 3 项，加上既有 142 项，共 162 项。
覆盖多意向计数、零值分类/月份、未分类、创建与完成时间、跨年、闰日、闭区间边界、个人范围与空库。
同时验证范围参数、年份输入、只读连接、外部事务可见性和回滚恢复。
独立只读审查无阻塞问题；补充验证了仅流转命中的未分类组、空范围无额外组，以及 0001/9999 年实际数据和末日边界。
