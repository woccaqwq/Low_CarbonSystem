package com.campus.recycle.dao;

import com.campus.recycle.entity.CategoryStat;
import com.campus.recycle.entity.MonthStat;
import com.campus.recycle.entity.StatOverview;
import com.campus.recycle.entity.StatQuery;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.FlowStatus;
import java.sql.Connection;
import java.util.List;

/** 只读统计接口；技术错误抛 DataAccessException，外部连接不由 DAO 管理。 */
public interface StatDao {
    /** @return 全部直接分类的统计，含零值，必要时含未分类组 */
    List<CategoryStat> countByCategory();
    /** @param year 0001–9999 四位年份 @return 十二个月的统计，空月份为零 */
    List<MonthStat> countByMonth(String year);
    /** @param status 状态或 null（全部） @return 物品数 */
    int countItems(ItemStatus status);
    /** @param status 状态或 null（全部） @return 流转记录数 */
    int countFlows(FlowStatus status);
    /** @param query 范围 @return 分类统计 */
    List<CategoryStat> countByCategory(StatQuery query);
    /** @param year 年份 @param query 范围，与年份取交集 @return 十二个月统计 */
    List<MonthStat> countByMonth(String year, StatQuery query);
    /** @param status 物品状态或 null @param query 范围 @return 数量 */
    int countItems(ItemStatus status, StatQuery query);
    /** @param status 流转状态或 null @param query 范围 @return 数量 */
    int countFlows(FlowStatus status, StatQuery query);
    /** @return 全平台当前概览 */
    StatOverview getOverview();
    /** @param query 范围；用户卡片不按资源日期筛选 @return 概览 */
    StatOverview getOverview(StatQuery query);
    /** @param connection 外部连接 @return 分类统计 */
    List<CategoryStat> countByCategory(Connection connection);
    /** @param connection 外部连接 @param year 年份 @return 十二个月统计 */
    List<MonthStat> countByMonth(Connection connection, String year);
    /** @param connection 外部连接 @param status 状态或 null @return 数量 */
    int countItems(Connection connection, ItemStatus status);
    /** @param connection 外部连接 @param status 状态或 null @return 数量 */
    int countFlows(Connection connection, FlowStatus status);
    /** @param connection 外部连接 @param query 范围 @return 分类统计 */
    List<CategoryStat> countByCategory(Connection connection, StatQuery query);
    /** @param connection 外部连接 @param year 年份 @param query 范围 @return 月份统计 */
    List<MonthStat> countByMonth(Connection connection, String year, StatQuery query);
    /** @param connection 外部连接 @param status 状态或 null @param query 范围 @return 数量 */
    int countItems(Connection connection, ItemStatus status, StatQuery query);
    /** @param connection 外部连接 @param status 状态或 null @param query 范围 @return 数量 */
    int countFlows(Connection connection, FlowStatus status, StatQuery query);
    /** @param connection 外部连接 @return 概览 */
    StatOverview getOverview(Connection connection);
    /** @param connection 外部连接 @param query 范围 @return 概览 */
    StatOverview getOverview(Connection connection, StatQuery query);
}
