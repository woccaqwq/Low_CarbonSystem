package com.campus.recycle.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class SysConfigTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2030, 1, 2, 3, 4, 5);

    @Test
    void keepsLiteralKeyValueAndTimestamp() {
        SysConfig config = new SysConfig(" Key ", " 中文\n2.4 ", TIME);
        assertEquals(" Key ", config.getConfigKey());
        assertEquals(" 中文\n2.4 ", config.getConfigValue());
        assertEquals(TIME, config.getUpdatedAt());
        assertEquals("", new SysConfig("empty", "", TIME).getConfigValue());
    }

    @Test
    void rejectsMissingMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new SysConfig(null, "v", TIME));
        assertThrows(IllegalArgumentException.class, () -> new SysConfig(" \t", "v", TIME));
        assertThrows(IllegalArgumentException.class, () -> new SysConfig("k", null, TIME));
        assertThrows(IllegalArgumentException.class, () -> new SysConfig("k", "v", null));
    }

    @Test
    void omitsValueFromDebugOutput() {
        assertFalse(new SysConfig("future.secret", "private_config_marker", TIME).toString()
                .contains("private_config_marker"));
    }
}
