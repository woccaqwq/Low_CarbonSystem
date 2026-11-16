package com.campus.recycle.dao.impl;

import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 参数绑定和资源管理模板；子类负责固定 SQL 与行映射，不管理事务。 */
public abstract class BaseDao<T> {
    private final DBUtil database;

    /** @param database 连接工厂，用于默认独立连接方法 */
    protected BaseDao(DBUtil database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** @param result 当前行 @return 实体 @throws SQLException 数据映射失败 */
    protected abstract T mapRow(ResultSet result) throws SQLException;

    @FunctionalInterface
    protected interface SqlWork<R> {
        R run(Connection connection) throws SQLException;
    }

    /** @param work 数据访问动作 @return 动作结果；自动关闭本方法创建的连接 */
    protected final <R> R withConnection(SqlWork<R> work) {
        try (Connection connection = database.getConnection()) {
            return work.run(connection);
        } catch (SQLException failure) {
            throw accessFailure(failure);
        }
    }

    /** @param connection 外部连接 @param work 动作 @return 结果；不关闭或提交外部连接 */
    protected final <R> R onConnection(Connection connection, SqlWork<R> work) {
        Objects.requireNonNull(connection, "connection");
        try {
            return work.run(connection);
        } catch (SQLException failure) {
            throw accessFailure(failure);
        }
    }

    /** @param connection 连接 @param sql 固定 SQL @param parameters 参数 @return 映射结果 */
    protected final List<T> query(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet result = statement.executeQuery()) {
            List<T> items = new ArrayList<>();
            while (result.next()) {
                items.add(mapRow(result));
            }
            return items;
        }
    }

    /** @param connection 连接 @param sql 固定 SQL @param parameters 参数 @return 受影响行数 */
    protected final int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            return statement.executeUpdate();
        }
    }

    /** @param connection 连接 @param sql 固定插入 SQL @param parameters 参数 @return 新生成的主键 */
    protected final int insertAndReturnId(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, parameters);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("插入未产生一条记录");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) { throw new SQLException("插入后未返回主键"); }
                return keys.getInt(1);
            }
        }
    }

    /** @param connection 连接 @param sql 固定计数 SQL @param parameters 参数 @return 计数结果 */
    protected final int count(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) { throw new SQLException("计数查询未返回结果"); }
            return result.getInt(1);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            bind(statement, parameters);
            return statement;
        } catch (SQLException failure) {
            try { statement.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private static DataAccessException accessFailure(SQLException failure) {
        return new DataAccessException("数据操作失败，请联系管理员", failure);
    }
}
