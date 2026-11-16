package com.campus.recycle.dao;

import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.Review;
import com.campus.recycle.exception.DataAccessException;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** 评价持久化；不自动调整信用，Connection 重载不管理调用方事务。 */
public interface ReviewDao {
    /** @param review 评价 @return 新主键，重复评价抛 DataAccessException，不修改输入对象 */
    int insert(Review review) throws DataAccessException;
    /** @param flowId 流转 @param fromUserId 评价人 @return 是否已评价该次流转 */
    boolean existsByFlowAndFrom(int flowId, int fromUserId) throws DataAccessException;
    /** @param userId 被评价人 @return 收到的评价，时间/编号倒序 */
    List<Review> findByUser(int userId) throws DataAccessException;
    /** @param userId 被评价人 @return 收到评价的平均分，没有评价时返回 0.0 */
    double avgScoreOf(int userId) throws DataAccessException;
    /** @param reviewId 主键 @return 评价及当前展示投影，查不到为空 */
    Optional<Review> findById(int reviewId) throws DataAccessException;
    /** @param userId 被评价人 @param pageNo 页码 1 起 @param pageSize 10–100 @return 收到评价分页 */
    PageResult<Review> findByUser(int userId, int pageNo, int pageSize) throws DataAccessException;
    /** @param connection 外部连接 @param review 评价 @return 新主键 */
    int insert(Connection connection, Review review) throws DataAccessException;
    /** @param connection 外部连接 @param flowId 流转 @param fromUserId 评价人 @return 是否已评价 */
    boolean existsByFlowAndFrom(Connection connection, int flowId, int fromUserId) throws DataAccessException;
    /** @param connection 外部连接 @param userId 被评价人 @return 收到的评价 */
    List<Review> findByUser(Connection connection, int userId) throws DataAccessException;
    /** @param connection 外部连接 @param userId 被评价人 @return 收评平均分，没有评价时为 0.0 */
    double avgScoreOf(Connection connection, int userId) throws DataAccessException;
    /** @param connection 外部连接 @param reviewId 主键 @return 评价或空 */
    Optional<Review> findById(Connection connection, int reviewId) throws DataAccessException;
    /** @param connection 外部连接 @param userId 被评价人 @param pageNo 页码 @param pageSize 页大小 @return 分页 */
    PageResult<Review> findByUser(Connection connection, int userId, int pageNo, int pageSize)
            throws DataAccessException;
}
