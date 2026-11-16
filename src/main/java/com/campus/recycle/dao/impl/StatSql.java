package com.campus.recycle.dao.impl;

import com.campus.recycle.entity.StatQuery;
import java.util.ArrayList;
import java.util.List;

/** 包内统计 SQL 构造器，拼接内容均为固定片段，范围值独立绑定。 */
final class StatSql {
    private static final String FLOW_TIME = "(CASE WHEN f.status='PENDING' THEN f.created_at "
            + "ELSE COALESCE(f.finished_at,f.created_at) END)";
    private static final String ITEM_SUMS = "SUM(CASE WHEN i.status='ON_SALE' THEN 1 ELSE 0 END) AS on_sale_items,"
            + "SUM(CASE WHEN i.status='OFF_SHELF' THEN 1 ELSE 0 END) AS off_shelf_items,"
            + "SUM(CASE WHEN i.status='FLOWED' THEN 1 ELSE 0 END) AS flowed_items";
    private static final String FLOW_SUMS = "SUM(CASE WHEN f.status='PENDING' THEN 1 ELSE 0 END) AS pending_flows,"
            + "SUM(CASE WHEN f.status='DEAL' THEN 1 ELSE 0 END) AS deal_flows,"
            + "SUM(CASE WHEN f.status='CANCELLED' THEN 1 ELSE 0 END) AS cancelled_flows";
    private static final String JOINED_COUNTS = "COALESCE(ic.on_sale_items,0) AS on_sale_items,"
            + "COALESCE(ic.off_shelf_items,0) AS off_shelf_items,COALESCE(ic.flowed_items,0) AS flowed_items,"
            + "COALESCE(fc.pending_flows,0) AS pending_flows,COALESCE(fc.deal_flows,0) AS deal_flows,"
            + "COALESCE(fc.cancelled_flows,0) AS cancelled_flows";

    record Sql(String text, List<Object> parameters) {
        Sql { parameters = List.copyOf(parameters); }
    }
    private record Scope(String where, List<Object> parameters) { }
    private StatSql() { }

    static Sql categories(StatQuery query) {
        Scope items = scope(query, false, null);
        Scope flows = scope(query, true, null);
        String sql = "WITH item_counts AS (SELECT i.category_id," + ITEM_SUMS + " FROM item i"
                + items.where() + " GROUP BY i.category_id),flow_counts AS (SELECT i.category_id," + FLOW_SUMS
                + " FROM flow_record f JOIN item i ON i.item_id=f.item_id" + flows.where()
                + " GROUP BY i.category_id),buckets AS (SELECT category_id,name,sort_no,0 AS unknown_group FROM category "
                + "UNION ALL SELECT NULL,'未分类',0,1 WHERE EXISTS(SELECT 1 FROM item_counts WHERE category_id IS NULL) "
                + "OR EXISTS(SELECT 1 FROM flow_counts WHERE category_id IS NULL)) "
                + "SELECT b.category_id,b.name AS category_name," + JOINED_COUNTS
                + " FROM buckets b LEFT JOIN item_counts ic ON ic.category_id IS b.category_id "
                + "LEFT JOIN flow_counts fc ON fc.category_id IS b.category_id "
                + "ORDER BY b.unknown_group,b.sort_no,b.category_id";
        return new Sql(sql, combined(items, flows));
    }

    static Sql months(String year, StatQuery query) {
        checkYear(year);
        Scope items = scope(query, false, year);
        Scope flows = scope(query, true, year);
        String sql = "WITH RECURSIVE months(month_no) AS (SELECT 1 UNION ALL "
                + "SELECT month_no+1 FROM months WHERE month_no<12),item_counts AS ("
                + "SELECT CAST(substr(i.created_at,6,2) AS INTEGER) AS month_no," + ITEM_SUMS
                + " FROM item i" + items.where() + " GROUP BY month_no),flow_counts AS ("
                + "SELECT CAST(substr(" + FLOW_TIME + ",6,2) AS INTEGER) AS month_no," + FLOW_SUMS
                + " FROM flow_record f" + flows.where() + " GROUP BY month_no) "
                + "SELECT m.month_no," + JOINED_COUNTS + " FROM months m "
                + "LEFT JOIN item_counts ic ON ic.month_no=m.month_no "
                + "LEFT JOIN flow_counts fc ON fc.month_no=m.month_no ORDER BY m.month_no";
        return new Sql(sql, combined(items, flows));
    }

