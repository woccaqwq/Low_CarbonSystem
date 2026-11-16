package com.campus.recycle.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.logging.LogRecord;

/** 独立 JUL 运行日志门面；调用方负责安全元数据、错误处理和应用退出时关闭。 */
public final class AppLogger implements AutoCloseable {
    /** 与设计中的四个级别对应。 */
    public enum Level {
        DEBUG(java.util.logging.Level.FINE), INFO(java.util.logging.Level.INFO),
        WARN(java.util.logging.Level.WARNING), ERROR(java.util.logging.Level.SEVERE);
        private final java.util.logging.Level jul;
        Level(java.util.logging.Level jul) { this.jul = jul; }
    }

    private final Clock clock;
    private final java.util.logging.Logger delegate;
    private final DailyLogHandler handler;
    private boolean closed;

    /** @throws IOException 无法创建或独占默认 logs 目录 */
    public AppLogger() throws IOException { this(Path.of("logs")); }
    /** @param directory 日志目录，默认本地时区、DEBUG 关闭 @throws IOException 目录或锁不可用 */
    public AppLogger(Path directory) throws IOException { this(directory, Clock.systemDefaultZone(), false); }
    /** @param directory 日志目录 @param clock 时钟与时区 @param debugEnabled 是否启用 DEBUG
     * @throws IOException 日志目录或文件锁不可用 */
    public AppLogger(Path directory, Clock clock, boolean debugEnabled) throws IOException {
        if (directory == null || clock == null) { throw new IllegalArgumentException("日志目录和时钟不能为空"); }
        this.clock = clock;
        handler = new DailyLogHandler(directory, clock);
        delegate = java.util.logging.Logger.getAnonymousLogger();
        delegate.setUseParentHandlers(false);
        delegate.setLevel(debugEnabled ? java.util.logging.Level.FINE : java.util.logging.Level.INFO);
        delegate.addHandler(handler);
    }

    /** @param level 级别 @param source 类 @param method 方法 @param userId 用户或 null（SYSTEM）
     * @param action 安全动作代码 @param result 安全结果摘要 @throws IOException 写入或轮转失败 */
    public void log(Level level, Class<?> source, String method, Integer userId, String action, String result)
            throws IOException { log(level, source, method, userId, action, result, null); }

    /** @param level 级别 @param source 类 @param method 方法 @param userId 用户或 null（SYSTEM）
     * @param action 安全动作代码 @param result 安全结果摘要 @param failure 可空异常，仅记录类型与栈
     * @throws IOException 写入、轮转或过期清理失败 */
    public synchronized void log(Level level, Class<?> source, String method, Integer userId, String action,
                                 String result, Throwable failure) throws IOException {
        if (closed) { throw new IllegalStateException("运行日志已关闭"); }
        if (level == null || source == null || blank(method) || blank(action) || blank(result)
                || (userId != null && userId <= 0)) { throw new IllegalArgumentException("日志元数据不合法"); }
        if (!delegate.isLoggable(level.jul)) { return; }
        LogRecord record = new LogRecord(level.jul, result);
        record.setInstant(clock.instant());
        record.setSourceClassName(source.getName());
        record.setSourceMethodName(method);
        record.setParameters(new Object[]{userId, action});
        record.setThrown(failure);
        try { delegate.log(record); }
        catch (UncheckedIOException problem) { throw problem.getCause(); }
    }

    /** 关闭并释放目录锁，重复调用无操作。@throws IOException 关闭失败，其他资源仍尝试释放 */
    @Override
    public synchronized void close() throws IOException {
        if (closed) { return; }
        closed = true;
        delegate.removeHandler(handler);
        try { handler.close(); }
        catch (UncheckedIOException problem) { throw problem.getCause(); }
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
}
