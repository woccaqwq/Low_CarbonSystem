# 迭代二模块九 会话快照与业务异常

本模块补齐 SessionUser、ErrorCode、BusinessException 与 ValidationException，供 Service 与 GUI 使用。现有 DAO 和 DataAccessException 保持兼容，没有变更数据库结构或增加依赖。

## 会话快照

`SessionUser` 是 final 不可变类，包含 userId、username、nickname、role、creditScore 五个字段，提供同名 getXxx()、isAdmin()。构造器为：

```java
SessionUser(int userId, String username, String nickname, Role role, int creditScore)
```

userId 须为正整数，username 非空白，nickname 非 null（可为空串），role 非 null，creditScore 在 0–150 之间；非法字段抛 IllegalArgumentException。
也可使用 `SessionUser.from(User)`，null User 被拒绝，userId 为 0 的未保存用户被拒绝。
转换只复制上述字段，没有哈希、盐、明文口令或 User 引用；原 User 改名、改角色、改信用不会影响旧快照。没有 setter。

Service 完成口令验证、禁用检查等登录步骤后再创建快照：

```java
// authenticatedUser 已由登录业务完成身份与账号状态校验。
SessionUser session = SessionUser.from(authenticatedUser);
String nickname = session.getNickname();
boolean showAdminMenu = session.isAdmin();
```

构造器和 from 都不是认证或授权操作：有合法编号不等于数据库存在该用户，能构造对象也不等于已登录。from 不检查账号 status。SessionUser 不取代 SessionManager，亦不保存登录状态。
isAdmin() 可供界面控制菜单，受限业务仍须基于 Service 管理的当前身份并重新读取账号，校验角色、禁用状态及资源所有权。不能信任界面传入的一个 SessionUser 就授予权限。
信用、角色、昵称发生变化后，业务层应重新读取 User 并替换快照；旧快照不会自动更新或失效。

## 错误码

ErrorCode 位于 `com.campus.recycle.exception`，`getMessage()` 返回固定中文提示，`name()` 可用于稳定的内部标识。保留原设计第 8.2 节十项和事务示例里的两项，新增通用 VALIDATION_ERROR。

| 错误码 | 固定提示 |
| --- | --- |
| USER_NOT_FOUND | 用户名或密码错误 |
| PASSWORD_ERROR | 用户名或密码错误 |
| USER_DISABLED | 账号已被禁用，请联系管理员 |
| USERNAME_DUPLICATED | 该用户名已被占用 |
| ITEM_NOT_ON_SALE | 该物品已下架或已流转 |
| ITEM_OWN_SELF | 不能对自己发布的物品发起意向 |
| FLOW_DUPLICATED | 你已对该物品发起过意向 |
| FLOW_STATUS_ILLEGAL | 当前状态不支持该操作 |
| PERMISSION_DENIED | 没有操作权限 |
| REVIEW_NOT_ALLOWED | 当前不可评价：未成交或已评价 |
| FLOW_NOT_FOUND | 流转记录不存在 |
| DB_ERROR | 数据操作失败，请联系管理员 |
| VALIDATION_ERROR | 输入参数不合法，请检查后重试 |

登录名不存在与口令错误提示相同，界面不要再展示差异化内部错误码或 cause。
REVIEW_NOT_ALLOWED 保留原设计的通用提示；具体的成交状态、参与人、7 天窗口等规则仍由 ReviewService 校验，不由枚举自动判断。

## 业务异常与字段定位

```text
RuntimeException
  BusinessException
    ValidationException
  DataAccessException
```

BusinessException 支持：

```java
new BusinessException(ErrorCode.PERMISSION_DENIED);
new BusinessException(ErrorCode.DB_ERROR, originalFailure);
```

通过 `getCode()` 获取枚举，`getMessage()` 只返回该枚举固定提示。code 不能为 null，否则抛 NullPointerException；cause 可为 null，非 null 时原样保留。
ValidationException 是其子类，支持以下四个构造器：

```java
new ValidationException(ErrorCode.VALIDATION_ERROR);
new ValidationException(ErrorCode.VALIDATION_ERROR, originalFailure);
new ValidationException("username", ErrorCode.USERNAME_DUPLICATED);
new ValidationException("price", ErrorCode.VALIDATION_ERROR, originalFailure);
```

`getField()` 返回固定字段标识（如 username、price），或 null 表示整个表单。显式传入 null 字段也表示整个表单；空串或纯空白字段抛 IllegalArgumentException。
field 应由代码填写固定标识，不得填入用户输入值。字段标识不拼进 message，界面可据此定位控件并保留表单。没有字段级自定义消息或自动校验器。

GUI 应先处理 ValidationException 的字段定位，再处理一般 BusinessException；DataAccessException 单独给出 DB_ERROR 的通用提示。不要向用户展示 cause、堆栈或底层 SQL。
保留 cause 不代表其内容已经脱敏；记录日志时仍需避免口令和个人信息，不能直接把任意异常原因写入 operation_log.detail。

## 与数据访问异常及事务的关系

DataAccessException 继续使用现有 `(String message, Throwable cause)` 构造器，它不是 BusinessException 的子类。DAO 失败不会因为新增 ErrorCode 就自动转换为业务错误码。
原设计示例中的 `new DataAccessException(ErrorCode.DB_ERROR, cause)` 不是现有签名；需要构造时可显式传入 `ErrorCode.DB_ERROR.getMessage()`，一般情况下应保留 DAO 已抛出的原异常。
Service 若有统一业务边界，可显式包装 `new BusinessException(ErrorCode.DB_ERROR, failure)`；这由接入层选择，本模块未做全局异常转换。

事务仍按 [验收记录中的更正](iteration2-data-acceptance.md) 捕获 `RuntimeException | SQLException` 并回滚，新业务异常与校验异常也属于该回滚范围。不能只捕获 SQLException，或在未回滚时恢复自动提交。

## 验证与范围

先运行测试确认缺失四个类导致失败，再实现并验证新增 18 项测试：SessionUser 6 项、ErrorCode 3 项、BusinessException 4 项、ValidationException 5 项。
测试覆盖源对象变更后的快照独立性、角色、信用边界、无凭据结构与调试输出、真实 DAO 用户转换、登录错误提示一致、原因保留、字段定位及非法参数。

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

完整 package 验证通过：原 182 项与本模块 18 项合计 200 项，失败 0、错误 0、跳过 0。
独立只读审查通过，没有待修复问题；四个新增类型已打入 JAR，字节码目标为 Java 17。
尚未实现登录 Service、SessionManager、权限控制、GUI、运行时文件日志、统计业务或 CSV 导出，新增类型也不会自动把现有界面或业务接上。
本模块接口扩展及 VALIDATION_ERROR 需同步给成员 2、3；本轮未提交或推送 Git，也未修改迭代一原 Word 与 UML。
