package com.campus.recycle.exception;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {
    @Test
    void retainsEveryCodeNamedInOriginalDesignAndAddsGeneralValidation() {
        List<String> expected = List.of("USER_NOT_FOUND", "PASSWORD_ERROR", "USER_DISABLED", "USERNAME_DUPLICATED",
                "ITEM_NOT_ON_SALE", "ITEM_OWN_SELF", "FLOW_DUPLICATED", "FLOW_STATUS_ILLEGAL", "PERMISSION_DENIED",
                "REVIEW_NOT_ALLOWED", "FLOW_NOT_FOUND", "DB_ERROR", "VALIDATION_ERROR");
        for (String code : expected) {
            assertDoesNotThrow(() -> ErrorCode.valueOf(code));
        }
    }

    @Test
    void doesNotDiscloseWhetherLoginUserExists() {
        assertEquals("用户名或密码错误", ErrorCode.USER_NOT_FOUND.getMessage());
        assertEquals(ErrorCode.USER_NOT_FOUND.getMessage(), ErrorCode.PASSWORD_ERROR.getMessage());
    }

    @Test
    void suppliesAVisibleMessageForEveryCode() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getMessage());
            assertFalse(code.getMessage().isBlank());
        }
        assertEquals("数据操作失败，请联系管理员", ErrorCode.DB_ERROR.getMessage());
    }
}
