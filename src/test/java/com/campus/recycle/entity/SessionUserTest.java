package com.campus.recycle.entity;

import com.campus.recycle.dao.impl.UserDaoImpl;
import com.campus.recycle.entity.enums.Role;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class SessionUserTest {
    @TempDir Path directory;

    @Test
    void exposesSafeMetadataAndRole() {
        SessionUser student = new SessionUser(2, "student", "同学", Role.STUDENT, 100);
        assertEquals(2, student.getUserId());
        assertEquals("student", student.getUsername());
        assertEquals("同学", student.getNickname());
        assertEquals(Role.STUDENT, student.getRole());
        assertEquals(100, student.getCreditScore());
        assertFalse(student.isAdmin());
        assertTrue(new SessionUser(1, "admin", "管理员", Role.ADMIN, 150).isAdmin());
    }

    @Test
    void copiesSourceInsteadOfFollowingLaterUserChanges() {
        User source = new User("original", "secret_hash_marker", "secret_salt_marker", "原昵称");
        source.setUserId(2);
        SessionUser snapshot = SessionUser.from(source);
        source.setUserId(3);
        source.setUsername("changed");
        source.setNickname("新昵称");
        source.setRole(Role.ADMIN);
        source.setCreditScore(130);
        assertEquals(2, snapshot.getUserId());
        assertEquals("original", snapshot.getUsername());
        assertEquals("原昵称", snapshot.getNickname());
        assertEquals(Role.STUDENT, snapshot.getRole());
        assertEquals(100, snapshot.getCreditScore());
    }

    @Test
    void hasNoStoredUserOrCredentialSurface() {
        SessionUser session = SessionUser.from(savedUser());
        assertTrue(Arrays.stream(SessionUser.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> field.getType() == int.class || field.getType() == String.class
                        || field.getType() == Role.class));
        assertTrue(Arrays.stream(SessionUser.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().matches(".*(password|hash|salt|credential).*")));
        assertTrue(Arrays.stream(SessionUser.class.getMethods())
                .noneMatch(method -> method.getName().matches("get(Password.*|Salt|User|Credentials)")));
        assertFalse(session.toString().contains("secret_hash_marker"));
        assertFalse(session.toString().contains("secret_salt_marker"));
    }

    @Test
    void rejectsInvalidMetadataAndUnsavedUsers() {
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(0, "u", "", Role.STUDENT, 100));
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(-1, "u", "", Role.STUDENT, 100));
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(1, null, "", Role.STUDENT, 100));
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(1, " ", "", Role.STUDENT, 100));
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(1, "u", null, Role.STUDENT, 100));
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(1, "u", "", null, 100));
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(1, "u", "", Role.STUDENT, -1));
        assertThrows(IllegalArgumentException.class, () -> new SessionUser(1, "u", "", Role.STUDENT, 151));
        assertThrows(NullPointerException.class, () -> SessionUser.from(null));
        assertThrows(IllegalArgumentException.class,
                () -> SessionUser.from(new User("unsaved", "hash", "salt", "")));
    }

    @Test
    void acceptsCreditBoundsAndEmptyNickname() {
        assertEquals(0, new SessionUser(1, "u", "", Role.STUDENT, 0).getCreditScore());
        assertEquals(150, new SessionUser(1, "u", "", Role.ADMIN, 150).getCreditScore());
    }

    @Test
    void convertsPersistedUserWithoutChangingAuthenticationData() {
        UserDaoImpl users = new UserDaoImpl(DBUtil.forPath(directory.resolve("session.db")));
        User admin = users.findById(1).orElseThrow();
        String originalHash = admin.getPasswordHash();
        String originalSalt = admin.getSalt();
        SessionUser snapshot = SessionUser.from(admin);
        assertEquals(admin.getUserId(), snapshot.getUserId());
        assertEquals(admin.getUsername(), snapshot.getUsername());
        assertEquals(admin.getNickname(), snapshot.getNickname());
        assertEquals(admin.getCreditScore(), snapshot.getCreditScore());
        assertTrue(snapshot.isAdmin());
        assertEquals(originalHash, admin.getPasswordHash());
        assertEquals(originalSalt, users.findById(1).orElseThrow().getSalt());
    }

    private static User savedUser() {
        User user = new User("safe_user", "secret_hash_marker", "secret_salt_marker", "安全昵称");
        user.setUserId(2);
        return user;
    }
}
