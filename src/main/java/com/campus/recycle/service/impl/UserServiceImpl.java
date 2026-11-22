package com.campus.recycle.service.impl;

import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.entity.User;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.exception.ValidationException;
import com.campus.recycle.service.UserService;
import com.campus.recycle.util.DBUtil;
import com.campus.recycle.util.PasswordUtil;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号业务实现：注册与口令校验规则、失败锁定、登录时间/日志事务。
 * 口令只在认证内部使用，不进入日志、快照或异常提示。
 */
public final class UserServiceImpl implements UserService {
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 10L * 60 * 1000;
    private static final String USERNAME_PATTERN = "[A-Za-z0-9_]{3,20}";

    private final DBUtil database;
    private final UserDao users;
    private final OperationLogDao logs;
    private final PasswordUtil passwords;
    private final TxSupport tx;
    private final Map<String, FailRecord> failures = new ConcurrentHashMap<>();

    /** 使用默认连接工厂与 DAO 构造。 */
    public UserServiceImpl() {
        this(DBUtil.getInstance(), DaoFactory.getUserDao(), DaoFactory.getOperationLogDao(), new PasswordUtil());
    }

    /**
     * 注入依赖，便于测试使用独立数据库。
     * @param database 连接工厂 @param users 用户 DAO @param logs 日志 DAO @param passwords 口令工具
     */
    public UserServiceImpl(DBUtil database, UserDao users, OperationLogDao logs, PasswordUtil passwords) {
        this.database = Objects.requireNonNull(database, "database");
        this.users = Objects.requireNonNull(users, "users");
        this.logs = Objects.requireNonNull(logs, "logs");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.tx = new TxSupport(database);
    }

    @Override
    public SessionUser login(String username, String rawPassword) throws BusinessException {
        requireText(username, "username");
        requireText(rawPassword, "password");
        checkNotLocked(username);

        User account = users.findByUsername(username)
                .orElseThrow(() -> failure(username, ErrorCode.USER_NOT_FOUND));
        if (!passwords.verify(rawPassword, account.getPasswordHash(), account.getSalt())) {
            throw failure(username, ErrorCode.PASSWORD_ERROR);
        }
        if (!account.isEnabled()) {
            throw failure(username, ErrorCode.USER_DISABLED);
        }
        failures.remove(username);

        int userId = account.getUserId();
        LocalDateTime now = LocalDateTime.now();
        return tx.call(connection -> {
            User current = users.findById(connection, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (!current.isEnabled()) {
                throw new BusinessException(ErrorCode.USER_DISABLED);
            }
            requireUpdated(connection, users.updateLastLogin(connection, userId, now));
            logs.insert(connection, new OperationLog(userId, "USER_LOGIN", "登录成功"));
            return SessionUser.from(current);
        });
    }

    @Override
    public void register(String username, String rawPassword, String nickname) throws BusinessException {
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            throw new ValidationException("username", ErrorCode.VALIDATION_ERROR);
        }
        validatePassword(rawPassword, "password");
        if (nickname == null || nickname.codePointCount(0, nickname.length()) > 30) {
            throw new ValidationException("nickname", ErrorCode.VALIDATION_ERROR);
        }
        if (users.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
        }
        String salt = passwords.generateSalt();
        String hash = passwords.hash(rawPassword, salt);
        User user = new User(username, hash, salt, nickname);
        tx.run(connection -> {
            if (users.findByUsername(connection, username).isPresent()) {
                throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
            }
            int userId = users.insert(connection, user);
            logs.insert(connection, new OperationLog(userId, "USER_REGISTER", "账号注册成功"));
            return null;
        });
    }

    @Override
    public void changePassword(int userId, String oldPwd, String newPwd) throws BusinessException {
        if (userId <= 0) { throw new ValidationException("userId", ErrorCode.VALIDATION_ERROR); }
        requireText(oldPwd, "oldPwd");
        validatePassword(newPwd, "newPwd");
        User account = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_ID));
        if (!passwords.verify(oldPwd, account.getPasswordHash(), account.getSalt())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        String salt = passwords.generateSalt();
        String hash = passwords.hash(newPwd, salt);
        tx.run(connection -> {
            requireUpdated(connection, users.updatePassword(connection, userId, hash, salt));
            logs.insert(connection, new OperationLog(userId, "PASSWORD_CHANGE", "口令已更新"));
            return null;
        });
    }

    @Override
    public PageResult<User> pageUsers(String keyword, int pageNo, int pageSize) {
        int normalizedPage = Math.max(1, pageNo);
        int normalizedSize = Math.min(100, Math.max(10, pageSize));
        return users.findByPage(keyword, normalizedPage, normalizedSize);
    }

    private BusinessException failure(String username, ErrorCode code) {
        FailRecord record = failures.compute(username, (key, old) -> {
            FailRecord current = old == null ? new FailRecord() : old;
            current.count++;
            current.lastFailure = System.currentTimeMillis();
            return current;
        });
        if (record.count >= MAX_FAILURES) {
            return new BusinessException(ErrorCode.LOGIN_LOCKED);
        }
        return new BusinessException(code);
    }

    private void checkNotLocked(String username) {
        FailRecord record = failures.get(username);
        if (record == null) { return; }
        long now = System.currentTimeMillis();
        if (now - record.lastFailure >= LOCK_MILLIS) {
            failures.remove(username, record);
            return;
        }
        if (record.count >= MAX_FAILURES) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }
    }

    private void requireUpdated(Connection connection, int rows) {
        if (rows != 1) {
            throw new com.campus.recycle.exception.DataAccessException(
                    ErrorCode.DB_ERROR.getMessage(), new IllegalStateException("预期更新 1 行，实际 " + rows + " 行"));
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, ErrorCode.VALIDATION_ERROR);
        }
    }

    private static void validatePassword(String password, String field) {
        if (password == null || password.length() < 6 || password.length() > 20
                || !password.chars().anyMatch(Character::isLetter)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw new ValidationException(field, ErrorCode.VALIDATION_ERROR);
        }
    }

    private static final class FailRecord {
        private int count;
        private long lastFailure;
    }
}
