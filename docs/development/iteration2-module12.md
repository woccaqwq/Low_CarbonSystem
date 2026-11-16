# 迭代二模块十二 运行日志工具

本模块使用 JDK 自带 java.util.logging，提供独立 AppLogger、日文件 Handler 与安全格式化器。与数据库 operation_log 业务日志分开，不自动拦截已有 DAO、Service 或 GUI。
原设计工具名 Logger 在实现中命名为 AppLogger，避免与 JDK Logger 混淆；没有增加依赖、修改数据库结构或原异常接口。

## 创建与调用

```java
AppLogger logs = new AppLogger(); // logs 目录、本地时区、DEBUG 关闭
AppLogger custom = new AppLogger(Path.of("data", "runtime-logs"));
AppLogger development = new AppLogger(Path.of("dev-logs"), Clock.systemDefaultZone(), true);
```

三个构造器均 throws IOException，目录不存在时创建。不同实例必须使用不同目录；同一目录只允许一个持锁实例，第二个实例或进程会收到 IOException。
App 应持有并在退出时关闭一个实例，不要每次业务调用都创建新实例。示例中的三个对象仅展示构造方式，实际程序只创建所需实例并管理生命周期。

```java
try (AppLogger logs = new AppLogger(Path.of("logs"))) {
    logs.log(AppLogger.Level.INFO, App.class, "startup", null, "APP_START", "SUCCESS");
    logs.log(AppLogger.Level.WARN, ItemServiceImpl.class, "publish", userId, "ITEM_PUBLISH", "REJECTED");
    logs.log(AppLogger.Level.ERROR, SomeService.class, "load", userId, "DB_LOAD", "FAILURE", originalFailure);
}
```

App/Service 类与 originalFailure 等变量是接入示意，当前仓库尚未实现这些业务入口。
log 有不带 Throwable 和末尾带 Throwable 的两个重载，均 throws IOException。level、source、method、action、result 不可为 null，后三项不可空白；userId 为正整数或 null（输出 SYSTEM）。非法元数据抛 IllegalArgumentException。
close 可重复调用；关闭后再次 log 抛 IllegalStateException，即使该条是被过滤的 DEBUG。

## 级别与格式

| 应用级别 | JUL | 用途 |
| --- | --- | --- |
| DEBUG | FINE | 开发诊断，默认关闭，需构造时显式开启 |
| INFO | INFO | 登录、发布、成交等关键动作的安全摘要 |
| WARN | WARNING | 业务拒绝、可恢复异常 |
| ERROR | SEVERE | 需要人工介入的故障，例如数据库损坏、初始化失败 |

工具不自动判定严重程度，调用方按上述规则选择。独立匿名 JUL Logger 禁用父 Handler，不修改 root logger、其他模块级别或全局日志配置。
每条事件同步写入并 flush，UTF-8 编码，示例：

```text
2026-11-16 09:02:03.456 INFO class="sample.ItemService" method="publish" user=2 action="ITEM_PUBLISH" result="SUCCESS"
```

时间使用该事件一次获取的 Clock instant 和其时区，文件日期与记录日期一致。字符串用引号包围，反斜杠、引号、CR/LF/TAB、控制字符与孤立代理项转为字面转义；元数据中的换行不会生成伪造记录。
异常额外输出类型、全部栈帧、cause/suppressed 与循环引用标记，默认不输出异常消息或 Throwable.toString，减少原始 SQL/口令出现在异常消息中的风险。
这不等于自动识别任意秘密：action/result/method 仍须使用固定代码、安全状态和摘要，不传口令、哈希、盐、表单、完整 SQL 或个人参数。人为改写的栈帧内容也不是自动脱敏对象。

## 日期轮转与七天清理

文件名为 `app-yyyyMMdd.log`，同日追加，跨日关闭旧文件再打开新文件，不截断旧日志。默认目录为运行目录下 logs。
七天以自然日定义：当天及前六天保留。以 2026-11-16 为例，保留 11-10 至 11-16，删除 11-09 及更早的匹配普通日志。
启动及下一条记录触发日期变化时清理，不使用后台定时器；应用未运行或无新记录时，不保证在零点立即清理。

只删除当前配置目录内、名字严格匹配 `app-八位有效日期.log` 的过期普通文件。不递归、不跟随符号链接，不删除未来日期、无效日期、目录、其他文件或 .bak 文件。
目录中的 `.app-log.lock` 是文件锁载体，关闭后保留为空文件；锁本身会释放。它不属于七天日志，也不应在另一进程运行时手工删除。
本模块没有大小轮转、压缩、定时后台清理、远程收集或防篡改功能；不保证掉电时未落盘缓存的保存。

## 故障与业务边界

构造、写入、轮转、过期清理或关闭失败会抛 IOException，没有交给 JUL 默认 ErrorManager 静默处理。目录独占由 FileLock 保证，同实例串行写入，测试覆盖多线程记录不交错。
路径故障恢复后可重试打开日志文件；写入/flush 的中途故障可能留下不完整尾部，本模块不把每条追加当作文件事务。关闭时尽力关闭 writer、释放锁和通道，后续错误附为 suppressed。

记录原业务故障时，避免日志失败覆盖原异常：

```java
catch (RuntimeException originalFailure) {
    try {
        logs.log(AppLogger.Level.ERROR, SomeService.class, "load", userId,
                "DB_LOAD", "FAILURE", originalFailure);
    } catch (IOException loggingFailure) {
        originalFailure.addSuppressed(loggingFailure);
    }
    throw originalFailure;
}
```

文件日志不是数据库事务的一部分。业务已提交后发生日志 I/O 故障，应由调用方按独立日志故障处理，不能把已成功提交的业务误报为回滚。业务 operation_log 仍由 Service 使用同一数据库连接提交，不能由文件日志替代。
当前没有向 App/DAO/Service 自动安装日志，也没有全局 GUI 异常处理或运行日志查看界面；需后续负责人接入与验收。

## 验证

新增 AppLoggerTest 17 项，先确认 AppLogger 缺失导致失败，再实现并验证；审查后追加一项故障回归：

- UTF-8 元数据、默认关闭 DEBUG、四级映射、系统用户和字段转义。
- 异常类型/栈/cause/suppressed/循环引用，且不输出消息中的测试秘密。
- 本地时区跨午夜、同日重新打开追加、七天边界和跨日清理。
- 无关/未来/无效日期/目录保护、目录锁互斥与关闭释放。
- 四线程 100 条记录、写入/轮转路径失败后的恢复、关闭状态、非法参数和 root logger 不变。
- 目录遍历的 DirectoryIteratorException 还原为 IOException，保留流关闭失败的 suppressed 信息。

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

全量 package 验证通过：此前 236 项及新增 17 项，共 253 项，失败 0、错误 0、跳过 0。独立审查指出目录遍历错误可能绕过 IOException 契约，已通过先失败、后通过的故障注入回归修复。
已注入 DirectoryStream 读取与关闭错误；实际磁盘、writer/锁/通道 close 失败仍以代码审查为主，不声称覆盖全部文件系统故障。

## Git 提交约定

用户在本模块明确要求每完成两个模块提交一次。此前模块尚未提交，本模块通过后先建立覆盖模块一至十二的统一本地检查点；后续每两个模块（十四、十六……）再提交一次。不自动推送，具体状态见 [Git 检查点记录](git-checkpoints.md)。
