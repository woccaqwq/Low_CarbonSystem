# 会话快照与业务异常设计

本模块补齐验收记录中列出的 SessionUser、ErrorCode、BusinessException、ValidationException，供成员 2 的 Service 和成员 3 的界面使用。用户已授权继续开发此模块；沿用迭代一设计与现有 Java 17 工程，不另行引入框架或依赖。

## 约定

- SessionUser 位于 entity，final 类、五个 final 字段：int userId、String username、String nickname、Role role、int creditScore；构造函数按此顺序，提供五个 getter 与 isAdmin()。
- public static SessionUser from(User user) 只复制上述五个字段，不保留 User 引用、口令、哈希或盐；源对象后续变化不影响快照。
- userId 必须为正整数，username 非空白，nickname 非 null（允许空串），role 非 null，creditScore 0–150。非法字段抛 IllegalArgumentException，from(null) 抛 NullPointerException。
- 会话快照只作数据投影，不表示已认证。from 不检查口令或账号状态；Service 完成登录和禁用校验后再创建，并在重要操作前重新检查权限与账号状态。信用更新后须重新读取并替换快照。
- ErrorCode 位于 exception，保留第 8.2 节十个名称及事务示例的 FLOW_NOT_FOUND、DB_ERROR，新增通用 VALIDATION_ERROR，共十三个枚举值；getMessage() 返回固定中文提示。USER_NOT_FOUND 与 PASSWORD_ERROR 使用相同提示。
- BusinessException extends RuntimeException，构造 (ErrorCode)、(ErrorCode,Throwable)，getCode() 返回错误码；null code 抛 NullPointerException。对外消息只取枚举固定提示，不拼接 cause 或输入值，cause 原样保留。
- ValidationException extends BusinessException，构造 (ErrorCode)、(ErrorCode,Throwable)、(String field,ErrorCode)、(String field,ErrorCode,Throwable)，getField() 返回固定字段标识或 null（整个表单）；非 null 字段不可空白，非法抛 IllegalArgumentException。字段与值均不拼入提示。
- 不改现有 DataAccessException(String,Throwable)、DAO、数据库结构或异常转换行为。DB_ERROR 可用于边界统一提示；DataAccessException 仍是独立技术异常，不自动变成 BusinessException。

## 实现选择

采用与现有实体 getter 风格一致的不可变 final 类，而非可变 User 子集，避免字段漂移及源对象变更影响会话。也未采用 record，因为现有调用契约与界面使用 getXxx()/isAdmin()。
异常沿用原设计的错误码构造器，不增加接收任意用户输入的自定义消息构造器。字段定位只扩展 ValidationException，不给所有业务异常增加无关表单字段。
这些为本轮实现选择，未声称小组三方已确认接口变更。

## 验证和范围

先用 JUnit 验证快照独立性、角色、边界、没有凭据或 User 引用、真实 DAO 查询结果转换；验证固定提示、登录失败提示一致、异常继承、字段定位与原因保留。
SessionUser 是内存快照；异常类仅定义契约。登录、SessionManager、Service、GUI、运行时日志、统计及 CSV 不属于本模块。
保留现有分支与未提交改动，本轮不提交或推送。
