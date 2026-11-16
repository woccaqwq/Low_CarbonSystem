package com.campus.recycle.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 仅在空库中执行迭代一脚本；版本或结构不匹配时停止，不重置已有数据。 */
final class DatabaseInitializer {
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_RESOURCE = "/sql/04_数据库建表脚本_schema.sql";
    private static final String DATA_RESOURCE = "/sql/05_初始化数据脚本_init_data.sql";
    private static final Set<String> REQUIRED_OBJECTS = Set.of(
            "table:sys_user", "table:category", "table:item", "table:favorite", "table:flow_record",
            "table:review", "table:operation_log", "table:sys_config", "view:v_item_list",
            "trigger:trg_item_updated_at", "trigger:trg_flow_finished_at");

    private DatabaseInitializer() {
    }

    // 单进程应用：序列化本进程的首次初始化，避免不同工厂同时对空库建表。
    static synchronized void initialize(Connection connection) throws SQLException, IOException {
        initialize(connection, SCHEMA_RESOURCE, DATA_RESOURCE);
    }

    static synchronized void initialize(Connection connection, String schemaResource, String dataResource)
            throws SQLException, IOException {
        int version = readVersion(connection);
        if (version == SCHEMA_VERSION) {
            validateObjects(connection);
            return;
        }
        if (version != 0) {
            throw new SQLException("数据库版本不兼容：" + version + "，当前支持版本：" + SCHEMA_VERSION);
        }
        if (!readObjects(connection).isEmpty()) {
            throw new SQLException("检测到未标记版本的已有数据库，需人工确认或迁移，禁止自动重建");
        }
        String schema = readScript(schemaResource);
        String data = readScript(dataResource);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
        }
        createDatabase(connection, schema, data);
    }

    private static void createDatabase(Connection connection, String schema, String data) throws SQLException {
        connection.setAutoCommit(false);
        try {
            executeScript(connection, schema);
            executeScript(connection, data);
            validateObjects(connection);
            try (Statement statement = connection.createStatement()) {
                try (ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
                    if (violations.next()) {
                        throw new SQLException("初始数据包含外键约束冲突");
                    }
                }
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            // 失败后由 DBUtil 关闭连接，不切回自动提交，避免意外提交残留操作。
            throw failure;
        }
        connection.setAutoCommit(true);
    }

    private static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static Set<String> readObjects(Connection connection) throws SQLException {
        Set<String> objects = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT type, name FROM sqlite_schema WHERE name NOT GLOB 'sqlite_*'")) {
            while (result.next()) {
                objects.add(result.getString("type") + ":" + result.getString("name"));
            }
        }
        return objects;
    }

    private static void validateObjects(Connection connection) throws SQLException {
        Set<String> missing = new HashSet<>(REQUIRED_OBJECTS);
        missing.removeAll(readObjects(connection));
        if (!missing.isEmpty()) {
            throw new SQLException("数据库结构不完整，缺失对象：" + missing);
        }
    }

    private static String readScript(String resource) throws IOException {
        try (InputStream input = DatabaseInitializer.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("找不到数据库初始化资源：" + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void executeScript(Connection connection, String script) throws SQLException {
        // 只读取受版本控制的项目脚本。连接和事务统一由 Java 管理，移除脚本中的相应独立命令。
        String sql = script.lines()
                .filter(line -> !isManagedCommand(line))
                .collect(Collectors.joining("\n"));
        try (Statement statement = connection.createStatement()) {
            // SQLite JDBC 的 executeUpdate 支持整段脚本，保留触发器 BEGIN/END 中的分号。
            statement.executeUpdate(sql);
        }
    }

    private static boolean isManagedCommand(String line) {
        String command = line.strip();
        return command.equalsIgnoreCase("PRAGMA foreign_keys = OFF;")
                || command.equalsIgnoreCase("PRAGMA foreign_keys = ON;")
                || command.equalsIgnoreCase("PRAGMA journal_mode = WAL;")
                || command.equalsIgnoreCase("BEGIN TRANSACTION;")
                || command.equalsIgnoreCase("COMMIT;");
    }
}
