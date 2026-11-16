package com.campus.recycle.dao;

import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.ItemQuery;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.exception.DataAccessException;
import java.sql.Connection;
import java.util.Optional;

/** 物品持久化；Connection 重载仅参与调用方事务，不管理其生命周期。 */
public interface ItemDao {
    /** @param itemId 编号 @return 完整物品及列表投影，查不到为空 */
    Optional<Item> findById(int itemId) throws DataAccessException;
    /** @param item 待保存物品 @return 新主键，不修改输入对象 */
    int insert(Item item) throws DataAccessException;
    /** @param item 修改数据，只更新标题、描述、价格、交易方式、分类和标签 @return 行数 */
    int update(Item item) throws DataAccessException;
    /** @param itemId 编号 @param status 目标状态，业务规则由 Service 校验 @return 行数 */
    int updateStatus(int itemId, ItemStatus status) throws DataAccessException;
    /** @param itemId 编号 @return 浏览量加一的行数 */
    int increaseViewCount(int itemId) throws DataAccessException;
    /** @param itemId 编号 @return 是否存在且在架 */
    boolean existsOnSale(int itemId) throws DataAccessException;
    /** @param query 非空检索条件 @return 分页结果 */
    PageResult<Item> search(ItemQuery query) throws DataAccessException;
    /** @param connection 外部连接 @param itemId 编号 @return 物品或空 */
    Optional<Item> findById(Connection connection, int itemId) throws DataAccessException;
    /** @param connection 外部连接 @param item 物品 @return 新主键 */
    int insert(Connection connection, Item item) throws DataAccessException;
    /** @param connection 外部连接 @param item 物品 @return 行数 */
    int update(Connection connection, Item item) throws DataAccessException;
    /** @param connection 外部连接 @param itemId 编号 @param status 状态 @return 行数 */
    int updateStatus(Connection connection, int itemId, ItemStatus status) throws DataAccessException;
    /** @param connection 外部连接 @param itemId 编号 @return 行数 */
    int increaseViewCount(Connection connection, int itemId) throws DataAccessException;
    /** @param connection 外部连接 @param itemId 编号 @return 是否在架 */
    boolean existsOnSale(Connection connection, int itemId) throws DataAccessException;
    /** @param connection 外部连接 @param query 检索条件 @return 分页结果 */
    PageResult<Item> search(Connection connection, ItemQuery query) throws DataAccessException;
}
