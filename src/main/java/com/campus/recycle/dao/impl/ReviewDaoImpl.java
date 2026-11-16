package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.ReviewDao;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.Review;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** 评价 JDBC 实现；唯一约束阻止重复评价，信用更新由 Service 使用同一连接编排。 */
public final class ReviewDaoImpl extends BaseDao<Review> implements ReviewDao {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SELECT = "SELECT r.*,a.nickname AS from_nickname,b.nickname AS to_nickname,"
            + "i.title AS item_title FROM review r LEFT JOIN sys_user a ON a.user_id=r.from_user_id "
            + "LEFT JOIN sys_user b ON b.user_id=r.to_user_id LEFT JOIN flow_record f ON f.flow_id=r.flow_id "
            + "LEFT JOIN item i ON i.item_id=f.item_id";
    private static final String ORDER = " ORDER BY r.created_at DESC,r.review_id DESC";

    /** 使用默认数据库。 */
    public ReviewDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 独立数据库工厂 */
    public ReviewDaoImpl(DBUtil database) { super(database); }

    @Override
    public int insert(Review review) {
        return withConnection(connection -> insert(connection, review));
    }
    @Override
    public boolean existsByFlowAndFrom(int flowId, int fromUserId) {
        return withConnection(connection -> existsByFlowAndFrom(connection, flowId, fromUserId));
    }
    @Override
    public List<Review> findByUser(int userId) {
        return withConnection(connection -> findByUser(connection, userId));
    }
    @Override
    public double avgScoreOf(int userId) {
        return withConnection(connection -> avgScoreOf(connection, userId));
    }
    @Override
    public Optional<Review> findById(int reviewId) {
        return withConnection(connection -> findById(connection, reviewId));
    }
    @Override
    public PageResult<Review> findByUser(int userId, int pageNo, int pageSize) {
        return withConnection(connection -> findByUser(connection, userId, pageNo, pageSize));
    }
    @Override
    public int insert(Connection connection, Review review) {
        if (review == null) { throw new IllegalArgumentException("评价不能为空"); }
        positive(review.getFlowId());
        positive(review.getFromUserId());
        positive(review.getToUserId());
        review.validateScore();
        LocalDateTime time = review.getCreatedAt();
        return onConnection(connection, current -> insertAndReturnId(current,
                "INSERT INTO review(flow_id,from_user_id,to_user_id,score,content,created_at) "
                        + "VALUES (?,?,?,?,?,COALESCE(?,datetime('now','localtime')))",
                review.getFlowId(), review.getFromUserId(), review.getToUserId(), review.getScore(),
                review.getContent(), time == null ? null : DATE_FORMAT.format(time)));
    }
    @Override
    public boolean existsByFlowAndFrom(Connection connection, int flowId, int fromUserId) {
        positive(flowId);
        positive(fromUserId);
        return onConnection(connection, current -> count(current,
                "SELECT count(*) FROM review WHERE flow_id=? AND from_user_id=?", flowId, fromUserId) != 0);
    }
    @Override
    public List<Review> findByUser(Connection connection, int userId) {
        positive(userId);
        return onConnection(connection, current -> query(current, SELECT + " WHERE r.to_user_id=?" + ORDER,
                userId));
    }
    @Override
    public double avgScoreOf(Connection connection, int userId) {
        positive(userId);
        return onConnection(connection, current -> {
            try (PreparedStatement statement = current.prepareStatement(
                    "SELECT COALESCE(AVG(score),0.0) FROM review WHERE to_user_id=?")) {
                statement.setInt(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) { throw new SQLException("平均分查询未返回结果"); }
                    return result.getDouble(1);
                }
            }
        });
    }
    @Override
    public Optional<Review> findById(Connection connection, int reviewId) {
        positive(reviewId);
        return onConnection(connection, current -> query(current, SELECT + " WHERE r.review_id=?", reviewId)
                .stream().findFirst());
    }
    @Override
    public PageResult<Review> findByUser(Connection connection, int userId, int pageNo, int pageSize) {
        positive(userId);
        if (pageNo < 1 || pageSize < 10 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        long offset = ((long) pageNo - 1) * pageSize;
        return onConnection(connection, current -> {
            int total = count(current, "SELECT count(*) FROM review WHERE to_user_id=?", userId);
            List<Review> records = query(current, SELECT + " WHERE r.to_user_id=?" + ORDER + " LIMIT ? OFFSET ?",
                    userId, pageSize, offset);
            return new PageResult<>(records, total, pageNo, pageSize);
        });
    }
    @Override
    protected Review mapRow(ResultSet result) throws SQLException {
        try {
            Review review = new Review(result.getInt("flow_id"), result.getInt("from_user_id"),
                    result.getInt("to_user_id"), result.getInt("score"), result.getString("content"));
            review.setReviewId(result.getInt("review_id"));
            review.setCreatedAt(LocalDateTime.parse(result.getString("created_at"), DATE_FORMAT));
            review.setFromNickname(result.getString("from_nickname"));
            review.setToNickname(result.getString("to_nickname"));
            review.setItemTitle(result.getString("item_title"));
            return review;
        } catch (IllegalArgumentException | DateTimeException failure) {
            throw new SQLException("评价记录包含非法字段", failure);
        }
    }
    private static void positive(int id) {
        if (id <= 0) { throw new IllegalArgumentException("编号须为正整数"); }
    }
}
