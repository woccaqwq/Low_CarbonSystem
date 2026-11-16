package com.campus.recycle.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationLogTest {
    @Test
    void supportsSystemActorAndDefaultsWithoutInventingAction() {
        OperationLog log = new OperationLog();
        assertNull(log.getUserId());
        assertEquals("", log.getDetail());
        assertNull(log.getCreatedAt());
        assertNull(log.getAction());
        OperationLog system = new OperationLog(null, "SYSTEM_START", "初始化成功");
        assertNull(system.getUserId());
    }

    @Test
    void rejectsInvalidIdentifiersAndMissingText() {
        assertThrows(IllegalArgumentException.class, () -> new OperationLog(0, "LOGIN", ""));
        assertThrows(IllegalArgumentException.class, () -> new OperationLog(1, " ", ""));
        assertThrows(IllegalArgumentException.class, () -> new OperationLog(1, "LOGIN", null));
        OperationLog log = new OperationLog(1, "LOGIN", "");
        assertThrows(IllegalArgumentException.class, () -> log.setLogId(-1));
        log.setUserId(null);
        assertNull(log.getUserId());
    }

    @Test
    void doesNotExposeDetailInDebugDescription() {
        OperationLog log = new OperationLog(1, "TEST", "sensitive_test_detail");
        assertFalse(log.toString().contains("sensitive_test_detail"));
    }
}
