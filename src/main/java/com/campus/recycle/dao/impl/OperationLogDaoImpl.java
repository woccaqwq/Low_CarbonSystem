package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 日志 JDBC 实现；不自动拦截业务调用，不管理调用方事务。 */
public final class OperationLogDaoImpl extends BaseDao<OperationLog> implements OperationLogDao {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SELECT = "SELECT l.*,u.username AS actor_username,u.nickname AS actor_nickname "
            + "FROM operation_log l LEFT JOIN sys_user u ON u.user_id=l.user_id";
    private static final String ORDER = " ORDER BY l.created_at DESC,l.log_id DESC";

    /** 使用默认数据库。 */
    public OperationLogDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 独立数据库工厂 */
    public OperationLogDaoImpl(DBUtil database) { super(database); }

    @Override
    public int insert(OperationLog log) {
        return withConnection(connection -> insert(connection, log));
    }
    @Override
    public List<OperationLog> findRecent(int limit) {
        return withConnection(connection -> findRecent(connection, limit));
    }
    @Override
    public Optional<OperationLog> findById(int logId) {
        return withConnection(connection -> findById(connection, logId));
    }
    @Override
    public PageResult<OperationLog> findByPage(Integer userId, String action, int pageNo, int pageSize) {
        return withConnection(connection -> findByPage(connection, userId, action, pageNo, pageSize));
    }
    @Override
    public int insert(Connection connection, OperationLog log) {
        if (log == null || log.getAction() == null || log.getAction().isBlank()) {
            throw new IllegalArgumentException("日志及动作不能为空");
        }
        LocalDateTime time = log.getCreatedAt();
        return onConnection(connection, current -> insertAndReturnId(current,
                "INSERT INTO operation_log(user_id,action,detail,created_at) "
                        + "VALUES (?,?,?,COALESCE(?,datetime('now','localtime')))",
                log.getUserId(), log.getAction(), log.getDetail(), time == null ? null : DATE_FORMAT.format(time)));
    }
    @Override
    public List<OperationLog> findRecent(Connection connection, int limit) {
        if (limit < 1 || limit > 1000) { throw new IllegalArgumentException("最近日志条数须在 1–1000 之间"); }
        return onConnection(connection, current -> query(current, SELECT + ORDER + " LIMIT ?", limit));
    }
    @Override
    public Optional<OperationLog> findById(Connection connection, int logId) {
        positive(logId);
        return onConnection(connection, current -> query(current, SELECT + " WHERE l.log_id=?", logId)
                .stream().findFirst());
    }
    @Override
    public PageResult<OperationLog> findByPage(Connection connection, Integer userId, String action,
                                              int pageNo, int pageSize) {
        if (userId != null) { positive(userId); }
        if (action != null && action.isBlank()) { throw new IllegalArgumentException("筛选动作不能是空白"); }
        if (pageNo < 1 || pageSize < 10 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (userId != null) { where.append(" AND l.user_id=?"); parameters.add(userId); }
        if (action != null) { where.append(" AND l.action=?"); parameters.add(action); }
        long offset = ((long) pageNo - 1) * pageSize;
        return onConnection(connection, current -> {
            int total = count(current, "SELECT count(*) FROM operation_log l" + where, parameters.toArray());
            List<Object> pageParameters = new ArrayList<>(parameters);
            pageParameters.add(pageSize);
            pageParameters.add(offset);
            List<OperationLog> records = query(current, SELECT + where + ORDER + " LIMIT ? OFFSET ?",
                    pageParameters.toArray());
            return new PageResult<>(records, total, pageNo, pageSize);
        });
    }
    @Override
    protected OperationLog mapRow(ResultSet result) throws SQLException {
        try {
            int actor = result.getInt("user_id");
            Integer userId = result.wasNull() ? null : actor;
            OperationLog log = new OperationLog(userId, result.getString("action"), result.getString("detail"));
            log.setLogId(result.getInt("log_id"));
            log.setCreatedAt(LocalDateTime.parse(result.getString("created_at"), DATE_FORMAT));
            log.setUsername(result.getString("actor_username"));
            log.setNickname(result.getString("actor_nickname"));
            return log;
        } catch (IllegalArgumentException | DateTimeException failure) {
            throw new SQLException("日志记录包含非法字段", failure);
        }
    }
    private static void positive(int id) {
        if (id <= 0) { throw new IllegalArgumentException("编号须为正整数"); }
    }
}
