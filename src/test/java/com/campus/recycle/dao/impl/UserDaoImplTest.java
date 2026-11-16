package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.User;
import com.campus.recycle.entity.enums.Role;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;
import com.campus.recycle.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserDaoImplTest {
    private static final String HASH = "2lFmtZvCAifuCQjRuFzFh5KojasY/zSk+67mIhff61Q=";
    private static final String SALT = "ytSKAF0hG0IsFXo4ed3mNg==";
    @TempDir
    Path directory;
    private DBUtil database;
    private UserDao dao;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("users.db"));
        dao = new UserDaoImpl(database);
    }

    @Test
    void readsSeedUsersAndMapsNullableDatesAndRoles() {
        User admin = dao.findByUsername("admin").orElseThrow();
        assertEquals(1, admin.getUserId());
        assertEquals("系统管理员", admin.getNickname());
        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals(HASH, admin.getPasswordHash());
        assertEquals(SALT, admin.getSalt());
        assertNotNull(admin.getCreatedAt());
        assertNull(admin.getLastLoginAt());
        assertTrue(new PasswordUtil().verify("admin123", admin.getPasswordHash(), admin.getSalt()));
        assertEquals(112, dao.findById(2).orElseThrow().getCreditScore());
    }

    @Test
    void insertsAndPersistsUserWithGeneratedIdAndDatabaseDefaults() {
        User original = user("new_user", "新同学");
        int id = dao.insert(original);
        assertTrue(id > 3);
        User saved = new UserDaoImpl(DBUtil.forPath(directory.resolve("users.db")))
                .findById(id).orElseThrow();
        assertEquals("new_user", saved.getUsername());
        assertEquals("新同学", saved.getNickname());
        assertEquals(HASH, saved.getPasswordHash());
        assertEquals(SALT, saved.getSalt());
        assertEquals(Role.STUDENT, saved.getRole());
        assertEquals(100, saved.getCreditScore());
        assertEquals(0, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertEquals(0, original.getUserId(), "插入不修改调用方对象，避免回滚后留下虚假主键");
    }

    @Test
    void preservesExplicitDatesAtSecondPrecision() {
        User original = user("dated_user", "有时间的用户");
        original.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 20, 30, 123));
        original.setLastLoginAt(LocalDateTime.of(2026, 11, 2, 11, 22, 33, 456));
        User saved = dao.findById(dao.insert(original)).orElseThrow();
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 20, 30), saved.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 11, 2, 11, 22, 33), saved.getLastLoginAt());
    }

    @Test
    void rejectsDuplicateUsernameAndPreservesSqlCause() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> dao.insert(user("admin", "重复账号")));
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals(3, dao.findByPage(null, 1, 10).getTotal());
    }

    @Test
    void treatsInjectionAsLiteralLookupText() {
        assertTrue(dao.findByUsername("' OR 1=1 --").isEmpty());
        assertTrue(dao.findByPage("' OR 1=1 --", 1, 10).isEmpty());
        assertEquals(3, dao.findByPage(null, 1, 10).getTotal());
    }

    @Test
    void searchesUsernameAndNicknameWithLiteralLikeCharacters() {
        dao.insert(user("percent_user", "进度100%"));
        dao.insert(user("under_user", "a_b"));
        dao.insert(user("slash_user", "路径\\目录"));
        assertEquals("percent_user", dao.findByPage("%", 1, 10).getItems().get(0).getUsername());
        assertEquals(1, dao.findByPage("%", 1, 10).getTotal());
        assertEquals(1, dao.findByPage("a_b", 1, 10).getTotal());
        assertEquals(1, dao.findByPage("\\", 1, 10).getTotal());
        assertEquals(2, dao.findByPage("student", 1, 10).getTotal());
        assertEquals(1, dao.findByPage("管理员", 1, 10).getTotal());
    }

    @Test
    void returnsStablePagesAndHandlesHugeOffset() throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (int index = 0; index < 12; index++) {
                dao.insert(connection, user("page_user" + index, "分页"));
            }
            connection.commit();
        }
        PageResult<User> first = dao.findByPage("", 1, 10);
        PageResult<User> second = dao.findByPage("  ", 2, 10);
        assertEquals(15, first.getTotal());
        assertEquals(10, first.getItems().size());
        assertEquals(2, first.getTotalPages());
        assertEquals(2, second.getPageNo());
        assertEquals(10, second.getPageSize());
        assertEquals(List.of(11, 12, 13, 14, 15), second.getItems().stream().map(User::getUserId).toList());
        PageResult<User> far = dao.findByPage(null, Integer.MAX_VALUE, 100);
        assertEquals(15, far.getTotal());
        assertTrue(far.isEmpty());
    }

    @Test
    void returnsEmptyForMissingUsersAndZeroAffectedRows() {
        assertTrue(dao.findById(9999).isEmpty());
        assertTrue(dao.findByUsername("missing_user").isEmpty());
        assertEquals(0, dao.updatePassword(9999, HASH, SALT));
        assertEquals(0, dao.updateCreditScore(9999, 2));
        assertEquals(0, dao.updateStatus(9999, 1));
        assertEquals(0, dao.updateLastLogin(9999, LocalDateTime.now()));
    }

    @Test
    void updatesPasswordSaltStatusAndLastLogin() {
        PasswordUtil passwords = new PasswordUtil();
        String salt = passwords.generateSalt();
        String hash = passwords.hash("changed123", salt);
        assertEquals(1, dao.updatePassword(2, hash, salt));
        assertEquals(1, dao.updateStatus(2, 1));
        assertEquals(1, dao.updateLastLogin(2, LocalDateTime.of(2026, 11, 16, 12, 30, 45, 123)));
        User saved = dao.findById(2).orElseThrow();
        assertTrue(passwords.verify("changed123", saved.getPasswordHash(), saved.getSalt()));
        assertFalse(passwords.verify("123456", saved.getPasswordHash(), saved.getSalt()));
        assertFalse(saved.isEnabled());
        assertEquals(LocalDateTime.of(2026, 11, 16, 12, 30, 45), saved.getLastLoginAt());
    }

    @Test
    void clampsCreditAtomicallyIncludingExtremeDeltas() {
        assertEquals(1, dao.updateCreditScore(2, 2));
        assertEquals(114, dao.findById(2).orElseThrow().getCreditScore());
        dao.updateCreditScore(2, Integer.MAX_VALUE);
        assertEquals(150, dao.findById(2).orElseThrow().getCreditScore());
        dao.updateCreditScore(2, Integer.MIN_VALUE);
        assertEquals(0, dao.findById(2).orElseThrow().getCreditScore());
    }

    @Test
    void sharesCallerTransactionWithoutCommittingOrClosingIt() throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            int id = dao.insert(connection, user("rollback_user", "待回滚"));
            dao.updatePassword(connection, 1, "temporary_hash", "temporary_salt");
            dao.updateCreditScore(connection, 1, 2);
            dao.updateStatus(connection, 1, 1);
            dao.updateLastLogin(connection, 1, LocalDateTime.of(2026, 11, 16, 12, 0));
            assertTrue(dao.findById(connection, id).isPresent());
            assertEquals(4, dao.findByPage(connection, null, 1, 10).getTotal());
            assertEquals(4, dao.findByUsername(connection, "rollback_user").orElseThrow().getUserId());
            assertFalse(connection.isClosed());
            assertFalse(connection.getAutoCommit());
            assertThrows(DataAccessException.class, () -> dao.insert(connection, user("admin", "重复")));
            connection.rollback();
        }
        assertTrue(dao.findByUsername("rollback_user").isEmpty());
        User admin = dao.findById(1).orElseThrow();
        assertEquals(HASH, admin.getPasswordHash());
        assertEquals(SALT, admin.getSalt());
        assertEquals(100, admin.getCreditScore());
        assertTrue(admin.isEnabled());
        assertNull(admin.getLastLoginAt());
    }

    @Test
    void commitsOnlyWhenCallerRequestsIt() throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            dao.insert(connection, user("commit_user", "提交后可见"));
            assertTrue(dao.findByUsername("commit_user").isEmpty());
            connection.commit();
        }
        assertTrue(dao.findByUsername("commit_user").isPresent());
    }

    @Test
    void reportsClosedExternalConnectionRatherThanOpeningAnotherOne() throws SQLException {
        Connection connection = database.getConnection();
        connection.close();
        assertThrows(DataAccessException.class, () -> dao.findById(connection, 1));
    }

    @Test
    void rejectsInvalidParametersBeforeChangingData() {
        assertThrows(IllegalArgumentException.class, () -> dao.findByPage(null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> dao.findByPage(null, 1, 9));
        assertThrows(IllegalArgumentException.class, () -> dao.findByPage(null, 1, 101));
        assertThrows(IllegalArgumentException.class, () -> dao.updateStatus(1, 2));
        assertThrows(IllegalArgumentException.class, () -> dao.updateLastLogin(1, null));
        assertThrows(IllegalArgumentException.class, () -> dao.insert(new User()));
        assertThrows(IllegalArgumentException.class, () -> dao.updatePassword(1, "", SALT));
        assertEquals(3, dao.findByPage(null, 1, 10).getTotal());
    }

    @Test
    void factoryExposesStableInterfaceInstance() {
        assertInstanceOf(UserDao.class, DaoFactory.getUserDao());
        assertSame(DaoFactory.getUserDao(), DaoFactory.getUserDao());
    }

    private static User user(String username, String nickname) {
        return new User(username, HASH, SALT, nickname);
    }
}
