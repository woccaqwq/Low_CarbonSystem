package com.campus.recycle.service.impl;

import com.campus.recycle.dao.FlowRecordDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.ReviewDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.Review;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.exception.ValidationException;
import com.campus.recycle.service.ReviewService;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 评价业务实现：交易双方资格、七天窗口、每人一次与接收人信用同事务。
 * 接收人由流转记录推导，不接受界面指定；每条评价只调整接收人信用。
 */
public final class ReviewServiceImpl implements ReviewService {
    private static final int REVIEW_WINDOW_DAYS = 7;

    private final DBUtil database;
    private final ReviewDao reviews;
    private final FlowRecordDao flows;
    private final UserDao users;
    private final OperationLogDao logs;
    private final TxSupport tx;

    /** 使用默认连接工厂与 DAO 构造。 */
    public ReviewServiceImpl() {
        this(DBUtil.getInstance(), DaoFactory.getReviewDao(), DaoFactory.getFlowRecordDao(),
                DaoFactory.getUserDao(), DaoFactory.getOperationLogDao());
    }

    /**
     * 注入依赖，便于测试使用独立数据库。
     */
    public ReviewServiceImpl(DBUtil database, ReviewDao reviews, FlowRecordDao flows,
                             UserDao users, OperationLogDao logs) {
        this.database = Objects.requireNonNull(database, "database");
        this.reviews = Objects.requireNonNull(reviews, "reviews");
        this.flows = Objects.requireNonNull(flows, "flows");
        this.users = Objects.requireNonNull(users, "users");
        this.logs = Objects.requireNonNull(logs, "logs");
        this.tx = new TxSupport(database);
    }

    @Override
    public void submitReview(int flowId, int fromUserId, int score, String content) throws BusinessException {
        if (flowId <= 0 || fromUserId <= 0) {
            throw new ValidationException("id", ErrorCode.VALIDATION_ERROR);
        }
        if (score < 1 || score > 5) { throw new ValidationException("score", ErrorCode.VALIDATION_ERROR); }
        if (content == null || content.codePointCount(0, content.length()) > 200) {
            throw new ValidationException("content", ErrorCode.VALIDATION_ERROR);
        }
        tx.run(connection -> {
            FlowRecord record = flows.findById(connection, flowId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FLOW_NOT_FOUND));
            if (record.getStatus() != FlowStatus.DEAL) {
                throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
            }
            if (record.getSellerId() != fromUserId && record.getBuyerId() != fromUserId) {
                throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
            }
            LocalDateTime basis = record.getFinishedAt() == null ? record.getCreatedAt() : record.getFinishedAt();
            if (basis == null || Duration.between(basis, LocalDateTime.now()).toDays() > REVIEW_WINDOW_DAYS
                    || LocalDateTime.now().isBefore(basis)) {
                throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
            }
            if (reviews.existsByFlowAndFrom(connection, flowId, fromUserId)) {
                throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
            }
            int toUserId = record.getSellerId() == fromUserId ? record.getBuyerId() : record.getSellerId();
            reviews.insert(connection, new Review(flowId, fromUserId, toUserId, score, content));
            int delta = score >= 4 ? 2 : (score <= 2 ? -3 : 0);
            if (delta != 0) {
                requireUpdated(users.updateCreditScore(connection, toUserId, delta));
            }
            logs.insert(connection, new OperationLog(fromUserId, "REVIEW_SUBMIT",
                    "评价流转编号=" + flowId + "，评分=" + score));
            return null;
        });
    }

    @Override
    public List<Review> reviewsOf(int userId) {
        if (userId <= 0) { return List.of(); }
        return reviews.findByUser(userId);
    }

    @Override
    public boolean canReview(int flowId, int userId) {
        if (flowId <= 0 || userId <= 0) { return false; }
        try {
            return tx.call(connection -> {
                FlowRecord record = flows.findById(connection, flowId).orElse(null);
                if (record == null || record.getStatus() != FlowStatus.DEAL) { return false; }
                if (record.getSellerId() != userId && record.getBuyerId() != userId) { return false; }
                LocalDateTime basis = record.getFinishedAt() == null ? record.getCreatedAt() : record.getFinishedAt();
                if (basis == null || Duration.between(basis, LocalDateTime.now()).toDays() > REVIEW_WINDOW_DAYS
                        || LocalDateTime.now().isBefore(basis)) {
                    return false;
                }
                return !reviews.existsByFlowAndFrom(connection, flowId, userId);
            });
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw new com.campus.recycle.exception.DataAccessException(ErrorCode.DB_ERROR.getMessage(),
                    new IllegalStateException("信用更新命中 " + rows + " 行，应为 1 行"));
        }
    }
}
