package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.UserDao;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.User;
import com.campus.recycle.entity.enums.Role;
import com.campus.recycle.util.DBUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** sys_user 的 JDBC 实现，业务输入全部参数化。 */
public final class UserDaoImpl extends BaseDao<User> implements UserDao {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SELECT = "SELECT user_id, username, password_hash, salt, nickname, role, "
            + "credit_score, status, created_at, last_login_at FROM sys_user";
    private static final String FILTER = " WHERE username LIKE ? ESCAPE '\\' OR nickname LIKE ? ESCAPE '\\'";
    private static final String INSERT = "INSERT INTO sys_user "
            + "(username,password_hash,salt,nickname,role,credit_score,status,created_at,last_login_at) "
            + "VALUES (?,?,?,?,?,?,?,COALESCE(?,datetime('now','localtime')),?)";

    /** 使用默认数据库工厂。 */
    public UserDaoImpl() { this(DBUtil.getInstance()); }

    /** @param database 可注入的独立数据库工厂 */
    public UserDaoImpl(DBUtil database) { super(database); }

    @Override
    public Optional<User> findById(int userId) {
        return withConnection(connection -> findById(connection, userId));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return withConnection(connection -> findByUsername(connection, username));
    }

    @Override
    public int insert(User user) {
        return withConnection(connection -> insert(connection, user));
    }

    @Override
    public int updatePassword(int userId, String hash, String salt) {
        return withConnection(connection -> updatePassword(connection, userId, hash, salt));
    }

    @Override
    public int updateCreditScore(int userId, int delta) {
        return withConnection(connection -> updateCreditScore(connection, userId, delta));
    }

    @Override
    public int updateStatus(int userId, int status) {
        return withConnection(connection -> updateStatus(connection, userId, status));
    }

    @Override
    public int updateLastLogin(int userId, LocalDateTime time) {
        return withConnection(connection -> updateLastLogin(connection, userId, time));
    }

    @Override
    public PageResult<User> findByPage(String keyword, int pageNo, int pageSize) {
        return withConnection(connection -> findByPage(connection, keyword, pageNo, pageSize));
    }

    @Override
    public Optional<User> findById(Connection connection, int userId) {
        return onConnection(connection, current -> query(current, SELECT + " WHERE user_id=?", userId)
                .stream().findFirst());
    }

    @Override
    public Optional<User> findByUsername(Connection connection, String username) {
        requireText(username);
        return onConnection(connection, current -> query(current, SELECT + " WHERE username=?", username)
                .stream().findFirst());
    }

    @Override
    public int insert(Connection connection, User user) {
        if (user == null) { throw new IllegalArgumentException("用户不能为空"); }
        requireText(user.getUsername());
        requireText(user.getPasswordHash());
        requireText(user.getSalt());
        return onConnection(connection, current -> insertAndReturnId(current, INSERT,
                user.getUsername(), user.getPasswordHash(), user.getSalt(), user.getNickname(),
                user.getRole().name(), user.getCreditScore(), user.getStatus(),
                format(user.getCreatedAt()), format(user.getLastLoginAt())));
    }

    @Override
    public int updatePassword(Connection connection, int userId, String hash, String salt) {
        requireText(hash);
        requireText(salt);
        return onConnection(connection, current -> update(current,
                "UPDATE sys_user SET password_hash=?,salt=? WHERE user_id=?", hash, salt, userId));
    }

    @Override
    public int updateCreditScore(Connection connection, int userId, int delta) {
        return onConnection(connection, current -> update(current,
                "UPDATE sys_user SET credit_score=MAX(0,MIN(150,credit_score+?)) WHERE user_id=?", delta, userId));
    }

    @Override
    public int updateStatus(Connection connection, int userId, int status) {
        if (status != 0 && status != 1) { throw new IllegalArgumentException("账号状态不合法"); }
        return onConnection(connection, current -> update(current,
                "UPDATE sys_user SET status=? WHERE user_id=?", status, userId));
    }

    @Override
    public int updateLastLogin(Connection connection, int userId, LocalDateTime time) {
        if (time == null) { throw new IllegalArgumentException("登录时间不能为空"); }
        return onConnection(connection, current -> update(current,
                "UPDATE sys_user SET last_login_at=? WHERE user_id=?", format(time), userId));
    }

    @Override
    public PageResult<User> findByPage(Connection connection, String keyword, int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 10 || pageSize > 100) {
            throw new IllegalArgumentException("页码须从 1 开始，每页条数须在 10–100 之间");
        }
        String pattern = likePattern(keyword);
        long offset = ((long) pageNo - 1) * pageSize;
        return onConnection(connection, current -> {
            int total = count(current, "SELECT count(*) FROM sys_user" + FILTER, pattern, pattern);
            List<User> users = query(current, SELECT + FILTER + " ORDER BY user_id ASC LIMIT ? OFFSET ?",
                    pattern, pattern, pageSize, offset);
            return new PageResult<>(users, total, pageNo, pageSize);
        });
    }

    @Override
    protected User mapRow(ResultSet result) throws SQLException {
        try {
            User user = new User(result.getString("username"), result.getString("password_hash"),
                    result.getString("salt"), result.getString("nickname"));
            user.setUserId(result.getInt("user_id"));
            user.setRole(Role.valueOf(result.getString("role")));
            user.setCreditScore(result.getInt("credit_score"));
            user.setStatus(result.getInt("status"));
            user.setCreatedAt(parse(result.getString("created_at")));
            user.setLastLoginAt(parse(result.getString("last_login_at")));
            return user;
        } catch (IllegalArgumentException | DateTimeException failure) {
            throw new SQLException("用户记录包含非法字段", failure);
        }
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : DATE_FORMAT.format(time);
    }

    private static LocalDateTime parse(String text) {
        return text == null ? null : LocalDateTime.parse(text, DATE_FORMAT);
    }

    private static String likePattern(String keyword) {
        String literal = keyword == null ? "" : keyword.strip();
        return "%" + literal.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank()) { throw new IllegalArgumentException("必要字段不能为空"); }
    }
}
