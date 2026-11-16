package com.campus.recycle.entity;

import java.time.YearMonth;
import java.util.Objects;

/** 一个日历月份的不可变统计。 */
public final class MonthStat {
    private final YearMonth month;
    private final StatCounts counts;
    /** @param month 月份 @param counts 该月计数 */
    public MonthStat(YearMonth month, StatCounts counts) {
        this.month = Objects.requireNonNull(month, "month");
        this.counts = Objects.requireNonNull(counts, "counts");
    }
    /** @return 月份，可用 toString 格式化为 yyyy-MM */
    public YearMonth getMonth() { return month; }
    /** @return 不可变计数 */
    public StatCounts getCounts() { return counts; }
}
