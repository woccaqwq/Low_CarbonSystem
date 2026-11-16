package com.campus.recycle.entity;

import java.time.LocalDate;

/** 不可变统计范围，日期为闭区间；null 表示该维度不限。 */
public final class StatQuery {
    private final LocalDate from;
    private final LocalDate to;
    private final Integer userId;

    private StatQuery(Builder builder) {
        checkDate(builder.from);
        checkDate(builder.to);
        if (builder.from != null && builder.to != null && builder.from.isAfter(builder.to)) {
            throw new IllegalArgumentException("起始日期不能晚于结束日期");
        }
        if (builder.userId != null && builder.userId <= 0) {
            throw new IllegalArgumentException("用户编号须为正整数");
        }
        from = builder.from;
        to = builder.to;
        userId = builder.userId;
    }
    /** @return 默认全平台全部时间的 Builder */
    public static Builder builder() { return new Builder(); }
    /** @return 包含在内的起始日期，null 不限 */
    public LocalDate getFrom() { return from; }
    /** @return 包含在内的结束日期，null 不限 */
    public LocalDate getTo() { return to; }
    /** @return 用户范围，null 全平台 */
    public Integer getUserId() { return userId; }

    private static void checkDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new IllegalArgumentException("日期年份须在 0001–9999 之间");
        }
    }
    /** 可复用 Builder，每次 build 产生独立快照。 */
    public static final class Builder {
        private LocalDate from;
        private LocalDate to;
        private Integer userId;
        private Builder() { }
        /** @param value 起始日期 @return 当前 Builder */
        public Builder from(LocalDate value) { from = value; return this; }
        /** @param value 结束日期 @return 当前 Builder */
        public Builder to(LocalDate value) { to = value; return this; }
        /** @param value 用户范围 @return 当前 Builder */
        public Builder userId(Integer value) { userId = value; return this; }
        /** @return 经校验的不可变范围 */
        public StatQuery build() { return new StatQuery(this); }
    }
}
