package com.campus.recycle.entity;

import java.time.LocalDateTime;

/** 系统配置的不可变快照；值保持原文，类型及业务范围由使用方校验。 */
public final class SysConfig {
    private final String configKey;
    private final String configValue;
    private final LocalDateTime updatedAt;

    /** @param configKey 非空白字面键 @param configValue 非 null 原文，可为空串 @param updatedAt 非 null 更新时间 */
    public SysConfig(String configKey, String configValue, LocalDateTime updatedAt) {
        if (configKey == null || configKey.isBlank()) { throw new IllegalArgumentException("配置键不能为空"); }
        if (configValue == null) { throw new IllegalArgumentException("配置值不能为 null"); }
        if (updatedAt == null) { throw new IllegalArgumentException("配置更新时间不能为 null"); }
        this.configKey = configKey;
        this.configValue = configValue;
        this.updatedAt = updatedAt;
    }

    /** @return 字面配置键，不做大小写或空格归一化 */
    public String getConfigKey() { return configKey; }
    /** @return 原始配置文本，尚未校验为数字或布尔类型 */
    public String getConfigValue() { return configValue; }
    /** @return 数据库保存的更新时间，本对象不维护该时间 */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** @return 不包含配置值的调试描述 */
    @Override
    public String toString() {
        return "SysConfig{configKey='" + configKey + "', updatedAt=" + updatedAt + "}";
    }
}
