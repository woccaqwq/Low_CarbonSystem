package com.campus.recycle.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class AppLoggerTest {
    @TempDir Path directory;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant TIME = Instant.parse("2026-11-16T01:02:03.456Z");

    @Test
    void writesUtf8MetadataAndFlushesBeforeClose() throws Exception {
        Path logs = directory.resolve("logs");
        try (AppLogger logger = create(logs, false)) {
            log(logger, AppLogger.Level.INFO, "发布成功");
            String text = read(logs, "20261116");
            assertTrue(text.startsWith("2026-11-16 09:02:03.456 INFO "));
            assertTrue(text.contains("class=\"com.campus.recycle.util.AppLoggerTest\""));
            assertTrue(text.contains("method=\"run\" user=2 action=\"TEST_ACTION\" result=\"发布成功\""));
        }
    }

    @Test
    void mapsAllLevelsAndFiltersDebugByDefault() throws Exception {
        Path logs = directory.resolve("production");
        try (AppLogger logger = create(logs, false)) {
            for (AppLogger.Level level : AppLogger.Level.values()) { log(logger, level, level.name()); }
        }
        String text = read(logs, "20261116");
        assertFalse(text.contains("DEBUG"));
        assertTrue(text.contains(" INFO "));
        assertTrue(text.contains(" WARN "));
        assertTrue(text.contains(" ERROR "));
        assertEquals(3, text.lines().count());
        Path debug = directory.resolve("development");
        try (AppLogger logger = create(debug, true)) { log(logger, AppLogger.Level.DEBUG, "开发信息"); }
        assertTrue(read(debug, "20261116").contains(" DEBUG "));
    }

    @Test
    void usesSystemActorWithoutInventingUser() throws Exception {
        try (AppLogger logger = create(directory, false)) {
            logger.log(AppLogger.Level.INFO, AppLoggerTest.class, "startup", null, "START", "SUCCESS");
        }
        assertTrue(read(directory, "20261116").contains("user=SYSTEM"));
    }

    @Test
    void escapesFieldsWithoutAllowingForgedLines() throws Exception {
        try (AppLogger logger = create(directory, false)) {
            logger.log(AppLogger.Level.INFO, AppLoggerTest.class, "run\nforged", 2,
                    "ACTION\tX", "OK\r\nERROR fake\t\"\\\u0000\u2028\uD800");
        }
        String text = read(directory, "20261116");
        assertEquals(1, text.lines().count());
        assertTrue(text.contains("run\\nforged"));
        assertTrue(text.contains("ACTION\\tX"));
        assertTrue(text.contains("OK\\r\\nERROR fake\\t\\\"\\\\\\u0000\\u2028\\uD800"));
    }

    @Test
    void preservesExceptionTypesFramesAndRelationsButNotMessages() throws Exception {
        IllegalArgumentException cause = new IllegalArgumentException("salt_private_marker");
        RuntimeException failure = new RuntimeException("password_private_marker", cause);
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("sample.Service", "publish", "Service.java", 42)});
        failure.addSuppressed(new IOException("sql_private_marker"));
        cause.initCause(failure);
        try (AppLogger logger = create(directory, false)) {
            logger.log(AppLogger.Level.ERROR, AppLoggerTest.class, "run", 2, "FAILED", "FAILURE", failure);
        }
        String text = read(directory, "20261116");
        assertTrue(text.contains("java.lang.RuntimeException"));
        assertTrue(text.contains("sample.Service.publish(Service.java:42)"));
        assertTrue(text.contains("suppressed=java.io.IOException"));
        assertTrue(text.contains("cause=java.lang.IllegalArgumentException"));
        assertTrue(text.contains("CIRCULAR_REFERENCE"));
        assertFalse(text.contains("private_marker"));
    }

    @Test
    void rotatesUsingClockZoneAndOneInstantPerRecord() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-16T15:59:59Z"), ZONE);
        try (AppLogger logger = new AppLogger(directory, clock, false)) {
            log(logger, AppLogger.Level.INFO, "first");
            clock.advance(Duration.ofSeconds(2));
            log(logger, AppLogger.Level.INFO, "second");
        }
        assertTrue(read(directory, "20261116").startsWith("2026-11-16 23:59:59.000"));
        assertFalse(read(directory, "20261116").contains("second"));
        assertTrue(read(directory, "20261117").startsWith("2026-11-17 00:00:01.000"));
    }

    @Test
    void appendsWhenReopenedInsteadOfTruncating() throws Exception {
        try (AppLogger first = create(directory, false)) { log(first, AppLogger.Level.INFO, "first"); }
        try (AppLogger second = create(directory, false)) { log(second, AppLogger.Level.INFO, "second"); }
        String text = read(directory, "20261116");
        assertEquals(2, text.lines().count());
        assertTrue(text.contains("first"));
        assertTrue(text.contains("second"));
    }

    @Test
    void keepsTodayAndPreviousSixDaysAndProtectsOtherPaths() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 16);
        for (int days = 0; days <= 10; days++) {
            Files.writeString(file(directory, today.minusDays(days)), "fixture");
        }
        Path future = file(directory, today.plusDays(1));
        Path invalid = directory.resolve("app-20260230.log");
        Path unrelated = directory.resolve("notes.log");
        Path backup = directory.resolve("app-20200101.log.bak");
        Path folder = Files.createDirectory(directory.resolve("app-20200101.log"));
        for (Path path : List.of(future, invalid, unrelated, backup)) { Files.writeString(path, "keep"); }
        try (AppLogger ignored = create(directory, false)) { }
        for (int days = 0; days <= 10; days++) {
            assertEquals(days < 7, Files.exists(file(directory, today.minusDays(days))));
        }
        for (Path path : List.of(future, invalid, unrelated, backup, folder)) { assertTrue(Files.exists(path)); }
    }

    @Test
    void cleansNewlyExpiredFileWhenDateChanges() throws Exception {
        MutableClock clock = new MutableClock(TIME, ZONE);
        Path boundary = file(directory, LocalDate.of(2026, 11, 10));
        Files.writeString(boundary, "boundary");
        try (AppLogger logger = new AppLogger(directory, clock, false)) {
            log(logger, AppLogger.Level.INFO, "day1");
            assertTrue(Files.exists(boundary));
            clock.advance(Duration.ofDays(1));
            log(logger, AppLogger.Level.INFO, "day2");
            assertFalse(Files.exists(boundary));
        }
    }

    @Test
    void preventsConcurrentOwnersThenReleasesDirectoryLock() throws Exception {
        try (AppLogger first = create(directory, false)) {
            assertThrows(IOException.class, () -> create(directory, false));
            log(first, AppLogger.Level.INFO, "still usable");
        }
        try (AppLogger next = create(directory, false)) { log(next, AppLogger.Level.INFO, "released"); }
        assertEquals(2, read(directory, "20261116").lines().count());
    }

    @Test
    void serializesConcurrentRecordsWithoutInterleaving() throws Exception {
        var workers = Executors.newFixedThreadPool(4);
        try (AppLogger logger = create(directory, false)) {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                int id = index;
                tasks.add(() -> { log(logger, AppLogger.Level.INFO, "event-" + id); return null; });
            }
            for (var future : workers.invokeAll(tasks)) { future.get(); }
        } finally {
            workers.shutdownNow();
        }
        List<String> lines = read(directory, "20261116").lines().toList();
        assertEquals(100, lines.size());
        for (int index = 0; index < 100; index++) {
            String result = "result=\"event-" + index + "\"";
            assertEquals(1, lines.stream().filter(line -> line.contains(result)).count());
        }
    }

    @Test
    void surfacesWriteFailureAndCanRetryAfterPathRepair() throws Exception {
        Path blocked = Files.createDirectory(directory.resolve("app-20261116.log"));
        try (AppLogger logger = create(directory, false)) {
            assertThrows(IOException.class, () -> log(logger, AppLogger.Level.ERROR, "failed"));
            Files.delete(blocked);
            log(logger, AppLogger.Level.INFO, "recovered");
        }
        assertTrue(read(directory, "20261116").contains("recovered"));
        assertFalse(read(directory, "20261116").contains("result=\"failed\""));
    }

    @Test
    void rotationFailurePreservesEarlierDayAndCanRetry() throws Exception {
        MutableClock clock = new MutableClock(TIME, ZONE);
        Path blocked = Files.createDirectory(directory.resolve("app-20261117.log"));
        try (AppLogger logger = new AppLogger(directory, clock, false)) {
            log(logger, AppLogger.Level.INFO, "yesterday");
            clock.advance(Duration.ofDays(1));
            assertThrows(IOException.class, () -> log(logger, AppLogger.Level.INFO, "failed"));
            Files.delete(blocked);
            log(logger, AppLogger.Level.INFO, "today");
        }
        assertTrue(read(directory, "20261116").contains("yesterday"));
        assertTrue(read(directory, "20261117").contains("today"));
    }

    @Test
    void rejectsInvalidMetadataAndClosedLogger() throws Exception {
        AppLogger logger = create(directory, false);
        assertThrows(IllegalArgumentException.class, () -> logger.log(null, AppLoggerTest.class, "m", null, "A", "R"));
        assertThrows(IllegalArgumentException.class, () -> logger.log(AppLogger.Level.INFO, null, "m", null, "A", "R"));
        assertThrows(IllegalArgumentException.class, () -> logger.log(AppLogger.Level.INFO, AppLoggerTest.class, " ", null, "A", "R"));
        assertThrows(IllegalArgumentException.class, () -> logger.log(AppLogger.Level.INFO, AppLoggerTest.class, "m", 0, "A", "R"));
        assertThrows(IllegalArgumentException.class, () -> logger.log(AppLogger.Level.INFO, AppLoggerTest.class, "m", null, null, "R"));
        assertThrows(IllegalArgumentException.class, () -> logger.log(AppLogger.Level.INFO, AppLoggerTest.class, "m", null, "A", ""));
        logger.close();
        logger.close();
        assertThrows(IllegalStateException.class, () -> log(logger, AppLogger.Level.INFO, "closed"));
        assertThrows(IllegalStateException.class, () -> log(logger, AppLogger.Level.DEBUG, "filtered but closed"));
    }

    @Test
    void rejectsUnusableDirectoryAndMissingConstructorSettings() throws Exception {
        Path file = Files.writeString(directory.resolve("not-directory"), "keep");
        assertThrows(IOException.class, () -> create(file, false));
        assertThrows(IllegalArgumentException.class, () -> new AppLogger(null, Clock.fixed(TIME, ZONE), false));
        assertThrows(IllegalArgumentException.class, () -> new AppLogger(directory, null, false));
        assertEquals("keep", Files.readString(file));
    }

    @Test
    void doesNotChangeRootLoggerConfiguration() throws Exception {
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        var beforeHandlers = root.getHandlers();
        var beforeLevel = root.getLevel();
        try (AppLogger logger = create(directory, true)) { log(logger, AppLogger.Level.DEBUG, "isolated"); }
        assertArrayEquals(beforeHandlers, root.getHandlers());
        assertEquals(beforeLevel, root.getLevel());
    }

    @Test
    void directoryTraversalFailureBecomesIOExceptionAndRetainsCloseFailure() {
        IOException readFailure = new IOException("directory read fixture");
        IOException closeFailure = new IOException("directory close fixture");
        DirectoryStream<Path> entries = new DirectoryStream<>() {
            public Iterator<Path> iterator() {
                return new Iterator<>() {
                    public boolean hasNext() { throw new DirectoryIteratorException(readFailure); }
                    public Path next() { throw new NoSuchElementException(); }
                };
            }
            public void close() throws IOException { throw closeFailure; }
        };
        IOException reported = assertThrows(IOException.class,
                () -> DailyLogHandler.cleanExpired(entries, LocalDate.of(2026, 11, 16)));
        assertSame(readFailure, reported);
        assertArrayEquals(new Throwable[]{closeFailure}, reported.getSuppressed());
    }

    private static AppLogger create(Path path, boolean debug) throws IOException {
        return new AppLogger(path, Clock.fixed(TIME, ZONE), debug);
    }
    private static void log(AppLogger logger, AppLogger.Level level, String result) throws IOException {
        logger.log(level, AppLoggerTest.class, "run", 2, "TEST_ACTION", result);
    }
    private static String read(Path path, String date) throws IOException {
        return Files.readString(path.resolve("app-" + date + ".log"), StandardCharsets.UTF_8);
    }
    private static Path file(Path path, LocalDate date) {
        return path.resolve("app-" + date.toString().replace("-", "") + ".log");
    }
    private static final class MutableClock extends Clock {
        private Instant time;
        private final ZoneId zone;
        private MutableClock(Instant time, ZoneId zone) { this.time = time; this.zone = zone; }
        void advance(Duration duration) { time = time.plus(duration); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId other) { return Clock.fixed(time, other); }
        @Override public Instant instant() { return time; }
    }
}
