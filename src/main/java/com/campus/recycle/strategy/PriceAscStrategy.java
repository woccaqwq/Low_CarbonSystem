package com.campus.recycle.strategy;

/** 价格从低到高，同价按发布时间倒序。 */
public final class PriceAscStrategy implements SortStrategy {
    @Override
    public String toOrderBy() { return "i.price ASC, i.created_at DESC"; }
}
