package com.campus.recycle.util;

import com.campus.recycle.exception.DataAccessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** SQLite 连接工厂。每次调用返回独立连接，由调用方用 try-with-resources 关闭。 */
public final class DBUtil {
    private final Path databasePath;

    private DBUtil(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    private static final class DefaultInstance {
        private static final DBUtil INSTANCE = new DBUtil(
                Path.of(System.getProperty("campus.db.path", "data/campus.db")));
    }

    /**
     * 获取默认连接工厂；首次调用时读取系统属性 campus.db.path，默认 data/campus.db。
     * @return 默认单例，不持有数据库连接
     */
    public static DBUtil getInstance() {
        return DefaultInstance.INSTANCE;
    }

    /**
     * 创建使用指定数据库文件的工厂，便于测试隔离及配置注入。
     * @param databasePath 数据库文件路径，不支持 :memory: 连接字符串
     * @return 独立的连接工厂
     */
    public static DBUtil forPath(Path databasePath) {
        return new DBUtil(databasePath);
    }

    /**
     * 打开连接，启用外键和锁等待，并在空数据库中原子地完成首次初始化。
     * @return 新连接；关闭和业务事务由调用方负责
     * @throws DataAccessException 路径不可写、数据库损坏、版本不兼容或初始化失败
     */
    public Connection getConnection() {
        try {
            Files.createDirectories(databasePath.getParent());
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try {
                configure(connection);
                DatabaseInitializer.initialize(connection);
                return connection;
            } catch (SQLException | IOException | RuntimeException failure) {
                closeAfterFailure(connection, failure);
                throw failure;
            }
        } catch (SQLException | IOException failure) {
            throw new DataAccessException("无法打开或初始化数据库，请检查数据目录及数据库版本", failure);
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }

    private static void closeAfterFailure(Connection connection, Throwable failure) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
