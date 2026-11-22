package com.campus.recycle.service;

import com.campus.recycle.entity.Review;
import com.campus.recycle.exception.BusinessException;
import java.util.List;

/** 评价业务：资格校验、接收人信用调整与列表查询，事务边界集中在此层。 */
public interface ReviewService {
    /**
     * 成交双方之一对本次流转评价；接收人由流转记录推导，每人一次，仅调整接收人信用。
     * @param flowId 流转编号 @param fromUserId 评价人 @param score 1–5 @param content 不超过 200 字
     * @throws BusinessException 资格不符、重复评价或参数越界
     */
    void submitReview(int flowId, int fromUserId, int score, String content) throws BusinessException;

    /** @param userId 被评价人 @return 收到的评价 */
    List<Review> reviewsOf(int userId);

    /** @param flowId 流转编号 @param userId 用户编号 @return 当前是否可以评价 */
    boolean canReview(int flowId, int userId);
}
