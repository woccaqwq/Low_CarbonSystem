package com.campus.recycle.dao;

import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.exception.DataAccessException;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** 流转存取契约；所有 Connection 重载均不管理调用方的连接和事务。 */
public interface FlowRecordDao {
    /** @param record 新的 PENDING 意向 @return 新主键，不修改输入对象 */
    int insert(FlowRecord record) throws DataAccessException;
    /** @param flowId 编号 @return 记录或空 */
    Optional<FlowRecord> findById(int flowId) throws DataAccessException;
    /** @param flowId 编号 @param status DEAL 或 CANCELLED @return 条件更新行数，终态或冲突返回 0 */
    int updateStatus(int flowId, FlowStatus status) throws DataAccessException;
    /** @param itemId 物品编号 @param buyerId 买家编号 @return 是否存在待确认意向 */
    boolean existsPending(int itemId, int buyerId) throws DataAccessException;
    /** @param userId 参与人编号 @param status null 不限 @return 买家或卖家的历史，时间/编号倒序 */
    List<FlowRecord> findByUser(int userId, FlowStatus status) throws DataAccessException;
    /** @param itemId 物品编号 @param exceptFlowId 属于该物品且已成交的保留记录 @return 取消行数 */
    int cancelOtherPending(int itemId, int exceptFlowId) throws DataAccessException;
    /** @param flowId 编号 @param reason 非空白原因，附加到 remark @return 从 PENDING 取消的行数 */
    int cancel(int flowId, String reason) throws DataAccessException;
    /** @param userId 参与人 @param status 状态或 null @param pageNo 1 起 @param pageSize 10–100 @return 分页 */
    PageResult<FlowRecord> findByUser(int userId, FlowStatus status, int pageNo, int pageSize)
            throws DataAccessException;
    /** @param buyerId 买家 @param status 状态或 null @param pageNo 页码 @param pageSize 页大小 @return 分页 */
    PageResult<FlowRecord> findByBuyer(int buyerId, FlowStatus status, int pageNo, int pageSize)
            throws DataAccessException;
    /** @param sellerId 卖家 @param status 状态或 null @param pageNo 页码 @param pageSize 页大小 @return 分页 */
    PageResult<FlowRecord> findBySeller(int sellerId, FlowStatus status, int pageNo, int pageSize)
            throws DataAccessException;

    /** @param connection 外部连接 @param record 新意向 @return 新主键 */
    int insert(Connection connection, FlowRecord record) throws DataAccessException;
    /** @param connection 外部连接 @param flowId 编号 @return 记录或空 */
    Optional<FlowRecord> findById(Connection connection, int flowId) throws DataAccessException;
    /** @param connection 外部连接 @param flowId 编号 @param status 目标终态 @return 条件更新行数 */
    int updateStatus(Connection connection, int flowId, FlowStatus status) throws DataAccessException;
    /** @param connection 外部连接 @param itemId 物品 @param buyerId 买家 @return 是否有待确认意向 */
    boolean existsPending(Connection connection, int itemId, int buyerId) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户 @param status 状态或 null @return 参与历史 */
    List<FlowRecord> findByUser(Connection connection, int userId, FlowStatus status) throws DataAccessException;
    /** @param connection 外部连接 @param itemId 物品 @param exceptFlowId 已成交保留记录 @return 取消行数 */
    int cancelOtherPending(Connection connection, int itemId, int exceptFlowId) throws DataAccessException;
    /** @param connection 外部连接 @param flowId 编号 @param reason 取消原因 @return 取消行数 */
    int cancel(Connection connection, int flowId, String reason) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户 @param status 状态 @param pageNo 页码 @param pageSize 大小 @return 分页 */
    PageResult<FlowRecord> findByUser(Connection connection, int userId, FlowStatus status, int pageNo, int pageSize)
            throws DataAccessException;
    /** @param connection 外部连接 @param buyerId 买家 @param status 状态 @param pageNo 页码 @param pageSize 大小 @return 分页 */
    PageResult<FlowRecord> findByBuyer(Connection connection, int buyerId, FlowStatus status, int pageNo, int pageSize)
            throws DataAccessException;
    /** @param connection 外部连接 @param sellerId 卖家 @param status 状态 @param pageNo 页码 @param pageSize 大小 @return 分页 */
    PageResult<FlowRecord> findBySeller(Connection connection, int sellerId, FlowStatus status, int pageNo, int pageSize)
            throws DataAccessException;
}
