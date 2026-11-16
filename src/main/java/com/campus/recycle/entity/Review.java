package com.campus.recycle.entity;

import java.time.LocalDateTime;

/** 评价实体，不负责资格校验或更新信用分。 */
public final class Review {
    private int reviewId;
    private int flowId;
    private int fromUserId;
    private int toUserId;
    private int score;
    private String content = "";
    private LocalDateTime createdAt;
    private String fromNickname;
    private String toNickname;
    private String itemTitle;

    /** 创建待填充的评价，保存前必须设置有效评分与关联编号。 */
    public Review() { }
    /** @param flowId 流转 @param fromUserId 评价人 @param toUserId 被评价人 @param score 1–5 @param content 内容 */
    public Review(int flowId, int fromUserId, int toUserId, int score, String content) {
        setFlowId(flowId);
        setFromUserId(fromUserId);
        setToUserId(toUserId);
        setScore(score);
        setContent(content);
    }
    /** @return 主键，未保存为 0 */
    public int getReviewId() { return reviewId; }
    /** @param reviewId 非负主键 */
    public void setReviewId(int reviewId) {
        if (reviewId < 0) { throw new IllegalArgumentException("评价编号不合法"); }
        this.reviewId = reviewId;
    }
    /** @return 流转编号 */
    public int getFlowId() { return flowId; }
    /** @param flowId 正整数编号 */
    public void setFlowId(int flowId) { positive(flowId); this.flowId = flowId; }
    /** @return 评价人编号 */
    public int getFromUserId() { return fromUserId; }
    /** @param fromUserId 正整数，不得与被评价人相同 */
    public void setFromUserId(int fromUserId) {
        positive(fromUserId);
        if (fromUserId == toUserId) { throw new IllegalArgumentException("不能评价自己"); }
        this.fromUserId = fromUserId;
    }
    /** @return 被评价人编号 */
    public int getToUserId() { return toUserId; }
    /** @param toUserId 正整数，不得与评价人相同 */
    public void setToUserId(int toUserId) {
        positive(toUserId);
        if (toUserId == fromUserId) { throw new IllegalArgumentException("不能评价自己"); }
        this.toUserId = toUserId;
    }
    /** @return 评分 */
    public int getScore() { return score; }
    /** @param score 1–5 之间的评分 */
    public void setScore(int score) {
        checkScore(score);
        this.score = score;
    }
    /** 校验当前评分，未填或越界时抛 IllegalArgumentException。 */
    public void validateScore() { checkScore(score); }
    /** @return 评价内容 */
    public String getContent() { return content; }
    /** @param content 非 null，最多 200 个 Unicode 码点，可为空串 */
    public void setContent(String content) {
        if (content == null || content.codePointCount(0, content.length()) > 200) {
            throw new IllegalArgumentException("评价内容不能为 null 或超过 200 字");
        }
        this.content = content;
    }
    /** @return 创建时间，保存前可为 null */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt 创建时间，null 使用数据库默认值 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** @return 评价人当前昵称投影 */
    public String getFromNickname() { return fromNickname; }
    /** @param fromNickname 只作展示的查询投影 */
    public void setFromNickname(String fromNickname) { this.fromNickname = fromNickname; }
    /** @return 被评价人当前昵称投影 */
    public String getToNickname() { return toNickname; }
    /** @param toNickname 只作展示的查询投影 */
    public void setToNickname(String toNickname) { this.toNickname = toNickname; }
    /** @return 物品当前标题投影 */
    public String getItemTitle() { return itemTitle; }
    /** @param itemTitle 只作展示的查询投影 */
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }

    private static void positive(int id) {
        if (id <= 0) { throw new IllegalArgumentException("关联编号须为正整数"); }
    }
    private static void checkScore(int score) {
        if (score < 1 || score > 5) { throw new IllegalArgumentException("评分须在 1–5 之间"); }
    }
}
