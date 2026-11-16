package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.SysConfigDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.SysConfig;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SysConfigDaoImplTest {
    private static final String CO2 = "stat.co2.per_item_kg";
    @TempDir Path directory;
    private DBUtil database;
    private SysConfigDao configs;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("config.db"));
        configs = new SysConfigDaoImpl(database);
    }

    @Test
    void readsThreeInitialConfigurationsInKeyOrder() {
        List<SysConfig> all = configs.findAll();
        assertEquals(List.of("db.backup.enabled", "db.backup.keep_days", CO2),
                all.stream().map(SysConfig::getConfigKey).toList());
        assertEquals(List.of("true", "7", "2.4"), all.stream().map(SysConfig::getConfigValue).toList());
        assertTrue(all.stream().allMatch(config -> config.getUpdatedAt() != null));
        assertEquals("2.4", configs.findByKey(CO2).orElseThrow().getConfigValue());
    }

    @Test
    void distinguishesMissingFromEmptyValueAndEmptyTable() throws Exception {
        try (Connection connection = database.getConnection()) {
            insert(connection, "empty", "", "2030-01-02 03:04:05");
        }
        assertEquals("", configs.findByKey("empty").orElseThrow().getConfigValue());
        assertTrue(configs.findByKey("missing").isEmpty());
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM sys_config");
        }
        assertEquals(List.of(), configs.findAll());
        assertTrue(configs.findByKey(CO2).isEmpty());
    }

    @Test
    void bindsSpecialKeyAndPreservesValueLiterally() throws Exception {
        String key = "' OR 1=1 --";
        String value = "中文 '); DROP TABLE sys_config;--\n保留原文";
        try (Connection connection = database.getConnection()) {
            insert(connection, key, value, "2030-01-02 03:04:05");
        }
        assertEquals(value, configs.findByKey(key).orElseThrow().getConfigValue());
        assertEquals(4, configs.findAll().size());
        assertTrue(configs.findByKey("' OR 2=2 --").isEmpty());
    }

    @Test
    void matchesCaseAndWhitespaceExactly() throws Exception {
        try (Connection connection = database.getConnection()) {
            insert(connection, "Case", "upper", "2030-01-02 03:04:05");
            insert(connection, "case", "lower", "2030-01-02 03:04:05");
            insert(connection, " Case ", "spaces", "2030-01-02 03:04:05");
        }
        assertEquals("upper", configs.findByKey("Case").orElseThrow().getConfigValue());
        assertEquals("lower", configs.findByKey("case").orElseThrow().getConfigValue());
        assertEquals("spaces", configs.findByKey(" Case ").orElseThrow().getConfigValue());
        assertTrue(configs.findByKey("CASE").isEmpty());
    }

    @Test
    void returnsUnmodifiableList() {
        List<SysConfig> all = configs.findAll();
        assertThrows(UnsupportedOperationException.class, () -> all.remove(0));
        assertEquals(3, configs.findAll().size());
    }

    @Test
    void reopensCommittedValuesAndExplicitTimestamp() throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE sys_config SET config_value=?,updated_at=? WHERE config_key=?")) {
            statement.setString(1, "3.6");
            statement.setString(2, "2030-01-02 03:04:05");
            statement.setString(3, CO2);
            assertEquals(1, statement.executeUpdate());
        }
        SysConfig config = new SysConfigDaoImpl(DBUtil.forPath(directory.resolve("config.db")))
                .findByKey(CO2).orElseThrow();
        assertEquals("3.6", config.getConfigValue());
        assertEquals(LocalDateTime.of(2030, 1, 2, 3, 4, 5), config.getUpdatedAt());
    }

    @Test
    void doesNotSubstituteDefaultsForMalformedBusinessValues() throws Exception {
        try (Connection connection = database.getConnection()) {
            setValue(connection, CO2, "NaN");
            setValue(connection, "db.backup.enabled", "yes");
            setValue(connection, "db.backup.keep_days", "7days");
        }
        assertEquals("NaN", configs.findByKey(CO2).orElseThrow().getConfigValue());
        assertEquals("yes", configs.findByKey("db.backup.enabled").orElseThrow().getConfigValue());
        assertEquals("7days", configs.findByKey("db.backup.keep_days").orElseThrow().getConfigValue());
    }

    @Test
    void readsUncommittedValueOnCallerConnectionThenAllowsRollback() throws Exception {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            setValue(connection, CO2, "3.6");
            assertEquals("3.6", configs.findByKey(connection, CO2).orElseThrow().getConfigValue());
            assertEquals("3.6", configs.findAll(connection).stream()
                    .filter(config -> config.getConfigKey().equals(CO2)).findFirst().orElseThrow().getConfigValue());
            assertEquals("2.4", configs.findByKey(CO2).orElseThrow().getConfigValue());
            assertFalse(connection.isClosed());
            assertFalse(connection.getAutoCommit());
            connection.rollback();
        }
        assertEquals("2.4", configs.findByKey(CO2).orElseThrow().getConfigValue());
    }

    @Test
    void refreshesCommittedChangesWithoutCache() throws Exception {
        SysConfig before = configs.findByKey(CO2).orElseThrow();
        List<SysConfig> beforeList = configs.findAll();
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            setValue(connection, CO2, "4.8");
            connection.commit();
        }
        assertEquals("4.8", configs.findByKey(CO2).orElseThrow().getConfigValue());
        assertEquals("2.4", before.getConfigValue());
        assertEquals("2.4", beforeList.stream().filter(config -> config.getConfigKey().equals(CO2))
                .findFirst().orElseThrow().getConfigValue());
    }

    @Test
    void supportsQueryOnlyConnectionWithoutChangingAutoCommit() throws Exception {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only=ON");
            assertEquals(3, configs.findAll(connection).size());
            assertEquals("2.4", configs.findByKey(connection, CO2).orElseThrow().getConfigValue());
            assertTrue(connection.getAutoCommit());
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void reportsClosedConnectionsForBothQueries() throws Exception {
        Connection connection = database.getConnection();
        connection.close();
        assertInstanceOf(SQLException.class, assertThrows(DataAccessException.class,
                () -> configs.findByKey(connection, CO2)).getCause());
        assertInstanceOf(SQLException.class, assertThrows(DataAccessException.class,
                () -> configs.findAll(connection)).getCause());
    }

    @Test
    void rejectsInvalidLookupKeys() throws Exception {
        for (String key : new String[]{null, "", " \t"}) {
            assertThrows(IllegalArgumentException.class, () -> configs.findByKey(key));
            try (Connection connection = database.getConnection()) {
                assertThrows(IllegalArgumentException.class, () -> configs.findByKey(connection, key));
            }
        }
    }

    @Test
    void wrapsMalformedStoredTimestampInsteadOfReturningBrokenObject() throws Exception {
        try (Connection connection = database.getConnection()) {
            insert(connection, "bad.time", "private_value_marker", "2030-02-30 00:00:00");
        }
        DataAccessException failure = assertThrows(DataAccessException.class, () -> configs.findByKey("bad.time"));
        assertInstanceOf(SQLException.class, failure.getCause());
        assertFalse(failure.getMessage().contains("private_value_marker"));
        assertThrows(DataAccessException.class, () -> configs.findAll());
    }

    @Test
    void rejectsNullPrimaryKeyAllowedByLegacySqliteSchema() throws Exception {
        try (Connection connection = database.getConnection()) {
            insert(connection, null, "value", "2030-01-02 03:04:05");
        }
        assertInstanceOf(SQLException.class,
                assertThrows(DataAccessException.class, () -> configs.findAll()).getCause());
    }

    @Test
    void queryMappingFailureLeavesEarlierWriteForCallerToRollBack() throws Exception {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            setValue(connection, CO2, "3.6");
            insert(connection, "bad.time", "fixture", "bad timestamp");
            assertThrows(DataAccessException.class, () -> configs.findByKey(connection, "bad.time"));
            assertEquals("3.6", configs.findByKey(connection, CO2).orElseThrow().getConfigValue());
            assertFalse(connection.getAutoCommit());
            connection.rollback();
        }
        assertEquals("2.4", configs.findByKey(CO2).orElseThrow().getConfigValue());
        assertTrue(configs.findByKey("bad.time").isEmpty());
    }

    @Test
    void sharesSnapshotAcrossQueriesInAnExplicitReadTransaction() throws Exception {
        try (Connection reader = database.getConnection()) {
            reader.setAutoCommit(false);
            assertEquals(3, configs.findAll(reader).size());
            try (Connection writer = database.getConnection()) {
                setValue(writer, CO2, "3.6");
            }
            assertEquals("2.4", configs.findByKey(reader, CO2).orElseThrow().getConfigValue());
            assertEquals("3.6", configs.findByKey(CO2).orElseThrow().getConfigValue());
            reader.rollback();
            assertEquals("3.6", configs.findByKey(reader, CO2).orElseThrow().getConfigValue());
            reader.rollback();
        }
    }

    @Test
    void exposesStableFactoryWithoutMixingInjectedDatabases() throws Exception {
        assertSame(DaoFactory.getSysConfigDao(), DaoFactory.getSysConfigDao());
        SysConfigDao other = new SysConfigDaoImpl(DBUtil.forPath(directory.resolve("other.db")));
        try (Connection connection = database.getConnection()) { setValue(connection, CO2, "9.9"); }
        assertEquals("9.9", configs.findByKey(CO2).orElseThrow().getConfigValue());
        assertEquals("2.4", other.findByKey(CO2).orElseThrow().getConfigValue());
    }

    private static void setValue(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE sys_config SET config_value=? WHERE config_key=?")) {
            statement.setString(1, value);
            statement.setString(2, key);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insert(Connection connection, String key, String value, String timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sys_config(config_key,config_value,updated_at) VALUES(?,?,?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.setString(3, timestamp);
            assertEquals(1, statement.executeUpdate());
        }
    }
}
