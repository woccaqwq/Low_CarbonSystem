package com.campus.recycle.strategy;

/** 发布时间从新到旧。 */
public final class TimeDescStrategy implements SortStrategy {
    @Override
    public String toOrderBy() { return "i.created_at DESC"; }
}
