package com.campus.recycle.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationExceptionTest {
    @Test
    void locatesFieldAndCanBeCaughtAsBusinessFailure() {
        ValidationException failure = new ValidationException("username", ErrorCode.USERNAME_DUPLICATED);
        BusinessException business = failure;
        assertEquals("username", failure.getField());
        assertEquals(ErrorCode.USERNAME_DUPLICATED, business.getCode());
        assertEquals(ErrorCode.USERNAME_DUPLICATED.getMessage(), business.getMessage());
    }

    @Test
    void supportsWholeFormWithoutInventingField() {
        assertNull(new ValidationException(ErrorCode.VALIDATION_ERROR).getField());
        assertNull(new ValidationException(null, ErrorCode.VALIDATION_ERROR).getField());
    }

    @Test
    void preservesCausesForBothFormAndFieldVariants() {
        IllegalArgumentException cause = new IllegalArgumentException("private_input_marker");
        ValidationException field = new ValidationException("price", ErrorCode.VALIDATION_ERROR, cause);
        ValidationException form = new ValidationException(ErrorCode.VALIDATION_ERROR, cause);
        assertSame(cause, field.getCause());
        assertSame(cause, form.getCause());
        assertEquals("price", field.getField());
        assertNull(form.getField());
        assertFalse(field.getMessage().contains("private_input_marker"));
        assertFalse(field.getMessage().contains("price"));
    }

    @Test
    void rejectsBlankFieldIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ValidationException("", ErrorCode.VALIDATION_ERROR));
        assertThrows(IllegalArgumentException.class,
                () -> new ValidationException(" \t", ErrorCode.VALIDATION_ERROR, null));
    }

    @Test
    void requiresCodeAndAllowsAnAbsentCause() {
        assertThrows(NullPointerException.class, () -> new ValidationException((ErrorCode) null));
        assertThrows(NullPointerException.class, () -> new ValidationException("title", null));
        assertNull(new ValidationException("title", ErrorCode.VALIDATION_ERROR, null).getCause());
    }
}
