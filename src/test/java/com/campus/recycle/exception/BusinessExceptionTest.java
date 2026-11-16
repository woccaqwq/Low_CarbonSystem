package com.campus.recycle.exception;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {
    @Test
    void carriesCodeAndFixedMessageForEveryCode() {
        for (ErrorCode code : ErrorCode.values()) {
            BusinessException failure = new BusinessException(code);
            assertSame(code, failure.getCode());
            assertEquals(code.getMessage(), failure.getMessage());
            assertNull(failure.getCause());
            assertInstanceOf(RuntimeException.class, failure);
        }
    }

    @Test
    void preservesCauseWithoutLeakingTechnicalOrInputDetailsIntoMessage() {
        SQLException cause = new SQLException("SQL text password=private_test_marker");
        BusinessException failure = new BusinessException(ErrorCode.DB_ERROR, cause);
        assertSame(cause, failure.getCause());
        assertEquals(ErrorCode.DB_ERROR.getMessage(), failure.getMessage());
        assertFalse(failure.toString().contains("private_test_marker"));
    }

    @Test
    void acceptsAbsentCauseButRequiresCode() {
        assertNull(new BusinessException(ErrorCode.PERMISSION_DENIED, null).getCause());
        assertThrows(NullPointerException.class, () -> new BusinessException(null));
        assertThrows(NullPointerException.class, () -> new BusinessException(null, new RuntimeException()));
    }

    @Test
    void leavesDataAccessFailuresOnTheirExistingIndependentBranch() {
        SQLException cause = new SQLException("fixture");
        RuntimeException technical = new DataAccessException("通用提示", cause);
        assertFalse(technical instanceof BusinessException);
        assertSame(cause, technical.getCause());
        assertEquals("通用提示", technical.getMessage());
    }
}
