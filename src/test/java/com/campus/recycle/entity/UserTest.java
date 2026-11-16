package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    @Test
    void defaultsToEnabledStudentWithInitialCredit() {
        User user = new User("new_user", "secret_hash", "secret_salt", "新同学");
        assertEquals(100, user.getCreditScore());
        assertTrue(user.isEnabled());
        assertFalse(user.isAdmin());
        assertNull(user.getLastLoginAt());
        user.setRole(Role.ADMIN);
        assertTrue(user.isAdmin());
        user.setStatus(1);
        assertFalse(user.isEnabled());
    }

    @Test
    void clampsCreditWithoutOverflow() {
        User user = new User();
        user.adjustCredit(2);
        assertEquals(102, user.getCreditScore());
        user.adjustCredit(Integer.MAX_VALUE);
        assertEquals(150, user.getCreditScore());
        user.adjustCredit(Integer.MIN_VALUE);
        assertEquals(0, user.getCreditScore());
    }

    @Test
    void rejectsImpossibleStates() {
        User user = new User();
        assertThrows(IllegalArgumentException.class, () -> user.setCreditScore(-1));
        assertThrows(IllegalArgumentException.class, () -> user.setCreditScore(151));
        assertThrows(IllegalArgumentException.class, () -> user.setStatus(2));
        assertThrows(IllegalArgumentException.class, () -> user.setRole(null));
    }

    @Test
    void doesNotExposeCredentialsInToString() {
        User user = new User("new_user", "secret_hash", "secret_salt", "同学");
        assertFalse(user.toString().contains("secret_hash"));
        assertFalse(user.toString().contains("secret_salt"));
    }
}