    static Sql count(StatQuery query, boolean flow, String status) {
        Scope scope = scope(query, flow, null);
        List<Object> parameters = new ArrayList<>(scope.parameters());
        String sql = "SELECT count(*) AS quantity FROM " + (flow ? "flow_record f" : "item i") + scope.where();
        if (status != null) {
            sql += flow ? " AND f.status=?" : " AND i.status=?";
            parameters.add(status);
        }
        return new Sql(sql, parameters);
    }

    static Sql overview(StatQuery query) {
        Scope items = scope(query, false, null);
        Scope flows = scope(query, true, null);
        List<Object> parameters = combined(items, flows);
        String users = "SELECT credit_score FROM sys_user";
        if (query.getUserId() != null) {
            users += " WHERE user_id=?";
            parameters.add(query.getUserId());
        }
        String sql = "WITH scoped_items AS (SELECT i.status FROM item i" + items.where()
                + "),scoped_flows AS (SELECT f.status FROM flow_record f" + flows.where()
                + "),scoped_users AS (" + users + ") SELECT "
                + "(SELECT count(*) FROM scoped_items WHERE status='ON_SALE') AS on_sale_items,"
                + "(SELECT count(*) FROM scoped_items WHERE status='OFF_SHELF') AS off_shelf_items,"
                + "(SELECT count(*) FROM scoped_items WHERE status='FLOWED') AS flowed_items,"
                + "(SELECT count(*) FROM scoped_flows WHERE status='PENDING') AS pending_flows,"
                + "(SELECT count(*) FROM scoped_flows WHERE status='DEAL') AS deal_flows,"
                + "(SELECT count(*) FROM scoped_flows WHERE status='CANCELLED') AS cancelled_flows,"
                + "(SELECT count(*) FROM scoped_users) AS user_count,"
                + "(SELECT COALESCE(AVG(credit_score),0.0) FROM scoped_users) AS average_credit";
        return new Sql(sql, parameters);
    }

    static int checkYear(String year) {
        if (year == null || !year.matches("[0-9]{4}") || year.equals("0000")) {
            throw new IllegalArgumentException("年份须为 0001–9999 的四位数字");
        }
        return Integer.parseInt(year);
    }

    private static Scope scope(StatQuery query, boolean flow, String year) {
        if (query == null) { throw new IllegalArgumentException("统计范围不能为空"); }
        String time = flow ? FLOW_TIME : "i.created_at";
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (year != null) {
            where.append(" AND substr(").append(time).append(",1,4)=?");
            parameters.add(year);
        }
        if (query.getFrom() != null) {
            where.append(" AND ").append(time).append(">=?");
            parameters.add(query.getFrom() + " 00:00:00");
        }
        if (query.getTo() != null) {
            where.append(" AND ").append(time).append("<=?");
            parameters.add(query.getTo() + " 23:59:59");
        }
        if (query.getUserId() != null) {
            where.append(flow ? " AND (f.seller_id=? OR f.buyer_id=?)" : " AND i.owner_id=?");
            parameters.add(query.getUserId());
            if (flow) { parameters.add(query.getUserId()); }
        }
        return new Scope(where.toString(), List.copyOf(parameters));
    }

    private static List<Object> combined(Scope items, Scope flows) {
        List<Object> parameters = new ArrayList<>(items.parameters());
        parameters.addAll(flows.parameters());
        return parameters;
    }
}
