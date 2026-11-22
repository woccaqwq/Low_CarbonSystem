package com.campus.recycle.entity;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 一次同快照统计查询的不可变报表，供展示与 CSV 导出使用，不持有凭据。 */
public final class StatReport {
    private final LocalDate from;
    private final LocalDate to;
    private final StatOverview overview;
    private final List<CategoryStat> categories;
    private final List<MonthStat> months;
    private final double co2PerItemKg;
    private final double co2SavedKg;

    /**
     * @param from 起始日期或 null @param to 结束日期或 null
     * @param overview 概览 @param categories 分类统计 @param months 月份统计
     * @param co2PerItemKg 每件减排系数 @param co2SavedKg 估算总减排
     */
    public StatReport(LocalDate from, LocalDate to, StatOverview overview, List<CategoryStat> categories,
                      List<MonthStat> months, double co2PerItemKg, double co2SavedKg) {
        if (!Double.isFinite(co2PerItemKg) || co2PerItemKg < 0
                || !Double.isFinite(co2SavedKg) || co2SavedKg < 0) {
            throw new IllegalArgumentException("减排数值必须是有限非负数");
        }
        this.from = from;
        this.to = to;
        this.overview = Objects.requireNonNull(overview, "overview");
        this.categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        this.months = List.copyOf(Objects.requireNonNull(months, "months"));
        this.co2PerItemKg = co2PerItemKg;
        this.co2SavedKg = co2SavedKg;
    }

    /** @return 起始日期，null 表示不限 */
    public LocalDate getFrom() { return from; }
    /** @return 结束日期，null 表示不限 */
    public LocalDate getTo() { return to; }
    /** @return 概览快照 */
    public StatOverview getOverview() { return overview; }
    /** @return 不可修改的分类统计 */
    public List<CategoryStat> getCategories() { return categories; }
    /** @return 不可修改的月份统计 */
    public List<MonthStat> getMonths() { return months; }
    /** @return 每件减排系数 */
    public double getCo2PerItemKg() { return co2PerItemKg; }
    /** @return 估算总减排千克数 */
    public double getCo2SavedKg() { return co2SavedKg; }
    /** @return 报表范围内的成交流转数 */
    public long getDealFlowCount() { return overview.getCounts().getDealFlowCount(); }
    /** @return 报表范围内的已流转物品数 */
    public long getFlowedItemCount() { return overview.getCounts().getFlowedItemCount(); }
}
