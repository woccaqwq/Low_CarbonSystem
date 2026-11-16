package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.FavoriteDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.Favorite;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.User;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FavoriteDaoImplTest {
    @TempDir Path directory;
    private DBUtil database;
    private FavoriteDao favorites;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("favorites.db"));
        favorites = new FavoriteDaoImpl(database);
    }

    @Test
    void readsSeedFavoritesWithFullCurrentItemProjection() {
        PageResult<Favorite> page = favorites.findByUser(3, 1, 10);
        assertEquals(2, page.getTotal());
        assertEquals(List.of(2, 1), itemIds(page));
        Favorite favorite = page.getItems().get(0);
        assertEquals(2, favorite.getFavoriteId());
        assertEquals(3, favorite.getUserId());
        assertNotNull(favorite.getCreatedAt());
        Item item = favorite.getItem();
        assertEquals(2, item.getItemId());
        assertTrue(item.getDescription().contains("原装正品"));
        assertEquals("徐同学", item.getOwnerNickname());
        assertEquals(112, item.getOwnerCredit());
        assertEquals(ItemStatus.ON_SALE, item.getStatus());
        assertEquals("数据线/充电器", item.getCategoryName());
        assertEquals(31, item.getViewCount());
        assertEquals(1, favorites.findByUser(2, 1, 10).getTotal());
        assertTrue(favorites.findByUser(1, 1, 10).isEmpty());
    }

    @Test
    void insertsAndPersistsWithoutMutatingInput() {
        Favorite input = new Favorite(1, 1);
        int id = favorites.insert(input);
        assertTrue(id > 3);
        assertEquals(0, input.getFavoriteId());
        assertNull(input.getCreatedAt());
        Favorite saved = new FavoriteDaoImpl(DBUtil.forPath(directory.resolve("favorites.db")))
                .findByUser(1, 1, 10).getItems().get(0);
        assertEquals(id, saved.getFavoriteId());
        assertEquals(1, saved.getItemId());
        assertNotNull(saved.getCreatedAt());
        assertTrue(favorites.exists(1, 1));
    }

    @Test
    void duplicateInsertReturnsOriginalIdAndPreservesOriginalTime() {
        Favorite existing = favorites.findByUser(3, 1, 10).getItems().get(1);
        Favorite repeated = new Favorite(3, 1);
        repeated.setCreatedAt(LocalDateTime.of(2030, 1, 1, 0, 0));
        assertEquals(existing.getFavoriteId(), favorites.insert(repeated));
        PageResult<Favorite> after = favorites.findByUser(3, 1, 10);
        assertEquals(2, after.getTotal());
        assertEquals(List.of(2, 1), itemIds(after));
        assertEquals(existing.getCreatedAt(), after.getItems().get(1).getCreatedAt());
    }

    @Test
    void cancellationIsIdempotentAndScopedToBothUserAndItem() {
        favorites.insert(new Favorite(1, 1));
        assertEquals(1, favorites.deleteByUserAndItem(3, 1));
        assertEquals(0, favorites.deleteByUserAndItem(3, 1));
        assertFalse(favorites.exists(3, 1));
        assertTrue(favorites.exists(1, 1));
        assertTrue(favorites.exists(3, 2));
        assertFalse(favorites.exists(3, 9999));
        assertEquals(0, favorites.deleteByUserAndItem(9999, 1));
    }

    @Test
    void showsOfflineAndFlowedItemsWithFreshJoinedMetadata() {
        ItemDaoImpl items = new ItemDaoImpl(database);
        items.updateStatus(1, ItemStatus.OFF_SHELF);
        items.updateStatus(2, ItemStatus.FLOWED);
        new UserDaoImpl(database).updateCreditScore(2, 2);
        PageResult<Favorite> page = favorites.findByUser(3, 1, 10);
        assertEquals(2, page.getTotal());
        assertEquals(ItemStatus.FLOWED, page.getItems().get(0).getItem().getStatus());
        assertEquals(ItemStatus.OFF_SHELF, page.getItems().get(1).getItem().getStatus());
        assertEquals(114, page.getItems().get(0).getItem().getOwnerCredit());
    }

    @Test
    void keepsFavoriteVisibleWhenCategoryIsDeleted() {
        new CategoryDaoImpl(database).deleteById(11);
        Favorite saved = favorites.findByUser(3, 1, 10).getItems().get(1);
        assertEquals(1, saved.getItemId());
        assertNull(saved.getItem().getCategoryId());
        assertNull(saved.getItem().getCategoryName());
        assertEquals(2, favorites.findByUser(3, 1, 10).getTotal());
    }

    @Test
    void storesExplicitTimeToSecondPrecisionAndUsesStablePagination() throws Exception {
        ItemDaoImpl items = new ItemDaoImpl(database);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (int index = 0; index < 12; index++) {
                Item item = new Item();
                item.setOwnerId(2);
                item.setTitle("收藏分页" + index);
                item.setCreatedAt(LocalDateTime.of(2025, 1, 2, 3, 4, 5));
                item.setUpdatedAt(LocalDateTime.of(2025, 2, 3, 4, 5, 6));
                int itemId = items.insert(connection, item);
                Favorite favorite = new Favorite(1, itemId);
                favorite.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123));
                favorites.insert(connection, favorite);
            }
            connection.commit();
        }
        PageResult<Favorite> first = favorites.findByUser(1, 1, 10);
        PageResult<Favorite> second = favorites.findByUser(1, 2, 10);
        assertEquals(12, first.getTotal());
        assertEquals(2, first.getTotalPages());
        assertEquals(10, first.getItems().size());
        assertEquals(17, first.getItems().get(0).getItemId());
        assertEquals(List.of(7, 6), itemIds(second));
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5), first.getItems().get(0).getCreatedAt());
        assertEquals(LocalDateTime.of(2025, 1, 2, 3, 4, 5),
                first.getItems().get(0).getItem().getCreatedAt());
        assertEquals(LocalDateTime.of(2025, 2, 3, 4, 5, 6),
                first.getItems().get(0).getItem().getUpdatedAt());
        PageResult<Favorite> far = favorites.findByUser(1, Integer.MAX_VALUE, 100);
        assertEquals(12, far.getTotal());
        assertTrue(far.isEmpty());
    }

    @Test
    void rejectsForeignKeyFailuresRatherThanIgnoringThem() {
        DataAccessException badUser = assertThrows(DataAccessException.class,
                () -> favorites.insert(new Favorite(9999, 1)));
        assertInstanceOf(java.sql.SQLException.class, badUser.getCause());
        assertThrows(DataAccessException.class, () -> favorites.insert(new Favorite(3, 9999)));
        assertEquals(2, favorites.findByUser(3, 1, 10).getTotal());
    }

    @Test
    void letsCallerRollBackAddAndRemoveAfterFailure() throws Exception {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            int id = favorites.insert(connection, new Favorite(3, 3));
            assertTrue(id > 3);
            assertEquals(1, favorites.deleteByUserAndItem(connection, 3, 1));
            assertTrue(favorites.exists(connection, 3, 3));
            assertFalse(favorites.exists(3, 3));
            assertThrows(DataAccessException.class, () -> favorites.insert(connection, new Favorite(3, 9999)));
            assertTrue(favorites.exists(connection, 3, 3));
            assertEquals(List.of(3, 2), itemIds(favorites.findByUser(connection, 3, 1, 10)));
            assertFalse(connection.isClosed());
            assertFalse(connection.getAutoCommit());
            connection.rollback();
        }
        assertFalse(favorites.exists(3, 3));
        assertTrue(favorites.exists(3, 1));
    }

    @Test
    void commitsOnlyWhenCallerRequestsIt() throws Exception {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            favorites.insert(connection, new Favorite(1, 2));
            assertFalse(favorites.exists(1, 2));
            connection.commit();
        }
        assertTrue(favorites.exists(1, 2));
    }

    @Test
    void cascadesWhenAnUnreferencedItemIsDeleted() throws Exception {
        Item item = new Item();
        item.setTitle("删除测试物品");
        item.setOwnerId(2);
        int id = new ItemDaoImpl(database).insert(item);
        favorites.insert(new Favorite(1, id));
        favorites.insert(new Favorite(3, id));
        try (Connection connection = database.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement("DELETE FROM item WHERE item_id=?")) {
            statement.setInt(1, id);
            assertEquals(1, statement.executeUpdate());
        }
        assertFalse(favorites.exists(1, id));
        assertFalse(favorites.exists(3, id));
    }

    @Test
    void cascadesWhenAUserWithoutOtherReferencesIsDeleted() throws Exception {
        int userId = new UserDaoImpl(database).insert(new User("favorite_user", "hash", "salt", "收藏用户"));
        favorites.insert(new Favorite(userId, 1));
        try (Connection connection = database.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement("DELETE FROM sys_user WHERE user_id=?")) {
            statement.setInt(1, userId);
            assertEquals(1, statement.executeUpdate());
        }
        assertFalse(favorites.exists(userId, 1));
        assertTrue(favorites.findByUser(userId, 1, 10).isEmpty());
    }

    @Test
    void rejectsInvalidParametersAndIncompleteEntities() {
        assertThrows(IllegalArgumentException.class, () -> new Favorite(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Favorite(1, -1));
        assertThrows(IllegalArgumentException.class, () -> favorites.insert(new Favorite()));
        assertThrows(IllegalArgumentException.class, () -> favorites.insert(null));
        assertThrows(IllegalArgumentException.class, () -> favorites.exists(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> favorites.deleteByUserAndItem(1, 0));
        assertThrows(IllegalArgumentException.class, () -> favorites.findByUser(1, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> favorites.findByUser(1, 1, 9));
        assertThrows(IllegalArgumentException.class, () -> favorites.findByUser(1, 1, 101));
    }

    @Test
    void reportsClosedExternalConnection() throws Exception {
        Connection connection = database.getConnection();
        connection.close();
        assertThrows(DataAccessException.class, () -> favorites.findByUser(connection, 3, 1, 10));
        assertThrows(DataAccessException.class, () -> favorites.exists(connection, 3, 1));
        assertThrows(DataAccessException.class, () -> favorites.insert(connection, new Favorite(3, 3)));
        assertThrows(DataAccessException.class, () -> favorites.deleteByUserAndItem(connection, 3, 1));
    }

    @Test
    void factoryExposesStableInterfaceInstance() {
        assertInstanceOf(FavoriteDao.class, DaoFactory.getFavoriteDao());
        assertSame(DaoFactory.getFavoriteDao(), DaoFactory.getFavoriteDao());
    }

    private static List<Integer> itemIds(PageResult<Favorite> page) {
        return page.getItems().stream().map(Favorite::getItemId).toList();
    }
}
