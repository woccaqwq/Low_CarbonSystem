package com.campus.recycle.entity;

import java.util.Objects;

/** 单个直接分类的统计；null 分类编号代表未分类。 */
public final class CategoryStat {
    private final Integer categoryId;
    private final String categoryName;
    private final StatCounts counts;
    /** @param categoryId 分类或 null @param categoryName 名称 @param counts 分类计数 */
    public CategoryStat(Integer categoryId, String categoryName, StatCounts counts) {
        if (categoryId != null && categoryId <= 0) { throw new IllegalArgumentException("分类编号不合法"); }
        this.categoryId = categoryId;
        this.categoryName = Objects.requireNonNull(categoryName, "categoryName");
        this.counts = Objects.requireNonNull(counts, "counts");
    }
    /** @return 分类编号 */
    public Integer getCategoryId() { return categoryId; }
    /** @return 分类名称 */
    public String getCategoryName() { return categoryName; }
    /** @return 不可变计数 */
    public StatCounts getCounts() { return counts; }
}
