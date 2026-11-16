package com.campus.recycle.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** 每个目录只允许一个持锁实例；启动和日期切换时执行严格限定的过期清理。 */
final class DailyLogHandler extends Handler {
    private final Path directory;
    private final ZoneId zone;
    private final FileChannel lockChannel;
    private final FileLock directoryLock;
    private BufferedWriter writer;
    private LocalDate openDate;
    private boolean closed;

    DailyLogHandler(Path directory, Clock clock) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        zone = clock.getZone();
        setLevel(java.util.logging.Level.ALL);
        setFormatter(new SafeLogFormatter(zone));
        Files.createDirectories(this.directory);
        FileChannel channel = FileChannel.open(this.directory.resolve(".app-log.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            try { acquired = channel.tryLock(); }
            catch (OverlappingFileLockException failure) { throw new IOException("运行日志目录已在使用中", failure); }
            if (acquired == null) { throw new IOException("运行日志目录已在使用中"); }
            cleanExpired(LocalDate.now(clock));
        } catch (IOException | RuntimeException | Error failure) {
            if (acquired != null) {
                try { acquired.release(); } catch (IOException problem) { failure.addSuppressed(problem); }
            }
            try { channel.close(); } catch (IOException problem) { failure.addSuppressed(problem); }
            throw failure;
        }
        lockChannel = channel;
        directoryLock = acquired;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (closed) { throw new IllegalStateException("日志处理器已关闭"); }
        if (!isLoggable(record)) { return; }
        try {
            LocalDate date = record.getInstant().atZone(zone).toLocalDate();
            if (writer == null || !date.equals(openDate)) { rotate(date); }
            writer.write(getFormatter().format(record));
            writer.flush();
        } catch (IOException failure) { throw new UncheckedIOException("运行日志写入失败", failure); }
    }

    private void rotate(LocalDate date) throws IOException {
        closeWriter();
        cleanExpired(date);
        Path file = directory.resolve("app-" + DateTimeFormatter.BASIC_ISO_DATE.format(date) + ".log");
        // 保留已有同日日志，禁止轮转时截断。
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        openDate = date;
    }

    private void cleanExpired(LocalDate today) throws IOException {
        cleanExpired(Files.newDirectoryStream(directory, "app-????????.log"), today);
    }

    static void cleanExpired(DirectoryStream<Path> entries, LocalDate today) throws IOException {
        LocalDate cutoff = today.minusDays(6);
        try (entries) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!name.matches("app-[0-9]{8}\\.log")
                        || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) { continue; }
                LocalDate date;
                try { date = LocalDate.parse(name.substring(4, 12), DateTimeFormatter.BASIC_ISO_DATE); }
                catch (DateTimeException invalidName) { continue; }
                if (date.isBefore(cutoff)) { Files.delete(entry); }
            }
        } catch (DirectoryIteratorException failure) {
            IOException original = failure.getCause();
            for (Throwable suppressed : failure.getSuppressed()) {
                if (suppressed != original) { original.addSuppressed(suppressed); }
            }
            throw original;
        }
    }

    private void closeWriter() throws IOException {
        BufferedWriter current = writer;
        writer = null;
        openDate = null;
        if (current != null) { current.close(); }
    }

    @Override
    public synchronized void flush() {
        if (writer == null) { return; }
        try { writer.flush(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override
    public synchronized void close() {
        if (closed) { return; }
        closed = true;
        IOException failure = null;
        try { closeWriter(); } catch (IOException problem) { failure = problem; }
        try { directoryLock.release(); } catch (IOException problem) { failure = combine(failure, problem); }
        try { lockChannel.close(); } catch (IOException problem) { failure = combine(failure, problem); }
        if (failure != null) { throw new UncheckedIOException("运行日志关闭失败", failure); }
    }

    private static IOException combine(IOException original, IOException next) {
        if (original == null) { return next; }
        original.addSuppressed(next);
        return original;
    }
}
