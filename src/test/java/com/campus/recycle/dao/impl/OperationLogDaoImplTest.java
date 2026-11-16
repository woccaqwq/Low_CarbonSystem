package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.User;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OperationLogDaoImplTest {
    @TempDir Path directory;
    private DBUtil database;
    private OperationLogDao logs;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("logs.db"));
        logs = new OperationLogDaoImpl(database);
    }

    @Test
    void readsSeedLogsInRecentOrderWithActorProjection() {
        assertEquals(List.of(4, 3, 2, 1), ids(logs.findRecent(10)));
        assertEquals(List.of(4, 3), ids(logs.findRecent(2)));
        OperationLog log = logs.findById(1).orElseThrow();
        assertEquals(2, log.getUserId());
        assertEquals("ITEM_PUBLISH", log.getAction());
        assertTrue(log.getDetail().contains("充电器"));
        assertNotNull(log.getCreatedAt());
        assertEquals("student1", log.getUsername());
        assertEquals("徐同学", log.getNickname());
        assertTrue(logs.findById(9999).isEmpty());
    }

    @Test
    void appendsAndPersistsWithoutMutatingInput() {
        OperationLog input = new OperationLog(2, "ITEM_PUBLISH", "物品编号=6");
        int first = logs.insert(input);
        int second = logs.insert(input);
        assertTrue(first > 4);
        assertTrue(second > first);
        assertEquals(0, input.getLogId());
        assertNull(input.getCreatedAt());
        OperationLog saved = new OperationLogDaoImpl(DBUtil.forPath(directory.resolve("logs.db")))
                .findById(first).orElseThrow();
        assertEquals("物品编号=6", saved.getDetail());
        assertNotNull(saved.getCreatedAt());
        assertEquals(6, logs.findByPage(null, null, 1, 10).getTotal());
    }

    @Test
    void preservesExplicitTimeAndSystemActor() {
        OperationLog input = new OperationLog(null, "SYSTEM_START", "系统启动");
        input.setCreatedAt(LocalDateTime.of(2030, 1, 2, 3, 4, 5, 123));
        OperationLog saved = logs.findById(logs.insert(input)).orElseThrow();
        assertNull(saved.getUserId());
        assertNull(saved.getUsername());
        assertNull(saved.getNickname());
        assertEquals(LocalDateTime.of(2030, 1, 2, 3, 4, 5), saved.getCreatedAt());
    }

    @Test
    void projectsCurrentActorThenKeepsLogWhenUserIsDeleted() throws Exception {
        int user = new UserDaoImpl(database).insert(new User("log_user", "hash", "salt", "操作人"));
        int id = logs.insert(new OperationLog(user, "TEST_ACTION", "对象编号=42"));
        try (Connection connection = database.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "UPDATE sys_user SET username=?,nickname=? WHERE user_id=?")) {
            statement.setString(1, "log_user_renamed");
            statement.setString(2, "新昵称");
            statement.setInt(3, user);
            assertEquals(1, statement.executeUpdate());
        }
        OperationLog renamed = logs.findById(id).orElseThrow();
        assertEquals("log_user_renamed", renamed.getUsername());
        assertEquals("新昵称", renamed.getNickname());
        assertEquals("对象编号=42", renamed.getDetail());
        try (Connection connection = database.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement("DELETE FROM sys_user WHERE user_id=?")) {
            statement.setInt(1, user);
            statement.executeUpdate();
        }
        OperationLog saved = logs.findById(id).orElseThrow();
        assertNull(saved.getUserId());
        assertNull(saved.getUsername());
        assertNull(saved.getNickname());
        assertEquals("对象编号=42", saved.getDetail());
        assertEquals(5, logs.findByPage(null, null, 1, 10).getTotal());
        assertEquals(0, logs.findByPage(user, null, 1, 10).getTotal());
    }

    @Test
    void combinesExactUserAndActionFilters() {
        assertEquals(2, logs.findByPage(2, null, 1, 10).getTotal());
        assertEquals(List.of(2), ids(logs.findByPage(2, "FLOW_REQUEST", 1, 10).getItems()));
        assertTrue(logs.findByPage(3, "FLOW_REQUEST", 1, 10).isEmpty());
        assertTrue(logs.findByPage(9999, null, 1, 10).isEmpty());
    }

    @Test
    void treatsSpecialActionAndDetailAsParameters() {
        String action = "' OR 1=1 --";
        int id = logs.insert(new OperationLog(2, action, "中文 '); DROP TABLE operation_log;--\n保留内容"));
        assertEquals(List.of(id), ids(logs.findByPage(null, action, 1, 10).getItems()));
        assertEquals(action, logs.findById(id).orElseThrow().getAction());
        assertTrue(logs.findById(id).orElseThrow().getDetail().endsWith("保留内容"));
        assertEquals(5, logs.findByPage(null, null, 1, 10).getTotal());
    }

    @Test
    void paginatesIdenticalTimesWithStableIdOrderAndLongOffset() throws Exception {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (int index = 0; index < 12; index++) {
                OperationLog input = new OperationLog(1, "PAGE_TEST", "对象=" + index);
                input.setCreatedAt(LocalDateTime.of(2030, 1, 1, 0, 0));
                logs.insert(connection, input);
            }
            connection.commit();
        }
        PageResult<OperationLog> first = logs.findByPage(1, "PAGE_TEST", 1, 10);
        PageResult<OperationLog> second = logs.findByPage(1, "PAGE_TEST", 2, 10);
        assertEquals(12, first.getTotal());
        assertEquals(2, first.getTotalPages());
        assertEquals(10, first.getItems().size());
        assertEquals(16, first.getItems().get(0).getLogId());
        assertEquals(List.of(6, 5), ids(second.getItems()));
        assertEquals(List.of(16, 15), ids(logs.findRecent(2)));
        PageResult<OperationLog> far = logs.findByPage(1, "PAGE_TEST", Integer.MAX_VALUE, 100);
        assertTrue(far.isEmpty());
        assertEquals(12, far.getTotal());
    }

    @Test
    void rejectsInvalidForeignUserAndIncompleteInput() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> logs.insert(new OperationLog(9999, "TEST", "")));
        assertInstanceOf(java.sql.SQLException.class, failure.getCause());
        assertThrows(IllegalArgumentException.class, () -> logs.insert(new OperationLog()));
        assertThrows(IllegalArgumentException.class, () -> logs.insert(null));
        assertThrows(IllegalArgumentException.class, () -> logs.findRecent(0));
        assertThrows(IllegalArgumentException.class, () -> logs.findRecent(1001));
        assertThrows(IllegalArgumentException.class, () -> logs.findByPage(0, null, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> logs.findByPage(null, " ", 1, 10));
        assertThrows(IllegalArgumentException.class, () -> logs.findByPage(null, null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> logs.findByPage(null, null, 1, 9));
        assertThrows(IllegalArgumentException.class, () -> logs.findByPage(null, null, 1, 101));
    }

    @Test
    void commitsBusinessWriteAndLogTogetherOnlyAtCallerCommit() throws Exception {
        ItemDaoImpl items = new ItemDaoImpl(database);
        int id;
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            assertEquals(1, items.updateStatus(connection, 1, ItemStatus.OFF_SHELF));
            id = logs.insert(connection, new OperationLog(2, "ITEM_OFFLINE", "物品编号=1"));
            assertTrue(logs.findById(connection, id).isPresent());
            assertTrue(logs.findById(id).isEmpty());
            assertEquals(1, logs.findByPage(connection, 2, "ITEM_OFFLINE", 1, 10).getTotal());
            assertEquals(5, logs.findRecent(connection, 10).size());
            assertFalse(connection.isClosed());
            assertFalse(connection.getAutoCommit());
            connection.commit();
        }
        assertEquals(ItemStatus.OFF_SHELF, items.findById(1).orElseThrow().getStatus());
        assertTrue(logs.findById(id).isPresent());
    }

    @Test
    void letsCallerRollBackBusinessWriteWhenLogInsertFails() throws Exception {
        ItemDaoImpl items = new ItemDaoImpl(database);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            items.updateStatus(connection, 1, ItemStatus.OFF_SHELF);
            assertThrows(DataAccessException.class,
                    () -> logs.insert(connection, new OperationLog(9999, "ITEM_OFFLINE", "物品编号=1")));
            assertEquals(ItemStatus.OFF_SHELF, items.findById(connection, 1).orElseThrow().getStatus());
            connection.rollback();
        }
        assertEquals(ItemStatus.ON_SALE, items.findById(1).orElseThrow().getStatus());
        assertEquals(4, logs.findByPage(null, null, 1, 10).getTotal());
    }

    @Test
    void rollsBackSuccessfulLogIfLaterBusinessUpdateFails() throws Exception {
        int id;
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TEMP TRIGGER reject_offline BEFORE UPDATE OF status ON item "
                    + "WHEN NEW.status='OFF_SHELF' BEGIN SELECT RAISE(ABORT,'test failure'); END");
            connection.setAutoCommit(false);
            id = logs.insert(connection, new OperationLog(2, "ITEM_OFFLINE", "物品编号=1"));
            assertThrows(DataAccessException.class,
                    () -> new ItemDaoImpl(database).updateStatus(connection, 1, ItemStatus.OFF_SHELF));
            assertTrue(logs.findById(connection, id).isPresent());
            connection.rollback();
        }
        assertTrue(logs.findById(id).isEmpty());
        assertEquals(ItemStatus.ON_SALE, new ItemDaoImpl(database).findById(1).orElseThrow().getStatus());
    }

    @Test
    void readsOnQueryOnlyConnection() throws Exception {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only=ON");
            assertTrue(logs.findById(connection, 1).isPresent());
            assertEquals(4, logs.findRecent(connection, 10).size());
            assertEquals(2, logs.findByPage(connection, 2, null, 1, 10).getTotal());
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void reportsClosedExternalConnectionForAllOperations() throws Exception {
        Connection connection = database.getConnection();
        connection.close();
        assertThrows(DataAccessException.class, () -> logs.findById(connection, 1));
        assertThrows(DataAccessException.class, () -> logs.findRecent(connection, 10));
        assertThrows(DataAccessException.class, () -> logs.findByPage(connection, null, null, 1, 10));
        assertThrows(DataAccessException.class, () -> logs.insert(connection, new OperationLog(1, "TEST", "")));
    }

    @Test
    void factoryExposesStableOperationLogDao() {
        assertSame(DaoFactory.getOperationLogDao(), DaoFactory.getOperationLogDao());
        assertInstanceOf(OperationLogDao.class, DaoFactory.getOperationLogDao());
    }

    private static List<Integer> ids(List<OperationLog> logs) {
        return logs.stream().map(OperationLog::getLogId).toList();
    }
}
