package com.campus.recycle.service.impl;

import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Service 层统一事务边界：一个业务方法一个连接一个事务。
 * 事务内所有 DAO 必须调用 Connection 重载；业务异常与数据访问异常原样传播，
 * JDBC 提交/设置异常包装为 DataAccessException，回滚失败作为 suppressed 保留。
 */
final class TxSupport {
    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final DBUtil database;

    TxSupport(DBUtil database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    <T> T call(Work<T> work) {
        try (Connection connection = database.getConnection()) {
            try {
                connection.setAutoCommit(false);
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                throw new DataAccessException(ErrorCode.DB_ERROR.getMessage(), failure);
            }
        } catch (SQLException connectionFailure) {
            throw new DataAccessException(ErrorCode.DB_ERROR.getMessage(), connectionFailure);
        }
    }

    void run(Work<Void> work) {
        call(connection -> {
            work.run(connection);
            return null;
        });
    }
}
