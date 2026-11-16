package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.StatDao;
import com.campus.recycle.entity.CategoryStat;
import com.campus.recycle.entity.MonthStat;
import com.campus.recycle.entity.StatCounts;
import com.campus.recycle.entity.StatOverview;
import com.campus.recycle.entity.StatQuery;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** 只读统计实现；分类、月份和概览各自使用单条 SQL 保持内部快照一致。 */
public final class StatDaoImpl extends BaseDao<StatCounts> implements StatDao {
    private static final StatQuery ALL = StatQuery.builder().build();
    @FunctionalInterface
    private interface RowReader<T> { T read(ResultSet result) throws SQLException; }

    /** 使用默认数据库。 */
    public StatDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 独立数据库工厂 */
    public StatDaoImpl(DBUtil database) { super(database); }

    @Override
    public List<CategoryStat> countByCategory() { return countByCategory(ALL); }
    @Override
    public List<MonthStat> countByMonth(String year) { return countByMonth(year, ALL); }
    @Override
    public int countItems(ItemStatus status) { return countItems(status, ALL); }
    @Override
    public int countFlows(FlowStatus status) { return countFlows(status, ALL); }
    @Override
    public StatOverview getOverview() { return getOverview(ALL); }
    @Override
    public List<CategoryStat> countByCategory(StatQuery query) {
        return withConnection(connection -> countByCategory(connection, query));
    }
    @Override
    public List<MonthStat> countByMonth(String year, StatQuery query) {
        return withConnection(connection -> countByMonth(connection, year, query));
    }
    @Override
    public int countItems(ItemStatus status, StatQuery query) {
        return withConnection(connection -> countItems(connection, status, query));
    }
    @Override
    public int countFlows(FlowStatus status, StatQuery query) {
        return withConnection(connection -> countFlows(connection, status, query));
    }
    @Override
    public StatOverview getOverview(StatQuery query) {
        return withConnection(connection -> getOverview(connection, query));
    }
    @Override
    public List<CategoryStat> countByCategory(Connection connection) { return countByCategory(connection, ALL); }
    @Override
    public List<MonthStat> countByMonth(Connection connection, String year) {
        return countByMonth(connection, year, ALL);
    }
    @Override
    public int countItems(Connection connection, ItemStatus status) { return countItems(connection, status, ALL); }
    @Override
    public int countFlows(Connection connection, FlowStatus status) { return countFlows(connection, status, ALL); }
    @Override
    public StatOverview getOverview(Connection connection) { return getOverview(connection, ALL); }
    @Override
    public List<CategoryStat> countByCategory(Connection connection, StatQuery query) {
        StatSql.Sql sql = StatSql.categories(query);
        return onConnection(connection, current -> readRows(current, sql, result -> {
            int category = result.getInt("category_id");
            Integer categoryId = result.wasNull() ? null : category;
            return new CategoryStat(categoryId, result.getString("category_name"), mapRow(result));
        }));
    }
    @Override
    public List<MonthStat> countByMonth(Connection connection, String year, StatQuery query) {
        int yearNumber = StatSql.checkYear(year);
        StatSql.Sql sql = StatSql.months(year, query);
        return onConnection(connection, current -> readRows(current, sql,
                result -> new MonthStat(YearMonth.of(yearNumber, result.getInt("month_no")), mapRow(result))));
    }
    @Override
    public int countItems(Connection connection, ItemStatus status, StatQuery query) {
        return number(connection, StatSql.count(query, false, status == null ? null : status.name()));
    }
    @Override
    public int countFlows(Connection connection, FlowStatus status, StatQuery query) {
        return number(connection, StatSql.count(query, true, status == null ? null : status.name()));
    }
    @Override
    public StatOverview getOverview(Connection connection, StatQuery query) {
        StatSql.Sql sql = StatSql.overview(query);
        return onConnection(connection, current -> {
            List<StatOverview> rows = readRows(current, sql, result -> new StatOverview(mapRow(result),
                    result.getLong("user_count"), result.getDouble("average_credit")));
            if (rows.size() != 1) { throw new SQLException("概览查询未返回一条结果"); }
            return rows.get(0);
        });
    }

    private int number(Connection connection, StatSql.Sql sql) {
        return onConnection(connection, current -> {
            List<Long> rows = readRows(current, sql, result -> result.getLong("quantity"));
            if (rows.size() != 1) { throw new SQLException("计数查询未返回一条结果"); }
            try {
                return Math.toIntExact(rows.get(0));
            } catch (ArithmeticException overflow) {
                throw new SQLException("计数超出冻结接口 int 范围", overflow);
            }
        });
    }

    private static <T> List<T> readRows(Connection connection, StatSql.Sql sql, RowReader<T> reader)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql.text())) {
            for (int index = 0; index < sql.parameters().size(); index++) {
                statement.setObject(index + 1, sql.parameters().get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) { rows.add(reader.read(result)); }
                return List.copyOf(rows);
            }
        }
    }
    @Override
    protected StatCounts mapRow(ResultSet result) throws SQLException {
        try {
            return new StatCounts(result.getLong("on_sale_items"), result.getLong("off_shelf_items"),
                    result.getLong("flowed_items"), result.getLong("pending_flows"),
                    result.getLong("deal_flows"), result.getLong("cancelled_flows"));
        } catch (IllegalArgumentException invalid) {
            throw new SQLException("统计数量不合法", invalid);
        }
    }
}
