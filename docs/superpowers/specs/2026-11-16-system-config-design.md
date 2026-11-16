# 系统配置读取模块设计

用户授权开发系统配置读取模块，为减碳系数等功能提供数据接口。沿用 sys_config 原表与三个初始键，不新增配置写入、业务计算或依赖。

## 数据与接口

- entity.SysConfig 是不可变 final 类，构造器 `(String configKey,String configValue,LocalDateTime updatedAt)`；提供 getConfigKey/getConfigValue/getUpdatedAt。key 非空白，value 非 null（允许空串），updatedAt 非 null，非法抛 IllegalArgumentException。
- toString 仅输出键和更新时间，不输出配置值，避免未来配置内容意外进入调试输出。
- dao.SysConfigDao 提供 `Optional<SysConfig> findByKey(String key)` 与 `List<SysConfig> findAll()`，均有 Connection 首参数重载并声明 throws DataAccessException。
- 键按字面值精确匹配，大小写和空格均有意义，不 trim；null/空白键抛 IllegalArgumentException。列表按 config_key ASC 排序，为不可修改列表；无记录返回空列表或 Optional.empty。
- config_value 原样返回，空串与不存在区分，不自动把缺失、非法数字、布尔值等替换为默认值。Service 必须判断系数非负、有限、格式正确，备份天数等也由使用方校验。
- impl.SysConfigDaoImpl 复用 BaseDao/DBUtil，支持默认与注入 DBUtil 构造器；DaoFactory.getSysConfigDao() 为惰性单例入口，不持有长连接。
- 无缓存，普通调用每次读取当前已提交数据库；读事务可通过 Connection 重载获得一致快照。DAO 不关闭、不提交、不回滚或更改外部连接的事务状态。
- updated_at 使用项目秒精度时间格式，严格解析；非法持久化字段转为 SQLException，再包装为 DataAccessException，原始 cause 保留。

## 实现选择与边界

选择独立只读配置 DAO，保留配置原文，避免把减碳/备份规则放进数据层。没有采用内存常量代替数据库，也没有增加全局缓存，以便后续修改数据库配置后读取到新值。
不增加 typed getter、ConfigService、管理员配置入口、写接口、sys_config 触发器、CO₂/金额计算或备份功能。此为当前读取模块的实现范围，不声称 FR-16 整体已完成。
原 SQLite `TEXT PRIMARY KEY` 可能允许 null 键；读取时将其视为非法持久化数据并报数据访问异常，不修改原 DDL。
沿用本地 feature/iteration2-data 和既有未提交内容，不提交或推送 Git。
