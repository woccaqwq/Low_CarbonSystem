package com.campus.recycle.strategy;

/** 发布人信用从高到低，同信用按发布时间倒序。 */
public final class CreditDescStrategy implements SortStrategy {
    @Override
    public String toOrderBy() { return "u.credit_score DESC, i.created_at DESC"; }
}
