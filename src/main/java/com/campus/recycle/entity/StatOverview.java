package com.campus.recycle.entity;

import java.util.Objects;

/** 单次 SQL 读取的统计卡片数据，用户指标为当前账号快照。 */
public final class StatOverview {
    private final StatCounts counts;
    private final long userCount;
    private final double averageCreditScore;
    /** @param counts 物品/流转计数 @param userCount 用户数 @param averageCreditScore 当前平均信用 */
    public StatOverview(StatCounts counts, long userCount, double averageCreditScore) {
        if (userCount < 0 || !Double.isFinite(averageCreditScore)
                || averageCreditScore < 0 || averageCreditScore > 150) {
            throw new IllegalArgumentException("用户统计值不合法");
        }
        this.counts = Objects.requireNonNull(counts, "counts");
        this.userCount = userCount;
        this.averageCreditScore = averageCreditScore;
    }
    /** @return 不可变物品/流转计数 */
    public StatCounts getCounts() { return counts; }
    /** @return 当前用户数 */
    public long getUserCount() { return userCount; }
    /** @return 当前平均信用，空用户集为 0.0 */
    public double getAverageCreditScore() { return averageCreditScore; }
}
