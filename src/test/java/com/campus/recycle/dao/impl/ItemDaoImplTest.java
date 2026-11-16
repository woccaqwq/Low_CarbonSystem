package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.Category;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.ItemQuery;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.strategy.CreditDescStrategy;
import com.campus.recycle.strategy.PriceAscStrategy;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemDaoImplTest {
    @TempDir Path directory;
    private DBUtil database;
    private ItemDao items;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("items.db"));
        items = new ItemDaoImpl(database);
    }

    @Test
    void mapsCompleteSeedDetailsAndJoinedFields() {
        Item item = items.findById(1).orElseThrow();
        assertEquals(2, item.getOwnerId());
        assertEquals(11, item.getCategoryId());
        assertTrue(item.getTitle().contains("软件构造"));
        assertTrue(item.getDescription().contains("内页干净"));
        assertEquals(25.0, item.getPrice());
        assertEquals(TradeType.SALE, item.getTradeType());
        assertEquals(ItemStatus.ON_SALE, item.getStatus());
        assertEquals("教材,软件工程,九成新", item.getTags());
        assertEquals(46, item.getViewCount());
        assertNotNull(item.getCreatedAt());
        assertNotNull(item.getUpdatedAt());
        assertEquals("徐同学", item.getOwnerNickname());
        assertEquals(112, item.getOwnerCredit());
        assertEquals("专业课教材", item.getCategoryName());
        assertTrue(items.findById(9999).isEmpty());
    }

    @Test
    void insertsAndPersistsAllEditableDataWithoutMutatingInput() {
        Item input = item("新物品", 2, 11);
        input.setDescription("详细描述");
        input.setPrice(18.5);
        input.setTradeType(TradeType.SALE);
        input.setTags("二手,实用");
        int id = items.insert(input);
        assertTrue(id > 5);
        assertEquals(0, input.getItemId());
        Item saved = new ItemDaoImpl(DBUtil.forPath(directory.resolve("items.db")))
                .findById(id).orElseThrow();
        assertEquals("新物品", saved.getTitle());
        assertEquals("详细描述", saved.getDescription());
        assertEquals(18.5, saved.getPrice());
        assertEquals(TradeType.SALE, saved.getTradeType());
        assertEquals("二手,实用", saved.getTags());
        assertEquals(ItemStatus.ON_SALE, saved.getStatus());
        assertEquals(0, saved.getViewCount());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void preservesExplicitTimesAtSecondPrecision() {
        Item input = item("有时间的物品", 2, null);
        input.setCreatedAt(LocalDateTime.of(2026, 10, 1, 12, 30, 45, 123));
        input.setUpdatedAt(LocalDateTime.of(2026, 10, 2, 13, 31, 46, 456));
        Item saved = items.findById(items.insert(input)).orElseThrow();
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 30, 45), saved.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 10, 2, 13, 31, 46), saved.getUpdatedAt());
        assertNull(saved.getCategoryId());
        assertNull(saved.getCategoryName());
    }

    @Test
    void updatesEditableFieldsWithoutOverwritingOwnershipStateOrCounters() {
        Item input = items.findById(1).orElseThrow();
        LocalDateTime created = input.getCreatedAt();
        LocalDateTime updated = input.getUpdatedAt();
        input.setTitle("更新后的教材");
        input.setDescription("更新描述");
        input.setPrice(20.0);
        input.setTradeType(TradeType.EXCHANGE);
        input.setCategoryId(12);
        input.setTags("新标签");
        input.setOwnerId(3);
        input.setStatus(ItemStatus.FLOWED);
        input.setViewCount(999);
        input.setCreatedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        assertEquals(1, items.update(input));
        Item saved = items.findById(1).orElseThrow();
        assertEquals("更新后的教材", saved.getTitle());
        assertEquals("更新描述", saved.getDescription());
        assertEquals(20.0, saved.getPrice());
        assertEquals(TradeType.EXCHANGE, saved.getTradeType());
        assertEquals(12, saved.getCategoryId());
        assertEquals("新标签", saved.getTags());
        assertEquals(2, saved.getOwnerId());
        assertEquals(ItemStatus.ON_SALE, saved.getStatus());
        assertEquals(46, saved.getViewCount());
        assertEquals(created, saved.getCreatedAt());
        assertNotEquals(updated, saved.getUpdatedAt());
    }

    @Test
    void changesStatusAndIncrementsViewsWithDedicatedOperations() {
        assertTrue(items.existsOnSale(1));
        assertFalse(items.existsOnSale(4));
        assertFalse(items.existsOnSale(9999));
        assertEquals(1, items.increaseViewCount(1));
        assertEquals(47, items.findById(1).orElseThrow().getViewCount());
        assertEquals(1, items.updateStatus(1, ItemStatus.OFF_SHELF));
        assertFalse(items.existsOnSale(1));
        assertEquals(ItemStatus.OFF_SHELF, items.findById(1).orElseThrow().getStatus());
        assertEquals(0, items.updateStatus(9999, ItemStatus.FLOWED));
        assertEquals(0, items.increaseViewCount(9999));
        Item missing = item("不存在", 2, null);
        missing.setItemId(9999);
        assertEquals(0, items.update(missing));
    }

    @Test
    void defaultsToOnSaleAndAllowsExplicitAllStates() {
        assertEquals(List.of(3, 2, 1), ids(items.search(ItemQuery.builder().build())));
        assertEquals(5, items.search(ItemQuery.builder().status(null).build()).getTotal());
        assertEquals(List.of(5), ids(items.search(ItemQuery.builder().status(ItemStatus.OFF_SHELF).build())));
        assertEquals(List.of(4), ids(items.search(ItemQuery.builder().status(ItemStatus.FLOWED).build())));
    }

    @Test
    void combinesKeywordCategoryPriceOwnerTradeTypeAndStatus() {
        ItemQuery query = ItemQuery.builder().keyword("教材").categoryId(1).ownerId(2)
                .minPrice(20.0).maxPrice(30.0).tradeType(TradeType.SALE).build();
        assertEquals(List.of(1), ids(items.search(query)));
        assertTrue(items.search(ItemQuery.builder().minPrice(26.0).maxPrice(44.0).build()).isEmpty());
        assertEquals(List.of(1), ids(items.search(ItemQuery.builder().minPrice(25.0).maxPrice(25.0).build())));
        assertEquals(List.of(3), ids(items.search(ItemQuery.builder().tradeType(TradeType.EXCHANGE).build())));
    }

    @Test
    void searchesDescriptionAndTagsAsWellAsTitle() {
        assertEquals(List.of(2), ids(items.search(ItemQuery.builder().keyword("原装正品").build())));
        assertEquals(List.of(1), ids(items.search(ItemQuery.builder().keyword("软件工程").build())));
        assertEquals(List.of(3), ids(items.search(ItemQuery.builder().keyword("羽毛球拍").build())));
    }

    @Test
    void selectsCategoryAndDescendantsAndMissingCategoryReturnsEmpty() {
        assertEquals(List.of(1, 5), ids(items.search(ItemQuery.builder().categoryId(1).status(null).build())));
        assertEquals(List.of(5), ids(items.search(ItemQuery.builder().categoryId(13).status(null).build())));
        assertTrue(items.search(ItemQuery.builder().categoryId(9999).build()).isEmpty());
    }

    @Test
    void appliesTimePriceAndCreditSortsAndReturnsFreshJoinedCredit() {
        assertEquals(List.of(3, 1, 2), ids(items.search(ItemQuery.builder().sort(new PriceAscStrategy()).build())));
        assertEquals(List.of(2, 1, 3), ids(items.search(ItemQuery.builder().sort(new CreditDescStrategy()).build())));
        new UserDaoImpl(database).updateCreditScore(3, 46);
        assertEquals(List.of(3, 2, 1), ids(items.search(ItemQuery.builder().sort(new CreditDescStrategy()).build())));
        assertEquals(150, items.findById(3).orElseThrow().getOwnerCredit());
    }

    @Test
    void paginatesTiedDatesDeterministicallyAndHandlesExtremeOffset() throws Exception {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (int index = 0; index < 12; index++) {
                Item input = item("分页测试" + index, 2, null);
                input.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                items.insert(connection, input);
            }
            connection.commit();
        }
        PageResult<Item> first = items.search(ItemQuery.builder().keyword("分页测试").pageSize(10).build());
        PageResult<Item> second = items.search(ItemQuery.builder().keyword("分页测试").pageSize(10).pageNo(2).build());
        assertEquals(12, first.getTotal());
        assertEquals(10, first.getItems().size());
        assertEquals(2, first.getTotalPages());
        assertEquals(List.of(7, 6), ids(second));
        assertEquals(17, first.getItems().get(0).getItemId());
        PageResult<Item> far = items.search(ItemQuery.builder().keyword("分页测试")
                .pageNo(Integer.MAX_VALUE).pageSize(100).build());
        assertEquals(12, far.getTotal());
        assertTrue(far.isEmpty());
    }

    @Test
    void treatsWildcardsAndQuotesAsLiteralSearchInput() {
        Item input = item("100% 实用'", 2, null);
        input.setDescription("a_b");
        input.setTags("path\\tag");
        int id = items.insert(input);
        for (String keyword : List.of("%", "_", "\\", "'")) {
            assertEquals(List.of(id), ids(items.search(ItemQuery.builder().keyword(keyword).build())));
        }
        assertTrue(items.search(ItemQuery.builder().keyword("' OR 1=1 --").build()).isEmpty());
        assertEquals(6, items.search(ItemQuery.builder().status(null).build()).getTotal());
    }

    @Test
    void retainsItemsAfterTheirCategoryIsDeleted() {
        assertEquals(1, new CategoryDaoImpl(database).deleteById(11));
        Item saved = items.findById(1).orElseThrow();
        assertNull(saved.getCategoryId());
        assertNull(saved.getCategoryName());
        assertEquals(3, items.search(ItemQuery.builder().build()).getTotal());
    }

    @Test
    void wrapsForeignKeyFailuresAndRejectsIncompleteInput() {
        assertThrows(DataAccessException.class, () -> items.insert(item("未知用户", 9999, null)));
        assertThrows(DataAccessException.class, () -> items.insert(item("未知分类", 2, 9999)));
        assertThrows(IllegalArgumentException.class, () -> items.insert(new Item()));
        assertThrows(IllegalArgumentException.class, () -> items.updateStatus(1, null));
        assertThrows(IllegalArgumentException.class, () -> items.search(null));
        assertEquals(5, items.search(ItemQuery.builder().status(null).build()).getTotal());
    }

    @Test
    void rollsBackCategoryAndItemWritesOnSharedConnection() throws Exception {
        CategoryDaoImpl categories = new CategoryDaoImpl(database);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            int category = categories.insert(connection, new Category("临时类别", null, 0));
            int itemId = items.insert(connection, item("临时物品", 2, category));
            Item edited = items.findById(connection, 1).orElseThrow();
            edited.setTitle("临时修改");
            assertEquals(1, items.update(connection, edited));
            assertEquals(1, items.updateStatus(connection, 1, ItemStatus.FLOWED));
            assertEquals(1, items.increaseViewCount(connection, 1));
            assertFalse(items.existsOnSale(connection, 1));
            assertTrue(items.findById(itemId).isEmpty());
            assertEquals(6, items.search(connection, ItemQuery.builder().status(null).build()).getTotal());
            assertFalse(connection.getAutoCommit());
            assertFalse(connection.isClosed());
            connection.rollback();
        }
        assertEquals(14, categories.findAll().size());
        assertEquals(5, items.search(ItemQuery.builder().status(null).build()).getTotal());
        Item original = items.findById(1).orElseThrow();
        assertTrue(original.getTitle().contains("软件构造"));
        assertEquals(ItemStatus.ON_SALE, original.getStatus());
        assertEquals(46, original.getViewCount());
    }

    @Test
    void reportsClosedExternalConnectionsWithoutOpeningAnotherOne() throws Exception {
        Connection connection = database.getConnection();
        connection.close();
        assertThrows(DataAccessException.class, () -> items.findById(connection, 1));
        assertThrows(DataAccessException.class, () -> new CategoryDaoImpl(database).findAll(connection));
    }

    @Test
    void preservesAndIncrementsViewCountsAboveIntegerRange() {
        Item input = item("高浏览量物品", 2, null);
        input.setViewCount(2147483657L);
        int id = items.insert(input);
        assertEquals(2147483657L, items.findById(id).orElseThrow().getViewCount());
        assertEquals(1, items.increaseViewCount(id));
        assertEquals(2147483658L, items.findById(id).orElseThrow().getViewCount());
    }

    @Test
    void keepsEarlierTransactionWritesAfterConstraintFailureUntilCallerRollsBack() throws Exception {
        CategoryDaoImpl categories = new CategoryDaoImpl(database);
        int categoryId;
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            categoryId = categories.insert(connection, new Category("错误前写入", null, 0));
            assertThrows(DataAccessException.class,
                    () -> categories.insert(connection, new Category("教材书籍", null, 0)));
            assertThrows(DataAccessException.class,
                    () -> items.insert(connection, item("非法外键", 9999, categoryId)));
            assertTrue(categories.findById(connection, categoryId).isPresent());
            assertTrue(categories.findById(categoryId).isEmpty());
            assertFalse(connection.getAutoCommit());
            connection.rollback();
        }
        assertTrue(categories.findById(categoryId).isEmpty());
        assertEquals(14, categories.findAll().size());
    }

    @Test
    void terminatesDescendantSearchWhenStoredCategoryGraphContainsCycle() throws Exception {
        try (Connection connection = database.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE category SET parent_id=11 WHERE category_id=1");
        }
        assertEquals(List.of(1, 5), ids(items.search(ItemQuery.builder().categoryId(1).status(null).build())));
    }

    @Test
    void factoryExposesItemAndCategoryInterfaces() {
        assertSame(DaoFactory.getItemDao(), DaoFactory.getItemDao());
        assertSame(DaoFactory.getCategoryDao(), DaoFactory.getCategoryDao());
    }

    private static Item item(String title, int ownerId, Integer categoryId) {
        Item item = new Item();
        item.setTitle(title);
        item.setOwnerId(ownerId);
        item.setCategoryId(categoryId);
        return item;
    }

    private static List<Integer> ids(PageResult<Item> result) {
        return result.getItems().stream().map(Item::getItemId).toList();
    }
}
