package com.campus.recycle.service;

import com.campus.recycle.dao.impl.*;
import com.campus.recycle.entity.*;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.impl.OperationLogDaoImpl;
import com.campus.recycle.dao.impl.UserDaoImpl;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.service.impl.UserServiceImpl;
import com.campus.recycle.util.DBUtil;
import com.campus.recycle.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 账号业务：注册规则、登录提示一致性与成功登录事务。 */
class UserServiceTest {
    @TempDir Path directory;
    private DBUtil database;
    private UserDao users;
    private OperationLogDao logs;
    private UserService service;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("user-service.db"));
        ServiceTestSupport.clearBusinessData(database);
        users = new UserDaoImpl(database);
        logs = new OperationLogDaoImpl(database);
        service = new UserServiceImpl(database, users, logs, new PasswordUtil());
    }

    @Test
    void registersUserAndLogsInWithFreshSnapshot() {
        service.register("student_x", "abc12345", "小×");
        assertTrue(users.findByUsername("student_x").isPresent());
        assertEquals("STUDENT", users.findByUsername("student_x").orElseThrow().getRole().name());

        SessionUser session = service.login("student_x", "abc12345");
        assertEquals("student_x", session.getUsername());
        assertEquals("小×", session.getNickname());
        assertNotNull(users.findByUsername("student_x").orElseThrow().getLastLoginAt());
        assertEquals(1, logs.findRecent(10).stream()
                .filter(log -> "USER_LOGIN".equals(log.getAction())).count());
    }

    @Test
    void rejectsDuplicateAndWeakCredentials() {
        service.register("student_x", "abc12345", "小×");
        BusinessException duplicated = assertThrows(BusinessException.class,
                () -> service.register("student_x", "abc12345", "另一人"));
        assertEquals(ErrorCode.USERNAME_DUPLICATED, duplicated.getCode());

        assertThrows(BusinessException.class, () -> service.register("a1", "abc12345", "昵称"));
        assertThrows(BusinessException.class, () -> service.register("good_name", "onlyletters", "昵称"));
        assertThrows(BusinessException.class, () -> service.register("good_name", "123456789", "昵称"));
    }

    @Test
    void missingUserAndWrongPasswordShareTheSameMessage() {
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.login("ghost_user", "abc12345"));
        service.register("student_x", "abc12345", "小×");
        BusinessException wrong = assertThrows(BusinessException.class,
                () -> service.login("student_x", "wrong123"));
        assertEquals(missing.getMessage(), wrong.getMessage());
        assertEquals("用户名或密码错误", wrong.getMessage());
    }

    @Test
    void changesPasswordWithOldCredentialInvalidated() {
        service.register("student_x", "abc12345", "小×");
        service.changePassword(users.findByUsername("student_x").orElseThrow().getUserId(),
                "abc12345", "newpass456");
        assertThrows(BusinessException.class, () -> service.login("student_x", "abc12345"));
        assertNotNull(service.login("student_x", "newpass456"));
    }

    @Test
    void locksAccountAfterRepeatedFailures() {
        service.register("student_x", "abc12345", "小×");
        for (int index = 0; index < 5; index++) {
            assertThrows(BusinessException.class, () -> service.login("student_x", "wrong123"));
        }
        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.login("student_x", "abc12345"));
        assertEquals(ErrorCode.LOGIN_LOCKED, locked.getCode());
    }

    @Test
    void rejectsDisabledAccount() {
        service.register("student_x", "abc12345", "小×");
        int userId = users.findByUsername("student_x").orElseThrow().getUserId();
        users.updateStatus(userId, 1);
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> service.login("student_x", "abc12345"));
        assertEquals(ErrorCode.USER_DISABLED, disabled.getCode());
    }
}
