package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.ItemQuery;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 物品 JDBC 实现；使用完整 JOIN 读取描述、更新时间及展示字段。 */
public final class ItemDaoImpl extends BaseDao<Item> implements ItemDao {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SELECT = "SELECT i.*,u.nickname AS owner_nickname,u.credit_score AS owner_credit,"
            + "c.name AS category_name FROM item i LEFT JOIN sys_user u ON u.user_id=i.owner_id "
            + "LEFT JOIN category c ON c.category_id=i.category_id";
    private static final String INSERT = "INSERT INTO item "
            + "(owner_id,category_id,title,description,price,trade_type,status,tags,view_count,created_at,updated_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,COALESCE(?,datetime('now','localtime')),"
            + "COALESCE(?,datetime('now','localtime')))";

    /** 使用默认数据库。 */
    public ItemDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 注入数据库工厂 */
    public ItemDaoImpl(DBUtil database) { super(database); }

    @Override
    public Optional<Item> findById(int itemId) {
        return withConnection(connection -> findById(connection, itemId));
    }
    @Override
    public int insert(Item item) {
        return withConnection(connection -> insert(connection, item));
    }
    @Override
    public int update(Item item) {
        return withConnection(connection -> update(connection, item));
    }
    @Override
    public int updateStatus(int itemId, ItemStatus status) {
        return withConnection(connection -> updateStatus(connection, itemId, status));
    }
    @Override
    public int increaseViewCount(int itemId) {
        return withConnection(connection -> increaseViewCount(connection, itemId));
    }
    @Override
    public boolean existsOnSale(int itemId) {
        return withConnection(connection -> existsOnSale(connection, itemId));
    }
    @Override
    public PageResult<Item> search(ItemQuery query) {
        return withConnection(connection -> search(connection, query));
    }
    @Override
    public Optional<Item> findById(Connection connection, int itemId) {
        return onConnection(connection, current -> query(current, SELECT + " WHERE i.item_id=?", itemId)
                .stream().findFirst());
    }
    @Override
    public int insert(Connection connection, Item item) {
        validate(item);
        if (item.getOwnerId() <= 0) { throw new IllegalArgumentException("物品缺少发布人"); }
        return onConnection(connection, current -> insertAndReturnId(current, INSERT,
                item.getOwnerId(), item.getCategoryId(), item.getTitle(), item.getDescription(), item.getPrice(),
                item.getTradeType().name(), item.getStatus().name(), item.getTags(), item.getViewCount(),
                format(item.getCreatedAt()), format(item.getUpdatedAt())));
    }
    @Override
    public int update(Connection connection, Item item) {
        validate(item);
        return onConnection(connection, current -> update(current,
                "UPDATE item SET category_id=?,title=?,description=?,price=?,trade_type=?,tags=? WHERE item_id=?",
                item.getCategoryId(), item.getTitle(), item.getDescription(), item.getPrice(),
                item.getTradeType().name(), item.getTags(), item.getItemId()));
    }
    @Override
    public int updateStatus(Connection connection, int itemId, ItemStatus status) {
        if (status == null) { throw new IllegalArgumentException("状态不能为空"); }
        return onConnection(connection, current -> update(current, "UPDATE item SET status=? WHERE item_id=?",
                status.name(), itemId));
    }
    @Override
    public int increaseViewCount(Connection connection, int itemId) {
        return onConnection(connection, current -> update(current,
                "UPDATE item SET view_count=view_count+1 WHERE item_id=?", itemId));
    }
    @Override
    public boolean existsOnSale(Connection connection, int itemId) {
        return onConnection(connection, current -> count(current,
                "SELECT count(*) FROM item WHERE item_id=? AND status=?", itemId, ItemStatus.ON_SALE.name()) != 0);
    }
    @Override
    public PageResult<Item> search(Connection connection, ItemQuery query) {
        if (query == null) { throw new IllegalArgumentException("检索条件不能为空"); }
        ItemSearchSql filter = ItemSearchSql.from(query);
        return onConnection(connection, current -> {
            int total = count(current, "SELECT count(*) FROM item i" + filter.where(), filter.parameters().toArray());
            List<Object> parameters = new ArrayList<>(filter.parameters());
            parameters.add(query.getPageSize());
            parameters.add(((long) query.getPageNo() - 1) * query.getPageSize());
            List<Item> results = query(current, SELECT + filter.where() + " ORDER BY " + query.getOrderBy()
                    + ",i.item_id DESC LIMIT ? OFFSET ?", parameters.toArray());
            return new PageResult<>(results, total, query.getPageNo(), query.getPageSize());
        });
    }
    @Override
    protected Item mapRow(ResultSet result) throws SQLException {
        return ItemRowMapper.read(result);
    }

    private static void validate(Item item) {
        if (item == null || item.getTitle() == null || item.getTitle().isBlank()) {
            throw new IllegalArgumentException("物品缺少有效标题");
        }
    }
    private static String format(LocalDateTime time) {
        return time == null ? null : DATE_FORMAT.format(time);
    }
}
