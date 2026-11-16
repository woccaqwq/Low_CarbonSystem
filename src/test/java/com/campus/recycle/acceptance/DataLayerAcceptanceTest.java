package com.campus.recycle.acceptance;

import com.campus.recycle.dao.*;
import com.campus.recycle.dao.impl.*;
import com.campus.recycle.entity.*;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

/** 直接编排 DAO 的数据层验收；不代替尚未实现的 Service 业务规则测试。 */
class DataLayerAcceptanceTest {
    @TempDir Path directory;
    private DBUtil database;
    private UserDao users;
    private CategoryDao categories;
    private ItemDao items;
    private FavoriteDao favorites;
    private FlowRecordDao flows;
    private ReviewDao reviews;
    private StatDao stats;
    private OperationLogDao logs;

    private record Scenario(int seller, int buyer, int otherBuyer, int category, int item,
                            int winner, int otherFlow) { }

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("acceptance.db"));
        users = new UserDaoImpl(database);
        categories = new CategoryDaoImpl(database);
        items = new ItemDaoImpl(database);
        favorites = new FavoriteDaoImpl(database);
        flows = new FlowRecordDaoImpl(database);
        reviews = new ReviewDaoImpl(database);
        stats = new StatDaoImpl(database);
        logs = new OperationLogDaoImpl(database);
    }

    @Test
    void commitsDealReviewsCreditAndLogThenReopensConsistentProjections() throws Exception {
        Scenario scenario = createScenario();
        int logId;
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            settleAndReview(connection, scenario);
            logId = logs.insert(connection, new OperationLog(scenario.seller(), "ACCEPTANCE_DEAL",
                    "流转编号=" + scenario.winner()));
            StatCounts inside = stats.getOverview(connection, scope(scenario)).getCounts();
            assertEquals(1, inside.getFlowedItemCount());
            assertEquals(1, inside.getDealFlowCount());
            assertEquals(1, inside.getCancelledFlowCount());
            // 独立连接不能看见其他连接尚未提交的成交、信用与日志。
            assertEquals(ItemStatus.ON_SALE, items.findById(scenario.item()).orElseThrow().getStatus());
            assertEquals(FlowStatus.PENDING, flows.findById(scenario.winner()).orElseThrow().getStatus());
            assertEquals(100, users.findById(scenario.seller()).orElseThrow().getCreditScore());
            assertTrue(logs.findById(logId).isEmpty());
            connection.commit();
        }
        DBUtil reopened = DBUtil.forPath(directory.resolve("acceptance.db"));
        Item saved = new ItemDaoImpl(reopened).findById(scenario.item()).orElseThrow();
        assertEquals(ItemStatus.FLOWED, saved.getStatus());
        assertEquals("验收分类", saved.getCategoryName());
        assertEquals(103, saved.getOwnerCredit());
        assertEquals(103, new UserDaoImpl(reopened).findById(scenario.seller()).orElseThrow().getCreditScore());
        assertEquals(98, new UserDaoImpl(reopened).findById(scenario.buyer()).orElseThrow().getCreditScore());
        FlowRecord winner = new FlowRecordDaoImpl(reopened).findById(scenario.winner()).orElseThrow();
        FlowRecord other = new FlowRecordDaoImpl(reopened).findById(scenario.otherFlow()).orElseThrow();
        assertEquals(FlowStatus.DEAL, winner.getStatus());
        assertEquals(FlowStatus.CANCELLED, other.getStatus());
        assertNotNull(winner.getFinishedAt());
        assertNotNull(other.getFinishedAt());
        assertEquals(5.0, new ReviewDaoImpl(reopened).avgScoreOf(scenario.seller()));
        assertEquals(2.0, new ReviewDaoImpl(reopened).avgScoreOf(scenario.buyer()));
        Item favorite = new FavoriteDaoImpl(reopened).findByUser(scenario.buyer(), 1, 10)
                .getItems().get(0).getItem();
        assertEquals(ItemStatus.FLOWED, favorite.getStatus());
        assertEquals(103, favorite.getOwnerCredit());
        CategoryStat category = new StatDaoImpl(reopened).countByCategory(scope(scenario)).stream()
                .filter(row -> row.getCategoryId() != null && row.getCategoryId() == scenario.category())
                .findFirst().orElseThrow();
        assertEquals(1, category.getCounts().getItemCount());
        assertEquals(2, category.getCounts().getFlowCount());
        assertEquals("ACCEPTANCE_DEAL", new OperationLogDaoImpl(reopened).findById(logId).orElseThrow().getAction());
        assertHealthy(reopened);
    }

    @Test
    void finalLogFailureLetsCallerRollBackEveryEarlierModuleWrite() throws Exception {
        Scenario scenario = createScenario();
        int tentativeLog;
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            settleAndReview(connection, scenario);
            tentativeLog = logs.insert(connection, new OperationLog(scenario.seller(), "ACCEPTANCE_DEAL", ""));
            DataAccessException failure = assertThrows(DataAccessException.class,
                    () -> logs.insert(connection, new OperationLog(Integer.MAX_VALUE, "INVALID_ACTOR", "")));
            assertInstanceOf(java.sql.SQLException.class, failure.getCause());
            assertEquals(1, stats.countFlows(connection, FlowStatus.DEAL, scope(scenario)));
            assertTrue(logs.findById(connection, tentativeLog).isPresent());
            assertFalse(connection.getAutoCommit());
            connection.rollback();
        }
        assertEquals(ItemStatus.ON_SALE, items.findById(scenario.item()).orElseThrow().getStatus());
        for (int flowId : new int[]{scenario.winner(), scenario.otherFlow()}) {
            FlowRecord record = flows.findById(flowId).orElseThrow();
            assertEquals(FlowStatus.PENDING, record.getStatus());
            assertNull(record.getFinishedAt());
        }
        assertEquals(100, users.findById(scenario.seller()).orElseThrow().getCreditScore());
        assertEquals(100, users.findById(scenario.buyer()).orElseThrow().getCreditScore());
        assertEquals(0, reviews.findByUser(scenario.seller()).size());
        assertEquals(0, reviews.findByUser(scenario.buyer()).size());
        assertTrue(logs.findById(tentativeLog).isEmpty());
        StatCounts counts = stats.getOverview(scope(scenario)).getCounts();
        assertEquals(1, counts.getOnSaleItemCount());
        assertEquals(2, counts.getPendingFlowCount());
        assertEquals(0, counts.getDealFlowCount());
        assertEquals(0, counts.getCancelledFlowCount());
        assertEquals(ItemStatus.ON_SALE, favorites.findByUser(scenario.buyer(), 1, 10)
                .getItems().get(0).getItem().getStatus());
        assertHealthy(database);
    }

    @Test
    void searchesTenThousandItemsAndReportsDaoTimingWithoutMachineDependentThreshold() throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO item(owner_id,title,description,tags) VALUES(2,?,?,?)")) {
            connection.setAutoCommit(false);
            for (int index = 0; index < 10_000; index++) {
                statement.setString(1, "验收性能物品" + index);
                statement.setString(2, "独立临时库性能样本");
                statement.setString(3, "验收");
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        }
        ItemQuery query = ItemQuery.builder().keyword("验收性能物品").pageNo(1).pageSize(20).build();
        long maximum = 0;
        for (int index = 0; index < 5; index++) {
            long started = System.nanoTime();
            PageResult<Item> result = items.search(query);
            long elapsed = System.nanoTime() - started;
            maximum = Math.max(maximum, elapsed);
            assertEquals(10_000, result.getTotal());
            assertEquals(20, result.getItems().size());
            assertTrue(result.getItems().stream().allMatch(item -> item.getTitle().startsWith("验收性能物品")));
        }
        System.out.printf(Locale.ROOT, "ACCEPTANCE_DAO_SEARCH rows=10000 samples=5 max_ms=%.3f%n", maximum / 1_000_000.0);
    }

    private Scenario createScenario() {
        int seller = users.insert(new User("audit_seller", "fixture-hash", "fixture-salt", "验收卖家"));
        int buyer = users.insert(new User("audit_buyer", "fixture-hash", "fixture-salt", "验收买家"));
        int otherBuyer = users.insert(new User("audit_other", "fixture-hash", "fixture-salt", "其他买家"));
        int category = categories.insert(new Category("验收分类", null, 100));
        Item item = new Item();
        item.setOwnerId(seller);
        item.setCategoryId(category);
        item.setTitle("验收物品");
        item.setTradeType(TradeType.SALE);
        item.setPrice(25.5);
        int itemId = items.insert(item);
        favorites.insert(new Favorite(buyer, itemId));
        int winner = flows.insert(new FlowRecord(itemId, seller, buyer, TradeType.SALE, "买家意向"));
        int otherFlow = flows.insert(new FlowRecord(itemId, seller, otherBuyer, TradeType.SALE, "其他意向"));
        return new Scenario(seller, buyer, otherBuyer, category, itemId, winner, otherFlow);
    }

    private void settleAndReview(Connection connection, Scenario scenario) {
        assertEquals(1, flows.updateStatus(connection, scenario.winner(), FlowStatus.DEAL));
        assertEquals(1, items.updateStatus(connection, scenario.item(), ItemStatus.FLOWED));
        assertEquals(1, flows.cancelOtherPending(connection, scenario.item(), scenario.winner()));
        assertEquals(1, users.updateCreditScore(connection, scenario.seller(), 1));
        assertEquals(1, users.updateCreditScore(connection, scenario.buyer(), 1));
        reviews.insert(connection, new Review(scenario.winner(), scenario.buyer(), scenario.seller(), 5, "验收好评"));
        assertEquals(1, users.updateCreditScore(connection, scenario.seller(), 2));
        reviews.insert(connection, new Review(scenario.winner(), scenario.seller(), scenario.buyer(), 2, "验收低分评价"));
        assertEquals(1, users.updateCreditScore(connection, scenario.buyer(), -3));
    }

    private static StatQuery scope(Scenario scenario) {
        return StatQuery.builder().userId(scenario.seller()).build();
    }

    private static void assertHealthy(DBUtil database) throws Exception {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
                assertTrue(result.next());
                assertEquals("ok", result.getString(1));
                assertFalse(result.next());
            }
            try (ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(result.next());
            }
        }
    }
}
