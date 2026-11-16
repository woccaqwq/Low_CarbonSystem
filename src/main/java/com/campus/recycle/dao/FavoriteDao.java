package com.campus.recycle.dao;

import com.campus.recycle.entity.Favorite;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.exception.DataAccessException;
import java.sql.Connection;

/** 收藏持久化；参数顺序为 userId、itemId，Connection 重载不管理外部事务。 */
public interface FavoriteDao {
    /** @param favorite 收藏数据 @return 新主键或同用户同物品的原主键，保留原收藏时间 */
    int insert(Favorite favorite) throws DataAccessException;
    /** @param userId 用户编号 @param itemId 物品编号 @return 删除行数，重复取消返回 0 */
    int deleteByUserAndItem(int userId, int itemId) throws DataAccessException;
    /** @param userId 用户编号 @param itemId 物品编号 @return 是否已收藏 */
    boolean exists(int userId, int itemId) throws DataAccessException;
    /**
     * 查询收藏列表，含物品当前状态，按收藏时间和编号倒序。
     * @param userId 用户编号
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页 10–100
     * @return 分页结果，不限物品状态
     */
    PageResult<Favorite> findByUser(int userId, int pageNo, int pageSize) throws DataAccessException;
    /** @param connection 外部连接 @param favorite 收藏 @return 新主键或已有主键 */
    int insert(Connection connection, Favorite favorite) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户编号 @param itemId 物品编号 @return 删除行数 */
    int deleteByUserAndItem(Connection connection, int userId, int itemId) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户编号 @param itemId 物品编号 @return 是否已收藏 */
    boolean exists(Connection connection, int userId, int itemId) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户编号 @param pageNo 页码 @param pageSize 页大小 @return 分页 */
    PageResult<Favorite> findByUser(Connection connection, int userId, int pageNo, int pageSize)
            throws DataAccessException;
}
