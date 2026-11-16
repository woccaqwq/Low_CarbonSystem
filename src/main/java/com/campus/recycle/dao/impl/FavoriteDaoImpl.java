package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.FavoriteDao;
import com.campus.recycle.entity.Favorite;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 收藏 JDBC 实现，重复添加不会重置收藏时间或吞掉外键错误。 */
public final class FavoriteDaoImpl extends BaseDao<Favorite> implements FavoriteDao {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SELECT = "SELECT f.favorite_id,f.user_id,f.created_at AS favorite_created_at,"
            + "i.*,u.nickname AS owner_nickname,u.credit_score AS owner_credit,c.name AS category_name "
            + "FROM favorite f JOIN item i ON i.item_id=f.item_id "
            + "LEFT JOIN sys_user u ON u.user_id=i.owner_id LEFT JOIN category c ON c.category_id=i.category_id";
    private static final String INSERT = "INSERT INTO favorite(user_id,item_id,created_at) "
            + "VALUES (?,?,COALESCE(?,datetime('now','localtime'))) "
            + "ON CONFLICT(user_id,item_id) DO UPDATE SET item_id=excluded.item_id RETURNING favorite_id";

    /** 使用默认数据库。 */
    public FavoriteDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 独立数据库工厂 */
    public FavoriteDaoImpl(DBUtil database) { super(database); }

    @Override
    public int insert(Favorite favorite) {
        return withConnection(connection -> insert(connection, favorite));
    }
    @Override
    public int deleteByUserAndItem(int userId, int itemId) {
        return withConnection(connection -> deleteByUserAndItem(connection, userId, itemId));
    }
    @Override
    public boolean exists(int userId, int itemId) {
        return withConnection(connection -> exists(connection, userId, itemId));
    }
    @Override
    public PageResult<Favorite> findByUser(int userId, int pageNo, int pageSize) {
        return withConnection(connection -> findByUser(connection, userId, pageNo, pageSize));
    }
    @Override
    public int insert(Connection connection, Favorite favorite) {
        if (favorite == null) { throw new IllegalArgumentException("收藏不能为空"); }
        validateIds(favorite.getUserId(), favorite.getItemId());
        return onConnection(connection, current -> {
            try (PreparedStatement statement = current.prepareStatement(INSERT)) {
                statement.setInt(1, favorite.getUserId());
                statement.setInt(2, favorite.getItemId());
                LocalDateTime time = favorite.getCreatedAt();
                statement.setString(3, time == null ? null : DATE_FORMAT.format(time));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) { throw new SQLException("收藏写入后未返回主键"); }
                    return result.getInt("favorite_id");
                }
            }
        });
    }
    @Override
    public int deleteByUserAndItem(Connection connection, int userId, int itemId) {
        validateIds(userId, itemId);
        return onConnection(connection, current -> update(current,
                "DELETE FROM favorite WHERE user_id=? AND item_id=?", userId, itemId));
    }
    @Override
    public boolean exists(Connection connection, int userId, int itemId) {
        validateIds(userId, itemId);
        return onConnection(connection, current -> count(current,
                "SELECT count(*) FROM favorite WHERE user_id=? AND item_id=?", userId, itemId) != 0);
    }
    @Override
    public PageResult<Favorite> findByUser(Connection connection, int userId, int pageNo, int pageSize) {
        if (userId <= 0 || pageNo < 1 || pageSize < 10 || pageSize > 100) {
            throw new IllegalArgumentException("用户编号或分页参数不合法");
        }
        long offset = ((long) pageNo - 1) * pageSize;
        return onConnection(connection, current -> {
            int total = count(current, "SELECT count(*) FROM favorite WHERE user_id=?", userId);
            List<Favorite> records = query(current, SELECT
                    + " WHERE f.user_id=? ORDER BY f.created_at DESC,f.favorite_id DESC LIMIT ? OFFSET ?",
                    userId, pageSize, offset);
            return new PageResult<>(records, total, pageNo, pageSize);
        });
    }
    @Override
    protected Favorite mapRow(ResultSet result) throws SQLException {
        try {
            Favorite favorite = new Favorite(result.getInt("user_id"), result.getInt("item_id"));
            favorite.setFavoriteId(result.getInt("favorite_id"));
            favorite.setCreatedAt(LocalDateTime.parse(result.getString("favorite_created_at"), DATE_FORMAT));
            favorite.setItem(ItemRowMapper.read(result));
            return favorite;
        } catch (IllegalArgumentException | DateTimeException failure) {
            throw new SQLException("收藏记录包含非法字段", failure);
        }
    }

    private static void validateIds(int userId, int itemId) {
        if (userId <= 0 || itemId <= 0) { throw new IllegalArgumentException("用户和物品编号须为正整数"); }
    }
}
