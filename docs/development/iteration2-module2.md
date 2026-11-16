# 迭代二模块二 用户数据与口令工具

本模块提供用户实体、八个用户 DAO 操作及口令派生工具，供成员 2 实现 UserService。
注册校验、登录失败提示、禁用判断、登录锁定与管理员权限仍由业务层实现。

## 已实现内容

| 文件 | 用途 |
| --- | --- |
| `entity/User.java` | sys_user 对应字段，默认学生、正常、信用 100；不保存明文口令 |
| `entity/enums/Role.java` | STUDENT、ADMIN，与数据库文本值一致 |
| `entity/PageResult.java` | 分页元数据与不可修改的列表快照；其中 User 本身仍可变 |
| `util/PasswordUtil.java` | 生成盐、派生哈希、校验口令，兼容三个初始账号 |
| `dao/UserDao.java` | 冻结的八个普通方法与八个 Connection 首参数重载 |
| `dao/impl/BaseDao.java` | 参数绑定、结果映射模板、资源释放、SQL 异常包装 |
| `dao/impl/UserDaoImpl.java` | 用户插入、查询、更新、分页的 JDBC 实现 |
| `dao/factory/DaoFactory.java` | 通过接口返回默认 UserDao 单例 |

## 业务层调用

```java
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.User;
import com.campus.recycle.util.PasswordUtil;

UserDao users = DaoFactory.getUserDao();
PasswordUtil passwords = new PasswordUtil();

// Service 先校验用户名、口令强度和昵称，再调用工具与 DAO。
String salt = passwords.generateSalt();
String hash = passwords.hash("example123", salt);
int userId = users.insert(new User("example_user", hash, salt, "示例同学"));
User saved = users.findById(userId).orElseThrow();
boolean passwordMatches = passwords.verify("example123", saved.getPasswordHash(), saved.getSalt());
```

口令参数顺序：`hash(rawPassword, salt)`，`verify(rawPassword, hash, salt)`。
每次注册或改密调用 `generateSalt()` 生成新盐，同时更新哈希和盐。
插入返回生成的主键，不修改传入 User 的 userId，避免外部事务回滚后对象保留无效编号。

PBKDF2 参数沿用迭代一：`PBKDF2WithHmacSHA256`、100000 次、256 位输出、16 字节随机盐。
盐与哈希均为 Base64；比较采用 `MessageDigest.isEqual`。
哈希入口对空口令、非法盐抛 `IllegalArgumentException`；验证入口遇到缺失或损坏的凭据返回 false。
工具不检查密码强度，也不删除口令两端空格，确保已有账号验证兼容。

## DAO 返回值

| 方法 | 返回值和语义 |
| --- | --- |
| `findById(int)` | Optional，查不到返回空 |
| `findByUsername(String)` | 按原字符串精确匹配，SQL 注入字符串按普通输入绑定 |
| `insert(User)` | 新主键；未指定创建时间时使用数据库默认时间 |
| `updatePassword(int,String,String)` | 同一 SQL 更新哈希和盐，返回受影响行数 |
| `updateCreditScore(int,int)` | 原子增加信用分并限制在 0–150，返回行数 |
| `updateStatus(int,int)` | 0 正常、1 禁用；权限检查由 Service 负责 |
| `updateLastLogin(int,LocalDateTime)` | 非空时间，保存精度为秒 |
| `findByPage(String,int,int)` | 用户名/昵称包含搜索，按 user_id 升序，返回 PageResult<User> |

更新不存在的用户返回 0。重复用户名等 SQL 约束错误包装为 `DataAccessException`，原始 SQLException 在 cause 中。
成员 2 可据此转换重复注册错误；禁止把原始 SQL 错误直接显示给用户。
User 内部包含哈希和盐，应仅用于认证与持久化；其 toString 不输出凭据，界面与导出也不得输出凭据字段。

分页页码从 1 开始，pageSize 10–100。DAO 拒绝非法分页参数，Service 可按设计先校正并记录。
关键词 null、空串或纯空白表示全部；其他关键词会去除两端空白。
百分号、下划线和反斜杠均按字面量匹配，不作为 LIKE 通配符；引号通过参数绑定处理。
页码超出总页数时保留请求页码、总数，返回空列表。offset 使用 long，避免极大页码溢出。

## 外部事务

普通方法只管理自己的独立连接。需要多个写操作同时成功时，必须在同一连接上调用 Connection 重载：

```java
try (Connection connection = DBUtil.getInstance().getConnection()) {
    connection.setAutoCommit(false);
    try {
        users.updateCreditScore(connection, userId, 1);
        users.updateLastLogin(connection, userId, LocalDateTime.now());
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

DAO 不提交、回滚或关闭传入连接，只释放自己的 Statement 和 ResultSet。
事务内应继续使用同一连接查询；调用普通方法会打开另一条连接，看不到尚未提交的写入。

## 接口变更记录

本轮保留冻结的八个普通方法，新增八个 Connection 首参数重载，以落实设计说明书第 8.3 节。
该扩展需要在小组交接时通知成员 2；其内存 DAO 或测试替身也需要实现对应方法。
本轮未修改 SQLite 表结构和原有 SQL 脚本。
公共 BaseDao 使用按操作传入固定 SQL、子类实现 mapRow 的模板，支持同一个 DAO 的多种查询。

实体按分层约束不实现依赖口令工具的 `User.login(rawPwd)`；口令校验通过 PasswordUtil，由 UserService 编排。
`User.adjustCredit(delta)` 只调整内存对象；持久化信用分需调用 DAO 的原子更新方法。

## 测试与当前边界

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" test
```

已在实现前运行新增测试，确认缺失实现导致失败，再实现并验证。
模块一 12 项、PasswordUtil 15 项、User 4 项、PageResult 3 项、UserDaoImpl 15 项，共 49 项通过。
覆盖初始账号、非法凭据、数据回读、时间精度、重复用户名、参数化查询、字面量筛选、分页极值、信用分溢出及外部事务回滚/提交。

本模块不提供 UserService 或登录界面。下一模块建议实现分类与物品数据层，复用 BaseDao 和 PageResult。
