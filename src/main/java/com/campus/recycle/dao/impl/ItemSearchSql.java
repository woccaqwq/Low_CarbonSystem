package com.campus.recycle.dao.impl;

import com.campus.recycle.entity.ItemQuery;
import java.util.ArrayList;
import java.util.List;

/** 包内 SQL 条件构造；所有条件片段来自固定文本，业务值单独绑定。 */
record ItemSearchSql(String where, List<Object> parameters) {
    static ItemSearchSql from(ItemQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String literal = query.getKeyword().strip().replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + literal + "%";
            where.append(" AND (i.title LIKE ? ESCAPE '\\' OR i.description LIKE ? ESCAPE '\\' "
                    + "OR i.tags LIKE ? ESCAPE '\\')");
            parameters.addAll(List.of(pattern, pattern, pattern));
        }
        if (query.getCategoryId() != null) {
            where.append(" AND i.category_id IN (WITH RECURSIVE chosen(category_id) AS ("
                    + "SELECT category_id FROM category WHERE category_id=? UNION "
                    + "SELECT c.category_id FROM category c JOIN chosen p ON c.parent_id=p.category_id) "
                    + "SELECT category_id FROM chosen)");
            parameters.add(query.getCategoryId());
        }
        add(where, parameters, " AND i.owner_id=?", query.getOwnerId());
        add(where, parameters, " AND i.price>=?", query.getMinPrice());
        add(where, parameters, " AND i.price<=?", query.getMaxPrice());
        add(where, parameters, " AND i.trade_type=?",
                query.getTradeType() == null ? null : query.getTradeType().name());
        add(where, parameters, " AND i.status=?", query.getStatus() == null ? null : query.getStatus().name());
        return new ItemSearchSql(where.toString(), List.copyOf(parameters));
    }

    private static void add(StringBuilder where, List<Object> parameters, String condition, Object value) {
        if (value != null) {
            where.append(condition);
            parameters.add(value);
        }
    }
}
