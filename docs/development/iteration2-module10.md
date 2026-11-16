# 迭代二模块十 系统配置读取

本模块读取 sys_config，为后续统计系数、备份策略等使用方提供数据库配置原文。增加第九个 DAO，不改变原八张表、初始化数据或已有接口。

## 初始配置

| 配置键 | 初始原文 | 使用方仍须处理的含义 |
| --- | --- | --- |
| db.backup.enabled | true | 是否启用备份 |
| db.backup.keep_days | 7 | 备份保留天数 |
| stat.co2.per_item_kg | 2.4 | 每件已流转物品折算的 kg CO₂ 系数 |

读取到开关、天数不代表备份已实现；读取到系数不代表 FR-16 减碳估算已完成。当前没有配置写接口、管理员编辑界面或隐式默认值。

## 实体与接口

SysConfig 是不可变 final 类，包含 configKey、configValue、updatedAt，提供三个 getXxx()。构造器为 `(String configKey, String configValue, LocalDateTime updatedAt)`，key 非空白、value 非 null（可为空串）、时间非 null；非法字段抛 IllegalArgumentException。
toString 省略 value，避免未来的敏感配置值进入普通调试描述；直接记录 getConfigValue() 仍须由调用方判断内容是否适合记录。

工厂入口 `DaoFactory.getSysConfigDao()`；测试使用 `new SysConfigDaoImpl(database)` 注入独立 DBUtil。

| 方法 | 返回 |
| --- | --- |
| findByKey(String key) | Optional<SysConfig>，不存在为空 |
| findAll() | List<SysConfig>，按 config_key ASC 排序，不可修改 |
| findByKey(Connection connection, String key) | 同上，使用调用方连接 |
| findAll(Connection connection) | 同上，使用调用方连接 |

键按字面精确匹配，大小写与空格都有意义，不去除两端空白。null、空串、纯空白查询键被拒绝。SQL 值通过 PreparedStatement 绑定。
值以原文出现在查询结果中，空串、字符串 "NaN" 或 "yes" 不会被转换成“未配置”；缺失项才返回 Optional.empty。小型配置表读取全部，不做分页。

updated_at 按项目秒精度时间格式严格解析。非法日期、空白/NULL 键等坏数据抛 DataAccessException，cause 保留 SQLException。原 SQLite TEXT PRIMARY KEY 可允许 NULL，本模块只在映射时拒绝该坏数据，没有变更 DDL。

## 调用示例与类型校验

```java
SysConfigDao configs = DaoFactory.getSysConfigDao();
Optional<SysConfig> setting = configs.findByKey("stat.co2.per_item_kg");
List<SysConfig> allSettings = configs.findAll();
```

后续 StatService/配置使用方需要自行明确缺失或损坏配置的错误策略：

1. 必需系数不存在时给出受控故障提示，不能把缺失项静默当作 0 或写死 2.4。
2. 将原文解析为 BigDecimal 等适当类型，拒绝非法数字和负系数；若使用 double，还必须拒绝 NaN 和正负无穷。
3. 使用已流转物品或按明确口径的成交数估算，不能乘以全部待确认/已取消意向数。
4. 备份布尔值应明确接受 true/false，不能用会把任意坏字符串变成 false 的宽松解析掩盖配置错误；天数也须校验格式与允许范围。

这些规则不是 DAO 的自动行为，本模块未新增配置错误码、数值转换器或减碳计算器。

## 刷新、快照与事务

本 DAO 没有缓存。每次普通调用打开独立连接，读取当前已提交的数据；已返回的配置对象和列表是旧快照，不会在数据库修改后自动变化，需要重新查询。
需要配置与统计共用同一数据库快照时，Service 可使用同一连接的读事务：

```java
try (Connection connection = DBUtil.getInstance().getConnection()) {
    connection.setAutoCommit(false);
    try {
        Optional<SysConfig> coefficient = configs.findByKey(connection, "stat.co2.per_item_kg");
        StatOverview overview = DaoFactory.getStatDao().getOverview(connection);
        // 在业务层校验配置并组装结果。
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

DAO 不关闭、不提交、不回滚调用方连接，不更改自动提交状态，支持 query_only 连接。显式读事务在其他连接提交配置后仍可读取旧快照，结束该事务后才能看到新值。
普通方法各有自己的快照；不能依靠先后两次普通查询保证配置与报表属于同一时点。

原 sys_config 没有更新时间触发器。将来增加配置写入时，写入方需校验并显式维护 updated_at，本模块只是读取数据库现有时间，不自动推进更新时间。

## 验证与交接

新增 DAO 测试 17 项、实体测试 3 项，先确认缺失实现导致失败，再实现并验证。
覆盖三项初始配置、空表、空串与缺失区分、特殊字符串、大小写/空格、中文原文、不可修改列表、持久化及无缓存刷新。
同时验证外部事务可见性、回滚、并发提交后的稳定读快照、query_only、关闭连接故障、坏存量键/日期，以及映射失败不回滚调用方此前写入。

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

全量 package 验证通过：原 200 项加本模块 20 项，共 220 项，失败 0、错误 0、跳过 0。
独立只读审查通过，没有待修复问题；新增实体、接口和实现已打入 JAR，字节码目标为 Java 17。
新增配置接口需同步给统计与备份功能的负责人；当前仅具备读取能力，后续 Service、数值校验、估算/导出、备份与配置修改仍需各自实现。
原 Word/UML 保持迭代一版本；当前代码仍在本地分支，本轮未提交或推送 Git。
