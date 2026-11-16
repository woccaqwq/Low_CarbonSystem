# 迭代二模块一 数据库连接与初始化

本模块将迭代一的 SQLite 设计接入 Java 工程，为后续 DAO 提供可配置、可测试的连接工厂。
已实现连接管理、首次初始化和数据访问异常，验证入口为 JUnit。

## 实现范围

| 文件 | 职责 |
| --- | --- |
| `pom.xml` | Java 17 编译目标、SQLite JDBC、JUnit 5、资源与构建插件 |
| `DBUtil` | 默认单例、指定路径工厂、新连接、外键开关、锁等待、失败时关闭连接 |
| `DatabaseInitializer` | 空库识别、脚本加载、初始化事务、数据库版本及必要对象检查 |
| `DataAccessException` | 包装 IO 与 SQL 故障，并保留原始 cause |
| `DBUtilTest` | 独立临时数据库上的初始化、持久化、约束及故障测试 |

## 调用方式

默认连接工厂在首次访问时读取 `campus.db.path` 系统属性，默认路径为 `data/campus.db`，相对运行工作目录解析。
每次调用获得独立连接；工厂本身不保存长连接。

```java
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;

try (Connection connection = DBUtil.getInstance().getConnection()) {
    // 后续调用 DAO，使用 PreparedStatement 绑定业务输入。
}
```

测试或需要注入数据库路径的模块使用独立工厂：

```java
DBUtil database = DBUtil.forPath(Path.of("data/test-campus.db"));
try (Connection connection = database.getConnection()) {
    // 由调用方关闭 connection。
}
```

每个连接均设置 `PRAGMA foreign_keys = ON` 和 `PRAGMA busy_timeout = 5000`。
首次建库启用 WAL。数据库路径指向文件；当前不支持 `:memory:` JDBC 地址。

## 首次初始化与现有数据保护

1. 自动创建数据库父目录并打开 SQLite 文件。
2. 读取 `PRAGMA user_version`。本模块的数据库结构版本为 1，区别于 Maven 工程版本。
3. 版本为 0 且没有用户创建的数据库对象时，在同一个事务中执行建表与初始数据脚本，并写入版本 1。
4. 版本为 1 时检查八张业务表、列表视图和两个触发器是否存在，保留所有业务数据。
5. 遇到未标版本的已有库、更高版本或缺失必要对象时抛异常，交由人工确认或后续迁移处理。

`初始化脚本/` 中的 SQL 是唯一脚本源，构建时作为 UTF-8 资源直接复制到 classpath 的 `sql/` 目录。
迭代一脚本含有清库命令，只在确定为空库时执行。程序不会在日常启动时重跑它们。
脚本内独立的外键、WAL 和事务命令由 Java 统一管理，初始化失败时结构与初始数据一并回滚。
使用 SQLite JDBC 整段执行脚本，保留触发器内部的分号。

当前只做数据库版本识别，不自动迁移旧库。通过命令行手动执行迭代一脚本建立的库没有版本标记，不能直接当作程序初始化完成的库使用。

## 与后续模块的接口约定

- DAO 的普通方法可向工厂获取连接，并自行关闭所获取的连接。
- 跨 DAO 的原子操作由 Service 获取一个连接、开启事务，并将同一连接传给相关 DAO。
- 接收外部连接的 DAO 方法只关闭自身的 Statement 和 ResultSet，不关闭、提交或回滚外部连接。
- 后续 DAO 实现需要补充接收 `Connection` 的重载；保留设计文档中已有普通方法签名，便于队友调用。
- 本模块尚未新增 DAO 签名；正式添加时应与成员 2 核对并记录变更。
- `getConnection()` 使用非受检 `DataAccessException` 包装 IO/SQL 故障。原始异常保存在 cause，界面应使用通用错误提示，调用边界负责记录日志。

## 测试与验收

在仓库根目录运行：

```powershell
mvn "-Dmaven.repo.local=.mvn-cache" test
```

本模块的 12 个 JUnit 测试已通过，失败、错误、跳过数均为 0。
测试使用 `@TempDir` 下的独立库，不写默认业务库。

| 场景 | 验证内容 |
| --- | --- |
| 首次启动 | 八张业务表、视图、初始数据及中文字符读取正确 |
| 重启 | 修改保留，初始账号没有重复插入 |
| 连接配置 | 每个新连接启用外键、锁等待和 WAL |
| 触发器 | 修改物品更新时间、结束流转写入完成时间 |
| 业务事务 | 中途约束失败后，流转和物品修改一起回滚 |
| 测试隔离 | 不同文件之间数据互不影响 |
| 未标版本旧库 | 拒绝自动重建，原数据保留 |
| 不兼容版本 | 拒绝打开，原版本和数据保留 |
| 结构不完整 | 抛异常，保留剩余对象和数据 |
| 初始化失败 | 外键错误导致全部回滚，修正后可重新初始化 |
| 路径故障 | 抛数据访问异常并保留 IO 原因 |
| 多线程首次打开 | 本进程串行初始化，只生成一份初始数据 |

## 本机 SQLite 工具

已发现并验证现有工具：`D:\Software\SQLite\sqlite-tools-win-x64-3500100\sqlite3.exe`，版本 3.50.1。
工具目录与应用数据库目录相互独立；工具不作为 Maven 工程运行依赖。
在项目库实际生成后，可使用只读方式检查：

```powershell
& 'D:\Software\SQLite\sqlite-tools-win-x64-3500100\sqlite3.exe' -readonly '.\data\campus.db' '.tables'
```

SQLite JDBC 目前固定为 3.47.1.0；命令行工具使用独立的 SQLite 版本，项目通过测试验证数据库行为。

## 下一模块

用户数据模块已实现，见 [模块二说明](iteration2-module2.md)。后续分类与物品 DAO 可复用其参数绑定与结果映射基础能力。
