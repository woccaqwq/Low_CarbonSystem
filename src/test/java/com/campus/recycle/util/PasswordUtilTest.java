package com.campus.recycle.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {
    private final PasswordUtil passwords = new PasswordUtil();

    @ParameterizedTest
    @CsvSource({
            "admin123,2lFmtZvCAifuCQjRuFzFh5KojasY/zSk+67mIhff61Q=,ytSKAF0hG0IsFXo4ed3mNg==",
            "123456,VRinVv4gsgJNrsvDJxZJqBWuzVDPKg9qe/ZXFsawLY8=,uuGegOWO6poks79ph4t1LA==",
            "123456,y5Ji0dVopjkzyB0OGVMgS9i07brSXPZR930ht3kveBw=,sSrNWgAcISm+U2e5Lp5TXA=="
    })
    void verifiesExistingSeedCredentials(String raw, String hash, String salt) {
        assertEquals(hash, passwords.hash(raw, salt));
        assertTrue(passwords.verify(raw, hash, salt));
        assertFalse(passwords.verify(raw + "wrong", hash, salt));
    }

    @Test
    void createsIndependentSaltsAndHashesForSamePassword() {
        String firstSalt = passwords.generateSalt();
        String secondSalt = passwords.generateSalt();
        assertEquals(16, Base64.getDecoder().decode(firstSalt).length);
        assertNotEquals(firstSalt, secondSalt);
        String firstHash = passwords.hash("password123", firstSalt);
        assertEquals(32, Base64.getDecoder().decode(firstHash).length);
        assertNotEquals(firstHash, passwords.hash("password123", secondSalt));
        assertTrue(passwords.verify("password123", firstHash, firstSalt));
    }

    @Test
    void preservesWhitespaceAndUnicodeInPassword() {
        String salt = passwords.generateSalt();
        String hash = passwords.hash(" 中文密码123 ", salt);
        assertTrue(passwords.verify(" 中文密码123 ", hash, salt));
        assertFalse(passwords.verify("中文密码123", hash, salt));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not base64!", "YQ=="})
    void rejectsInvalidSaltWhenHashingAndFailsClosedWhenVerifying(String invalidSalt) {
        assertThrows(IllegalArgumentException.class, () -> passwords.hash("password123", invalidSalt));
        assertFalse(passwords.verify("password123",
                "2lFmtZvCAifuCQjRuFzFh5KojasY/zSk+67mIhff61Q=", invalidSalt));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not base64!", "YQ=="})
    void failsClosedForInvalidStoredHash(String invalidHash) {
        assertFalse(passwords.verify("password123", invalidHash, "ytSKAF0hG0IsFXo4ed3mNg=="));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingPassword(String raw) {
        assertThrows(IllegalArgumentException.class,
                () -> passwords.hash(raw, "ytSKAF0hG0IsFXo4ed3mNg=="));
        assertFalse(passwords.verify(raw, "2lFmtZvCAifuCQjRuFzFh5KojasY/zSk+67mIhff61Q=",
                "ytSKAF0hG0IsFXo4ed3mNg=="));
    }
}
