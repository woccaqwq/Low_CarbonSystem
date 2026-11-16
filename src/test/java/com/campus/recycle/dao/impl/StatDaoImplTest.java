package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.StatDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.CategoryStat;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.MonthStat;
import com.campus.recycle.entity.StatCounts;
import com.campus.recycle.entity.StatOverview;
import com.campus.recycle.entity.StatQuery;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class StatDaoImplTest {
    @TempDir Path directory;
    private DBUtil database;
    private StatDao stats;

    @BeforeEach
    void setUp() throws Exception {
        database = DBUtil.forPath(directory.resolve("stats.db"));
        stats = new StatDaoImpl(database);
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE item SET created_at='2026-01-01 00:00:00' WHERE item_id=1");
            statement.executeUpdate("UPDATE item SET created_at='2026-02-28 23:59:59' WHERE item_id=2");
            statement.executeUpdate("UPDATE item SET created_at='2026-02-01 10:00:00' WHERE item_id=3");
            statement.executeUpdate("UPDATE item SET created_at='2025-12-31 23:59:59' WHERE item_id=4");
            statement.executeUpdate("UPDATE item SET created_at='2026-12-31 23:59:59' WHERE item_id=5");
            statement.executeUpdate("UPDATE flow_record SET created_at='2025-12-31 23:59:59',"
                    + "finished_at='2026-02-01 00:00:00' WHERE flow_id=1");
            statement.executeUpdate("UPDATE flow_record SET created_at='2026-01-01 00:00:00' WHERE flow_id=2");
            statement.executeUpdate("UPDATE flow_record SET created_at='2026-02-28 23:59:59' WHERE flow_id=3");
        }
    }

    @Test
    void countsCurrentItemAndFlowStatesWithNullMeaningAll() {
        assertEquals(5, stats.countItems(null));
        assertEquals(3, stats.countItems(ItemStatus.ON_SALE));
        assertEquals(1, stats.countItems(ItemStatus.OFF_SHELF));
        assertEquals(1, stats.countItems(ItemStatus.FLOWED));
        assertEquals(3, stats.countFlows(null));
        assertEquals(2, stats.countFlows(FlowStatus.PENDING));
        assertEquals(1, stats.countFlows(FlowStatus.DEAL));
        assertEquals(0, stats.countFlows(FlowStatus.CANCELLED));
    }

    @Test
    void countsDirectCategoriesIncludingZeroRowsWithoutParentDoubleCounting() {
        List<CategoryStat> rows = stats.countByCategory();
        assertEquals(14, rows.size());
        assertEquals(0, category(rows, 1).getCounts().getItemCount());
        CategoryStat books = category(rows, 11);
        assertEquals("专业课教材", books.getCategoryName());
        assertEquals(1, books.getCounts().getOnSaleItemCount());
        assertEquals(1, books.getCounts().getPendingFlowCount());
        assertEquals(1, category(rows, 41).getCounts().getDealFlowCount());
        assertEquals(5, rows.stream().mapToLong(row -> row.getCounts().getItemCount()).sum());
        assertEquals(3, rows.stream().mapToLong(row -> row.getCounts().getFlowCount()).sum());
    }

    @Test
    void doesNotMultiplyItemsWhenOneItemHasMultipleIntents() {
        new FlowRecordDaoImpl(database).insert(new FlowRecord(1, 2, 1, TradeType.SALE, "另一买家"));
        StatCounts books = category(stats.countByCategory(), 11).getCounts();
        assertEquals(1, books.getItemCount());
        assertEquals(2, books.getPendingFlowCount());
        assertEquals(5, stats.getOverview().getCounts().getItemCount());
        assertEquals(4, stats.getOverview().getCounts().getFlowCount());
    }

    @Test
    void retainsUncategorizedItemsAndFlowsAfterCategoryDeletion() {
        new CategoryDaoImpl(database).deleteById(11);
        List<CategoryStat> rows = stats.countByCategory();
        CategoryStat unknown = category(rows, null);
        assertEquals("未分类", unknown.getCategoryName());
        assertEquals(1, unknown.getCounts().getOnSaleItemCount());
        assertEquals(1, unknown.getCounts().getPendingFlowCount());
        assertEquals(5, rows.stream().mapToLong(row -> row.getCounts().getItemCount()).sum());
        StatQuery flowOnly = StatQuery.builder().userId(3).from(LocalDate.of(2026, 1, 1))
                .to(LocalDate.of(2026, 1, 1)).build();
        StatCounts onlyFlow = category(stats.countByCategory(flowOnly), null).getCounts();
        assertEquals(0, onlyFlow.getItemCount());
        assertEquals(1, onlyFlow.getPendingFlowCount());
        assertTrue(stats.countByCategory(range("2000-01-01", "2000-12-31")).stream()
                .noneMatch(row -> row.getCategoryId() == null));
    }

    @Test
    void fillsTwelveMonthsAndUsesCreationVersusCompletionTimes() {
        List<MonthStat> months = stats.countByMonth("2026");
        assertEquals(12, months.size());
        assertEquals(YearMonth.of(2026, 1), months.get(0).getMonth());
        assertEquals(YearMonth.of(2026, 12), months.get(11).getMonth());
        assertEquals(1, months.get(0).getCounts().getOnSaleItemCount());
        assertEquals(1, months.get(0).getCounts().getPendingFlowCount());
        assertEquals(2, months.get(1).getCounts().getOnSaleItemCount());
        assertEquals(1, months.get(1).getCounts().getDealFlowCount());
        assertEquals(1, months.get(1).getCounts().getPendingFlowCount());
        assertEquals(1, months.get(11).getCounts().getOffShelfItemCount());
        assertEquals(0, months.get(2).getCounts().getItemCount());
        assertEquals(4, months.stream().mapToLong(row -> row.getCounts().getItemCount()).sum());
        assertEquals(1, stats.countByMonth("2025").get(11).getCounts().getFlowedItemCount());
        assertEquals(0, stats.countByMonth("2025").get(11).getCounts().getDealFlowCount());
    }

    @Test
    void assignsCancelledFlowToCompletionYearNotIntentYear() throws Exception {
        new FlowRecordDaoImpl(database).cancel(2, "跨年取消");
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE flow_record SET finished_at='2027-01-01 00:00:00' WHERE flow_id=2");
        }
        assertEquals(0, stats.countByMonth("2026").get(0).getCounts().getPendingFlowCount());
        assertEquals(0, stats.countByMonth("2026").get(0).getCounts().getCancelledFlowCount());
        assertEquals(1, stats.countByMonth("2027").get(0).getCounts().getCancelledFlowCount());
    }

    @Test
    void appliesInclusiveDateBoundsAndIntersectsRequestedYear() {
        StatQuery day = range("2026-02-28", "2026-02-28");
        assertEquals(1, stats.countItems(null, day));
        assertEquals(1, stats.countFlows(FlowStatus.PENDING, day));
        assertEquals(0, stats.countFlows(FlowStatus.DEAL, day));
        StatQuery year = range("2026-01-01", "2026-12-31");
        assertEquals(4, stats.countItems(null, year));
        assertEquals(3, stats.countFlows(null, year));
        assertEquals(0, stats.countByMonth("2025", year).stream()
                .mapToLong(row -> row.getCounts().getItemCount()).sum());
    }

    @Test
    void scopesItemsToOwnerAndFlowsToEitherParticipantWithoutOrLeakage() {
        StatQuery user = StatQuery.builder().userId(2).build();
        assertEquals(3, stats.countItems(null, user));
        assertEquals(3, stats.countFlows(null, user));
        StatQuery january = StatQuery.builder().userId(3).from(LocalDate.of(2026, 1, 1))
                .to(LocalDate.of(2026, 1, 1)).build();
        assertEquals(0, stats.countItems(null, january));
        assertEquals(1, stats.countFlows(null, january));
        assertEquals(1, category(stats.countByCategory(january), 11).getCounts().getPendingFlowCount());
        assertEquals(0, stats.countByMonth("2026", january).get(1).getCounts().getFlowCount());
        StatQuery missing = StatQuery.builder().userId(9999).build();
        assertEquals(0, stats.getOverview(missing).getUserCount());
        assertEquals(0, stats.getOverview(missing).getCounts().getFlowCount());
    }

    @Test
    void buildsOverviewAndKeepsCurrentUserSnapshotIndependentOfResourceDates() {
        StatOverview overview = stats.getOverview();
        assertEquals(3, overview.getUserCount());
        assertEquals(316.0 / 3, overview.getAverageCreditScore(), 0.000001);
        assertEquals(5, overview.getCounts().getItemCount());
        assertEquals(3, overview.getCounts().getFlowCount());
        new UserDaoImpl(database).updateStatus(3, 1);
        assertEquals(3, stats.getOverview().getUserCount());
        StatOverview past = stats.getOverview(range("2000-01-01", "2000-12-31"));
        assertEquals(0, past.getCounts().getItemCount());
        assertEquals(3, past.getUserCount());
        assertEquals(100.0, stats.getOverview(StatQuery.builder().userId(1).build()).getAverageCreditScore());
    }

    @Test
    void returnsZerosForEmptyBusinessTables() throws Exception {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            for (String table : List.of("review", "flow_record", "favorite", "item", "operation_log", "sys_user")) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
        StatOverview overview = stats.getOverview();
        assertEquals(0, overview.getUserCount());
        assertEquals(0.0, overview.getAverageCreditScore());
        assertEquals(0, overview.getCounts().getItemCount());
        assertEquals(0, overview.getCounts().getFlowCount());
        assertEquals(14, stats.countByCategory().size());
        assertEquals(12, stats.countByMonth("2026").size());
        assertEquals(0, stats.countItems(null));
        assertEquals(0, stats.countFlows(null));
    }

    @Test
    void handlesLeapDayAndExtremeSupportedYears() {
        Item item = new Item();
        item.setOwnerId(2);
        item.setTitle("闰日物品");
        item.setCreatedAt(LocalDateTime.of(2024, 2, 29, 23, 59, 59));
        new ItemDaoImpl(database).insert(item);
        assertEquals(1, stats.countItems(null, range("2024-02-29", "2024-02-29")));
        assertEquals(1, stats.countByMonth("2024").get(1).getCounts().getItemCount());
        assertEquals(12, stats.countByMonth("0001").size());
        assertEquals(12, stats.countByMonth("9999").size());
        Item earliest = new Item();
        earliest.setOwnerId(2);
        earliest.setTitle("最早年份物品");
        earliest.setCreatedAt(LocalDateTime.of(1, 1, 1, 0, 0));
        new ItemDaoImpl(database).insert(earliest);
        Item latest = new Item();
        latest.setOwnerId(2);
        latest.setTitle("最晚年份物品");
        latest.setCreatedAt(LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        new ItemDaoImpl(database).insert(latest);
        assertEquals(1, stats.countByMonth("0001").get(0).getCounts().getItemCount());
        assertEquals(1, stats.countByMonth("9999").get(11).getCounts().getItemCount());
        assertEquals(1, stats.countItems(null, range("0001-01-01", "0001-01-01")));
        assertEquals(1, stats.countItems(null, range("9999-12-31", "9999-12-31")));
    }

    @Test
    void fallsBackToCreationTimeForLegacyTerminalRecordWithoutFinishTime() throws Exception {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE flow_record SET finished_at=NULL WHERE flow_id=1");
        }
        assertEquals(1, stats.countByMonth("2025").get(11).getCounts().getDealFlowCount());
        assertEquals(0, stats.countByMonth("2026").get(1).getCounts().getDealFlowCount());
    }

    @Test
    void seesCallerTransactionAndRestoresStatisticsAfterRollback() throws Exception {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            new ItemDaoImpl(database).updateStatus(connection, 1, ItemStatus.OFF_SHELF);
            new FlowRecordDaoImpl(database).cancel(connection, 2, "统计事务测试");
            statement.executeUpdate("UPDATE flow_record SET finished_at='2026-01-02 12:00:00' WHERE flow_id=2");
            new UserDaoImpl(database).updateCreditScore(connection, 2, 2);
            StatQuery all = StatQuery.builder().build();
            assertEquals(2, stats.countItems(connection, ItemStatus.OFF_SHELF));
            assertEquals(1, stats.countFlows(connection, FlowStatus.CANCELLED, all));
            assertEquals(1, category(stats.countByCategory(connection, all), 11).getCounts().getCancelledFlowCount());
            assertEquals(1, stats.countByMonth(connection, "2026", all).get(0).getCounts().getCancelledFlowCount());
            assertEquals(106.0, stats.getOverview(connection).getAverageCreditScore());
            assertEquals(1, stats.getOverview().getCounts().getOffShelfItemCount());
            assertFalse(connection.getAutoCommit());
            assertFalse(connection.isClosed());
            connection.rollback();
        }
        assertEquals(1, stats.countItems(ItemStatus.OFF_SHELF));
        assertEquals(0, stats.countFlows(FlowStatus.CANCELLED));
        assertEquals(316.0 / 3, stats.getOverview().getAverageCreditScore(), 0.000001);
    }

    @Test
    void executesAllQueriesOnReadOnlyConnectionWithoutChangingOwnership() throws Exception {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only=ON");
            StatQuery all = StatQuery.builder().build();
            assertEquals(14, stats.countByCategory(connection).size());
            assertEquals(12, stats.countByMonth(connection, "2026").size());
            assertEquals(5, stats.countItems(connection, null));
            assertEquals(3, stats.countFlows(connection, null));
            assertEquals(5, stats.countItems(connection, null, all));
            assertEquals(3, stats.getOverview(connection, all).getUserCount());
            assertTrue(connection.getAutoCommit());
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void rejectsInvalidYearsAndNullScope() {
        for (String year : List.of("2026 OR 1=1", "abcd", "0000", "10000", " 2026", "２０２６")) {
            assertThrows(IllegalArgumentException.class, () -> stats.countByMonth(year));
        }
        assertThrows(IllegalArgumentException.class, () -> stats.countByMonth(null));
        assertThrows(IllegalArgumentException.class, () -> stats.countByCategory((StatQuery) null));
        assertThrows(IllegalArgumentException.class, () -> stats.countItems((ItemStatus) null, (StatQuery) null));
        assertThrows(IllegalArgumentException.class, () -> stats.getOverview((StatQuery) null));
    }

    @Test
    void reportsClosedExternalConnection() throws Exception {
        Connection connection = database.getConnection();
        connection.close();
        assertThrows(DataAccessException.class, () -> stats.countByCategory(connection));
        assertThrows(DataAccessException.class, () -> stats.countByMonth(connection, "2026"));
        assertThrows(DataAccessException.class, () -> stats.countItems(connection, null));
        assertThrows(DataAccessException.class, () -> stats.countFlows(connection, null));
        assertThrows(DataAccessException.class, () -> stats.getOverview(connection));
    }

    @Test
    void factoryExposesStableStatDao() {
        assertInstanceOf(StatDao.class, DaoFactory.getStatDao());
        assertSame(DaoFactory.getStatDao(), DaoFactory.getStatDao());
    }

    private static StatQuery range(String from, String to) {
        return StatQuery.builder().from(LocalDate.parse(from)).to(LocalDate.parse(to)).build();
    }
    private static CategoryStat category(List<CategoryStat> rows, Integer id) {
        return rows.stream().filter(row -> Objects.equals(id, row.getCategoryId())).findFirst().orElseThrow();
    }
}
