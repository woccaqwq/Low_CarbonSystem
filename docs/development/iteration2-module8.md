# 迭代二模块八 操作日志数据层

本模块实现 operation_log 的实体、追加写入、最近记录、编号查询和筛选分页，供业务 Service 与管理员界面接入。
沿用迭代一表结构与初始数据，没有修改初始化 SQL。

## 接口与字段

工厂入口为 `DaoFactory.getOperationLogDao()`，测试可使用 `new OperationLogDaoImpl(database)` 注入独立数据库。

| 方法 | 返回与约定 |
| --- | --- |
| `insert(OperationLog log)` | 新主键；不回填输入对象，每次调用都追加新记录 |
| `findRecent(int limit)` | 最近日志 List，limit 1–1000 |
| `findById(int logId)` | Optional，查不到为空，编号须为正整数 |
| `findByPage(Integer userId, String action, int pageNo, int pageSize)` | PageResult；页码 1 起，页大小 10–100 |

四个方法均提供以 `Connection` 为首参数的重载，连接归调用方管理。
用户筛选与动作筛选可组合，各自为 null 表示该维度不限；动作精确匹配，空白动作被拒绝。
所有列表按 `created_at DESC, log_id DESC` 排序，保证同秒记录排序稳定；分页偏移用 long 计算。
分页总数与列表是两次查询；若要求两者严格使用同一快照，应使用外部连接的读事务。
当前接口不单独筛选“所有匿名日志”，null userId 表示不限操作人。

OperationLog 字段：

| 字段 | 含义 |
| --- | --- |
| logId | 主键，未保存为 0 |
| userId | Integer，正整数或 null；系统、匿名及已删除用户可为 null |
| action | 非空白动作代码，建议 Service 使用固定常量 |
| detail | 非 null 安全摘要，默认空串 |
| createdAt | LocalDateTime，null 使用数据库本地当前时间；显式时间保存到秒 |
| username / nickname | 操作人当前展示信息，LEFT JOIN 查询投影，不参与写入 |

用户删除时，外键 ON DELETE SET NULL 保留日志、动作、摘要和时间，操作人编号被置空。
用户名与昵称不是历史快照；用户改名会改变展示，删除后两个投影均为 null。
日志接口只提供追加与查询，不提供修改或删除；这不等于数据库具备防篡改或审计认证能力。

## 同事务接入

普通方法使用独立连接并自动关闭；`insert(log)` 自身成功并不代表其他业务操作也成功。
关键业务变更与日志应使用同一连接，由 Service 统一提交或回滚。
下面演示管理员下架；调用前应完成登录身份、管理员权限和业务状态校验：

```java
ItemDao items = DaoFactory.getItemDao();
OperationLogDao logs = DaoFactory.getOperationLogDao();
try (Connection connection = DBUtil.getInstance().getConnection()) {
    connection.setAutoCommit(false);
    try {
        int changed = items.updateStatus(connection, itemId, ItemStatus.OFF_SHELF);
        if (changed != 1) {
            throw new IllegalStateException("物品不存在");
        }
        logs.insert(connection, new OperationLog(adminUserId, "ADMIN_ITEM_OFFLINE", "物品编号=" + itemId));
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

DAO 不提交、回滚或关闭外部连接；日志插入失败会抛出 DataAccessException，保留原 SQLException 原因，不能在 Service 中吞掉后继续提交。
查询可运行在 SQLite query_only 连接上。

## 安全输入与交接

日志表用于保存关键业务事件；Service 可在登录、发布、下架、成交、评价和禁用账号等成功路径接入。
目前新增 DAO 不会自动记录已有 DAO 的调用，业务日志的实际写入需由对应 Service 接入。
管理员查看权限也须由 Service 检查，DAO 本身不认证身份。

建议使用固定动作代码与对象编号，例如 `ITEM_PUBLISH` / `物品编号=6`。
禁止把口令、密码哈希、盐、完整用户表单、原始 SQL 或其个人信息参数写入 action/detail。
参数绑定可以避免 SQL 注入，不能代替隐私处理；DAO 原样保存文本，不承诺自动识别或脱敏任意敏感内容。
实体 toString 省略 detail，但 action 仍须使用安全代码。

本模块只实现数据库中的业务日志。设计说明书中按天写入 `logs/app-yyyyMMdd.log`、保留七天及日志级别的运行时文件日志尚未实现。
AdminService、登录/发布等 Service 接入和管理员日志界面不在本模块范围内。

## 验证

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

本模块新增 DAO 测试 14 项、实体测试 3 项；与此前 162 项合计 179 项。
覆盖初始数据、追加及重新打开数据库后的持久化、匿名操作人、用户改名后的当前投影与删除后保留日志、精确组合筛选、特殊字符串、同秒稳定分页与大偏移。
事务测试覆盖提交前隔离、共同提交、日志失败后业务回滚，以及日志成功但后续业务失败时一起撤销。
同时验证外部连接生命周期、只读连接、关闭连接错误与非法参数。
独立只读审查无阻塞问题；依据审查建议补充了用户改名后已写日志展示当前名称的验证。
