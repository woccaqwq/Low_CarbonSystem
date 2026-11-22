package com.campus.recycle.service.impl;

import com.campus.recycle.dao.CategoryDao;
import com.campus.recycle.dao.FavoriteDao;
import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.Favorite;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.ItemForm;
import com.campus.recycle.entity.ItemQuery;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.exception.ValidationException;
import com.campus.recycle.service.ItemService;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;

/** 物品业务实现：所有权、分类、状态守卫与发布/下架日志事务。 */
public final class ItemServiceImpl implements ItemService {
    private final DBUtil database;
    private final ItemDao items;
    private final CategoryDao categories;
    private final FavoriteDao favorites;
    private final UserDao users;
    private final OperationLogDao logs;
    private final TxSupport tx;

    /** 使用默认连接工厂与 DAO 构造。 */
    public ItemServiceImpl() {
        this(DBUtil.getInstance(), DaoFactory.getItemDao(), DaoFactory.getCategoryDao(),
                DaoFactory.getFavoriteDao(), DaoFactory.getUserDao(), DaoFactory.getOperationLogDao());
    }

    /**
     * 注入依赖，便于测试使用独立数据库。
     */
    public ItemServiceImpl(DBUtil database, ItemDao items, CategoryDao categories, FavoriteDao favorites,
                           UserDao users, OperationLogDao logs) {
        this.database = Objects.requireNonNull(database, "database");
        this.items = Objects.requireNonNull(items, "items");
        this.categories = Objects.requireNonNull(categories, "categories");
        this.favorites = Objects.requireNonNull(favorites, "favorites");
        this.users = Objects.requireNonNull(users, "users");
        this.logs = Objects.requireNonNull(logs, "logs");
        this.tx = new TxSupport(database);
    }

    @Override
    public int publish(ItemForm form, SessionUser owner) throws BusinessException {
        Objects.requireNonNull(form, "form");
        requireOwner(owner);
        int itemId = tx.call(connection -> {
            requireActiveUser(connection, owner.getUserId());
            requireCategory(connection, form.getCategoryId());
            Item item = new Item();
            item.setOwnerId(owner.getUserId());
            applyForm(item, form);
            int newId = items.insert(connection, item);
            logs.insert(connection, new OperationLog(owner.getUserId(), "ITEM_PUBLISH", "发布物品编号=" + newId));
            return newId;
        });
        return itemId;
    }

    @Override
    public void update(int itemId, ItemForm form, SessionUser operator) throws BusinessException {
        Objects.requireNonNull(form, "form");
        requireOwner(operator);
        tx.run(connection -> {
            Item item = requireItem(connection, itemId);
            requireOwnerOf(item, operator.getUserId());
            requireMutable(item);
            requireCategory(connection, form.getCategoryId());
            applyForm(item, form);
            requireUpdated(items.update(connection, item));
            logs.insert(connection, new OperationLog(operator.getUserId(), "ITEM_UPDATE", "修改物品编号=" + itemId));
            return null;
        });
    }

    @Override
    public void offline(int itemId, SessionUser operator) throws BusinessException {
        requireOwner(operator);
        tx.run(connection -> {
            Item item = requireItem(connection, itemId);
            requireOwnerOf(item, operator.getUserId());
            if (item.getStatus() != ItemStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.FLOW_STATUS_ILLEGAL);
            }
            requireUpdated(items.updateStatus(connection, itemId, ItemStatus.OFF_SHELF));
            logs.insert(connection, new OperationLog(operator.getUserId(), "ITEM_OFFLINE", "下架物品编号=" + itemId));
            return null;
        });
    }

    @Override
    public Optional<Item> detail(int itemId) {
        if (itemId <= 0) { return Optional.empty(); }
        items.increaseViewCount(itemId);
        return items.findById(itemId);
    }

    @Override
    public PageResult<Item> search(ItemQuery query) {
        ItemQuery effective = query == null ? ItemQuery.builder().build() : query;
        return items.search(effective);
    }

    @Override
    public void favorite(int itemId, int userId) throws BusinessException {
        requirePositive(itemId, "itemId");
        requirePositive(userId, "userId");
        if (items.findById(itemId).isEmpty()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        favorites.insert(new Favorite(userId, itemId));
    }

    @Override
    public void unfavorite(int itemId, int userId) throws BusinessException {
        requirePositive(itemId, "itemId");
        requirePositive(userId, "userId");
        favorites.deleteByUserAndItem(userId, itemId);
    }

    private static void applyForm(Item item, ItemForm form) {
        item.setTitle(form.getTitle());
        item.setDescription(form.getDescription());
        item.setPrice(form.getPrice());
        item.setTradeType(form.getTradeType());
        item.setCategoryId(form.getCategoryId());
        item.setTags(form.getTags());
    }

    private Item requireItem(Connection connection, int itemId) {
        if (itemId <= 0) { throw new ValidationException("itemId", ErrorCode.VALIDATION_ERROR); }
        return items.findById(connection, itemId).orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
    }

    private void requireCategory(Connection connection, Integer categoryId) {
        if (categoryId != null && categories.findById(connection, categoryId).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private void requireActiveUser(Connection connection, int userId) {
        if (users.findById(connection, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_BY_ID);
        }
    }

    private static void requireOwner(SessionUser operator) {
        if (operator == null) { throw new BusinessException(ErrorCode.PERMISSION_DENIED); }
    }

    private static void requireOwnerOf(Item item, int userId) {
        if (item.getOwnerId() != userId) { throw new BusinessException(ErrorCode.PERMISSION_DENIED); }
    }

    private static void requireMutable(Item item) {
        if (item.getStatus() == ItemStatus.FLOWED) { throw new BusinessException(ErrorCode.FLOW_STATUS_ILLEGAL); }
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw new com.campus.recycle.exception.DataAccessException(ErrorCode.DB_ERROR.getMessage(),
                    new IllegalStateException("预期更新 1 行，实际 " + rows + " 行"));
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) { throw new ValidationException(field, ErrorCode.VALIDATION_ERROR); }
    }
}
