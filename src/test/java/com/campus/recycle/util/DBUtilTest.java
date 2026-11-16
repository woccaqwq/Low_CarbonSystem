package com.campus.recycle.util;

import com.campus.recycle.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class DBUtilTest {
    @TempDir
    Path temporaryDirectory;

    private DBUtil database() {
        return DBUtil.forPath(temporaryDirectory.resolve("data/campus.db"));
    }

    @Test
    void createsSchemaAndSeedDataOnFirstConnection() throws SQLException {
        try (Connection connection = database().getConnection()) {
            assertEquals(1, number(connection, "PRAGMA user_version"));
            assertEquals(8, number(connection,
                    "SELECT count(*) FROM sqlite_schema WHERE type='table' AND name NOT GLOB 'sqlite_*'"));
            assertEquals(3, number(connection, "SELECT count(*) FROM sys_user"));
            assertEquals(14, number(connection, "SELECT count(*) FROM category"));
            assertEquals(5, number(connection, "SELECT count(*) FROM item"));
            assertEquals(3, number(connection, "SELECT count(*) FROM flow_record"));
            assertEquals(2, number(connection, "SELECT count(*) FROM review"));
            assertEquals(3, number(connection, "SELECT count(*) FROM favorite"));
            assertEquals(4, number(connection, "SELECT count(*) FROM operation_log"));
            assertEquals(3, number(connection, "SELECT count(*) FROM sys_config"));
            assertEquals(5, number(connection, "SELECT count(*) FROM v_item_list"));
            assertEquals("系统管理员", text(connection, "SELECT nickname FROM sys_user WHERE username='admin'"));
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void preservesChangesAcrossNewFactoriesAndConnections() throws SQLException {
        try (Connection connection = database().getConnection()) {
            execute(connection, "UPDATE item SET title='重启后保留的数据' WHERE item_id=1");
        }
        try (Connection connection = database().getConnection()) {
            assertEquals("重启后保留的数据", text(connection, "SELECT title FROM item WHERE item_id=1"));
            assertEquals(3, number(connection, "SELECT count(*) FROM sys_user"));
        }
    }

    @Test
    void enablesForeignKeysAndTimeoutOnEveryConnection() throws SQLException {
        for (int i = 0; i < 2; i++) {
            try (Connection connection = database().getConnection()) {
                assertEquals(1, number(connection, "PRAGMA foreign_keys"));
                assertEquals(5000, number(connection, "PRAGMA busy_timeout"));
                assertEquals("wal", text(connection, "PRAGMA journal_mode"));
                assertThrows(SQLException.class,
                        () -> execute(connection, "INSERT INTO item (owner_id,title) VALUES (99999,'非法外键')"));
            }
        }
    }

    @Test
    void installsWorkingTimestampTriggers() throws SQLException {
        try (Connection connection = database().getConnection()) {
            String previous = text(connection, "SELECT updated_at FROM item WHERE item_id=1");
            execute(connection, "UPDATE item SET title='触发时间更新' WHERE item_id=1");
            assertNotEquals(previous, text(connection, "SELECT updated_at FROM item WHERE item_id=1"));
            execute(connection, "UPDATE flow_record SET status='CANCELLED' WHERE flow_id=2");
            assertNotNull(text(connection, "SELECT finished_at FROM flow_record WHERE flow_id=2"));
        }
    }

    @Test
    void supportsCallerManagedTransactionRollback() throws SQLException {
        try (Connection connection = database().getConnection()) {
            connection.setAutoCommit(false);
            execute(connection, "UPDATE flow_record SET status='DEAL' WHERE flow_id=2");
            execute(connection, "UPDATE item SET status='FLOWED' WHERE item_id=1");
            assertThrows(SQLException.class,
                    () -> execute(connection, "INSERT INTO favorite(user_id,item_id) VALUES (3,1)"));
            connection.rollback();
            assertEquals("PENDING", text(connection, "SELECT status FROM flow_record WHERE flow_id=2"));
            assertNull(text(connection, "SELECT finished_at FROM flow_record WHERE flow_id=2"));
            assertEquals("ON_SALE", text(connection, "SELECT status FROM item WHERE item_id=1"));
        }
    }

    @Test
    void isolatesDifferentDatabaseFiles() throws SQLException {
        try (Connection first = database().getConnection();
             Connection second = DBUtil.forPath(temporaryDirectory.resolve("other.db")).getConnection()) {
            execute(first, "UPDATE sys_user SET nickname='仅第一库' WHERE user_id=1");
            assertEquals("系统管理员", text(second, "SELECT nickname FROM sys_user WHERE user_id=1"));
        }
    }

    @Test
    void refusesUnversionedExistingDatabaseWithoutDeletingData() throws SQLException {
        Path file = temporaryDirectory.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            execute(connection, "CREATE TABLE old_data (value TEXT)");
            execute(connection, "INSERT INTO old_data VALUES ('保留旧数据')");
        }
        assertThrows(DataAccessException.class, () -> DBUtil.forPath(file).getConnection());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            assertEquals("保留旧数据", text(connection, "SELECT value FROM old_data"));
            assertEquals(0, number(connection, "PRAGMA user_version"));
        }
    }

    @Test
    void refusesNewerDatabaseWithoutChangingIt() throws SQLException {
        try (Connection connection = database().getConnection()) {
            execute(connection, "PRAGMA user_version=99");
        }
        assertThrows(DataAccessException.class, () -> database().getConnection());
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + temporaryDirectory.resolve("data/campus.db"))) {
            assertEquals(99, number(connection, "PRAGMA user_version"));
            assertEquals(5, number(connection, "SELECT count(*) FROM item"));
        }
    }

    @Test
    void refusesIncompleteSchemaRatherThanRecreatingTables() throws SQLException {
        try (Connection connection = database().getConnection()) {
            execute(connection, "DROP VIEW v_item_list");
        }
        assertThrows(DataAccessException.class, () -> database().getConnection());
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + temporaryDirectory.resolve("data/campus.db"))) {
            assertEquals(5, number(connection, "SELECT count(*) FROM item"));
            assertEquals(0, number(connection, "SELECT count(*) FROM sqlite_schema WHERE name='v_item_list'"));
        }
    }

    @Test
    void rollsBackEntireInitializationAndCanRetryAfterBadSeed() throws SQLException, IOException {
        Path file = temporaryDirectory.resolve("failed.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            execute(connection, "PRAGMA foreign_keys=ON");
            assertThrows(SQLException.class, () -> DatabaseInitializer.initialize(connection,
                    "/sql/04_数据库建表脚本_schema.sql", "/sql/invalid_init_data.sql"));
            assertEquals(0, number(connection, "PRAGMA user_version"));
            assertEquals(0, number(connection,
                    "SELECT count(*) FROM sqlite_schema WHERE name NOT GLOB 'sqlite_*'"));
        }
        try (Connection connection = DBUtil.forPath(file).getConnection()) {
            assertEquals(3, number(connection, "SELECT count(*) FROM sys_user"));
        }
    }

    @Test
    void reportsInvalidPathWithOriginalCause() throws IOException {
        Path blocked = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blocked, "existing file");
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> DBUtil.forPath(blocked.resolve("campus.db")).getConnection());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    @Test
    void initializesOnlyOnceWhenThreadsOpenSameFile() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Callable<Integer> open = () -> {
                try (Connection connection = database().getConnection()) {
                    return number(connection, "SELECT count(*) FROM sys_user");
                }
            };
            List<Future<Integer>> results = executor.invokeAll(List.of(open, open, open, open));
            for (Future<Integer> result : results) {
                assertEquals(3, result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int number(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String text(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
