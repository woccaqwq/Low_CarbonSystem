package com.campus.recycle.service;

import com.campus.recycle.dao.impl.*;
import com.campus.recycle.entity.*;
import com.campus.recycle.dao.CategoryDao;
import com.campus.recycle.dao.FavoriteDao;
import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.impl.CategoryDaoImpl;
import com.campus.recycle.dao.impl.FavoriteDaoImpl;
import com.campus.recycle.dao.impl.ItemDaoImpl;
import com.campus.recycle.dao.impl.OperationLogDaoImpl;
import com.campus.recycle.dao.impl.UserDaoImpl;
import com.campus.recycle.entity.Category;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.ItemForm;
import com.campus.recycle.entity.ItemQuery;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.Role;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.service.impl.ItemServiceImpl;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 物品业务：发布、所有权校验、状态守卫、检索与收藏。 */
class ItemServiceTest {
    @TempDir Path directory;
    private DBUtil database;
    private UserDao users;
    private CategoryDao categories;
    private ItemDao items;
    private FavoriteDao favorites;
    private OperationLogDao logs;
    private ItemService service;
    private SessionUser owner;
    private SessionUser other;
    private int categoryId;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("item-service.db"));
        ServiceTestSupport.clearBusinessData(database);
        users = new UserDaoImpl(database);
        categories = new CategoryDaoImpl(database);
        items = new ItemDaoImpl(database);
        favorites = new FavoriteDaoImpl(database);
        logs = new OperationLogDaoImpl(database);
        service = new ItemServiceImpl(database, items, categories, favorites, users, logs);
        int ownerId = users.insert(new User("owner_user", "hash", "salt", "发布者"));
        int otherId = users.insert(new User("other_user", "hash", "salt", "其他用户"));
        owner = new SessionUser(ownerId, "owner_user", "发布者", Role.STUDENT, 100);
        other = new SessionUser(otherId, "other_user", "其他用户", Role.STUDENT, 100);
        categoryId = categories.insert(new Category("教材", null, 1));
    }

    private ItemForm form() {
        return ItemForm.builder().title("软件构造教材").description("九成新")
                .categoryId(categoryId).price(25).tradeType(TradeType.SALE).tags("教材,软件").build();
    }

    @Test
    void publishesItemAsOnSaleAndWritesLog() {
        int itemId = service.publish(form(), owner);
        Item saved = items.findById(itemId).orElseThrow();
        assertEquals(ItemStatus.ON_SALE, saved.getStatus());
        assertEquals(owner.getUserId(), saved.getOwnerId());
        assertEquals(25, saved.getPrice());
        assertTrue(logs.findRecent(10).stream().anyMatch(log -> "ITEM_PUBLISH".equals(log.getAction())));
        assertEquals(1, service.search(ItemQuery.builder().keyword("软件构造").build()).getTotal());
    }

    @Test
    void rejectsPublishWithUnknownCategory() {
        ItemForm invalid = ItemForm.builder().title("物品").categoryId(99999)
                .price(10).tradeType(TradeType.SALE).build();
        BusinessException failure = assertThrows(BusinessException.class, () -> service.publish(invalid, owner));
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, failure.getCode());
    }

    @Test
    void onlyOwnerCanUpdateOrOffline() {
        int itemId = service.publish(form(), owner);
        assertThrows(BusinessException.class, () -> service.offline(itemId, other));
        assertThrows(BusinessException.class,
                () -> service.update(itemId, form(), other));
        service.offline(itemId, owner);
        assertEquals(ItemStatus.OFF_SHELF, items.findById(itemId).orElseThrow().getStatus());
        assertEquals(0, service.search(ItemQuery.builder().build()).getTotal());
    }

    @Test
    void favoriteIsIdempotentAndCancelable() {
        int itemId = service.publish(form(), owner);
        service.favorite(itemId, other.getUserId());
        service.favorite(itemId, other.getUserId());
        assertTrue(favorites.exists(other.getUserId(), itemId));
        service.unfavorite(itemId, other.getUserId());
        assertFalse(favorites.exists(other.getUserId(), itemId));
    }

    @Test
    void rejectsFavoriteOfMissingItem() {
        BusinessException failure = assertThrows(BusinessException.class, () -> service.favorite(99999, 1));
        assertEquals(ErrorCode.ITEM_NOT_FOUND, failure.getCode());
    }
}
