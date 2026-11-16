# 运行日志工具设计

用户授权开发运行日志，并要求每完成两个模块提交一次 Git。当前模块十二完成、验证与审查后统一提交此前未提交的迭代二成果，随后按两个模块一个本地提交点执行，不自动推送。

## 接口与架构

- public final util.AppLogger implements AutoCloseable，避免与 JDK Logger 重名；使用独立匿名 java.util.logging.Logger，不修改 root logger 或全局配置。
- 构造器 AppLogger()、AppLogger(Path directory)、AppLogger(Path directory,Clock clock,boolean debugEnabled)，均 throws IOException；默认 logs、系统本地区域时钟、DEBUG 关闭。
- 内嵌 public enum Level { DEBUG,INFO,WARN,ERROR } 映射 JUL FINE/INFO/WARNING/SEVERE。
- public void log(Level,Class<?> source,String method,Integer userId,String action,String result) throws IOException，以及末尾带 Throwable 的重载。userId 为正整数或 null（SYSTEM），其他元数据非 null/非空白；非法参数抛 IllegalArgumentException。
- public close() throws IOException，可重复调用；关闭后日志调用抛 IllegalStateException，包括被级别过滤的 DEBUG。
- 每次同步写入并 flush，按该事件时刻和 Clock 时区决定日期和文件。默认 DEBUG 过滤，测试/开发显式启用。所有级别仍由业务选择，不自动判断故障严重度。
- 每条记录含本地时间（毫秒）、级别、类名、方法、用户、动作和结果；字符串使用引号及控制字符转义，避免换行伪造记录。非法孤立代理项转义为字面 \uXXXX。
- 异常打印类型及全部调用栈、cause/suppressed 关系，循环引用标记，默认不输出 Throwable 消息或 toString，避免原始 SQL/口令混进日志。元数据仍须由调用方使用安全动作/摘要；不声称能自动识别任意秘密或篡改的栈帧内容。

## 文件与生命周期

- package-private DailyLogHandler 负责 UTF-8 追加 logs/app-yyyyMMdd.log、轮转和清理，SafeLogFormatter 负责固定格式，不依赖 Service/DAO。
- 创建目录，持有 .app-log.lock 的文件锁，阻止同目录多个实例/进程相互干扰；锁文件保留为空文件，关闭释放文件锁和通道。
- 启动及日志日期切换时清理：保留事件日期当天及前六个自然日，删除更早且名称严格为 app-八位有效日期.log 的普通文件。不递归、不跟随符号链接、不删除未来日期、无效日期、目录或其他文件。
- 无后台定时器；应用未运行或没有新记录时，不承诺在零点立即清理。无文件大小上限、压缩、网络收集、备份功能或防篡改保证。
- I/O 故障向调用方抛 IOException，不由 JUL ErrorManager 静默吞掉；Handler 内使用 UncheckedIOException 传递给 facade 后还原。失败后可在路径问题修复后重试，关闭尽力释放全部资源并保留 suppressed 异常。
- 一次事件只取一个 Clock instant，避免日期与时间跨零点不一致。单实例串行写入，禁止多个实例共享目录。

## 接入范围与选择

采用 JUL + 自定义日文件 Handler，而非 FileHandler 按大小轮转，落实固定日期文件名和七天窗口。默认生产不输出 DEBUG，不引入日志框架依赖。
此为运行时日志，与 operation_log 业务日志分开；不自动拦截已有 DAO/Service，不写数据库，不替代管理员业务审计。
App/Service 后续负责持有、关闭、选择级别、安全摘要，并在已有业务异常时保留日志失败为 suppressed，避免覆盖原故障。当前不增加 GUI 全局异常捕获或启动入口。
