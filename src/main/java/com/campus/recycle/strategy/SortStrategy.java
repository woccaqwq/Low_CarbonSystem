package com.campus.recycle.strategy;

/** 排序策略；ItemQuery 仅接受指定物品/信用列的 ASC/DESC 排序表达式。 */
@FunctionalInterface
public interface SortStrategy {
    /** @return 排序片段，不包含 ORDER BY，构建查询时验证并冻结 */
    String toOrderBy();
}
