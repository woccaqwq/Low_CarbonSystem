package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.TradeType;
import java.time.LocalDateTime;

/** 流转记录；语义状态方法只修改内存，事务与持久化由 Service/DAO 负责。 */
public final class FlowRecord {
    private int flowId;
    private int itemId;
    private int sellerId;
    private int buyerId;
    private TradeType flowType = TradeType.EXCHANGE;
    private FlowStatus status = FlowStatus.PENDING;
    private String remark = "";
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private String itemTitle;
    private String sellerNickname;
    private String buyerNickname;

    /** 创建待填充的意向记录。 */
    public FlowRecord() { }
    /** @param itemId 物品 @param sellerId 卖家 @param buyerId 买家 @param flowType 类型 @param remark 留言 */
    public FlowRecord(int itemId, int sellerId, int buyerId, TradeType flowType, String remark) {
        setItemId(itemId);
        setSellerId(sellerId);
        setBuyerId(buyerId);
        setFlowType(flowType);
        setRemark(remark);
    }
    /** @return 主键，未保存为 0 */
    public int getFlowId() { return flowId; }
    /** @param flowId 非负编号 */
    public void setFlowId(int flowId) {
        if (flowId < 0) { throw new IllegalArgumentException("流转编号不合法"); }
        this.flowId = flowId;
    }
    /** @return 物品编号 */
    public int getItemId() { return itemId; }
    /** @param itemId 正整数编号 */
    public void setItemId(int itemId) { requirePositive(itemId); this.itemId = itemId; }
    /** @return 卖家编号 */
    public int getSellerId() { return sellerId; }
    /** @param sellerId 正整数，不能与买家相同 */
    public void setSellerId(int sellerId) {
        requirePositive(sellerId);
        if (sellerId == buyerId) { throw new IllegalArgumentException("不能与自己流转"); }
        this.sellerId = sellerId;
    }
    /** @return 买家编号 */
    public int getBuyerId() { return buyerId; }
    /** @param buyerId 正整数，不能与卖家相同 */
    public void setBuyerId(int buyerId) {
        requirePositive(buyerId);
        if (buyerId == sellerId) { throw new IllegalArgumentException("不能与自己流转"); }
        this.buyerId = buyerId;
    }
    /** @return 交易方式 */
    public TradeType getFlowType() { return flowType; }
    /** @param flowType 非空交易方式 */
    public void setFlowType(TradeType flowType) {
        if (flowType == null) { throw new IllegalArgumentException("交易方式不能为空"); }
        this.flowType = flowType;
    }
    /** @return 当前状态 */
    public FlowStatus getStatus() { return status; }
    /** @param status 非空状态，供映射使用，业务转移使用语义方法 */
    public void setStatus(FlowStatus status) {
        if (status == null) { throw new IllegalArgumentException("流转状态不能为空"); }
        this.status = status;
    }
    /** @return 留言与附加取消原因 */
    public String getRemark() { return remark; }
    /** @param remark 非 null，可为空串 */
    public void setRemark(String remark) {
        if (remark == null) { throw new IllegalArgumentException("留言不能为 null"); }
        this.remark = remark;
    }
    /** @return 创建时间 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt 创建时间，null 使用数据库默认值 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** @return 完成时间，待确认时为 null */
    public LocalDateTime getFinishedAt() { return finishedAt; }
    /** @param finishedAt 完成时间，映射时可为 null */
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    /** @return 物品当前标题投影 */
    public String getItemTitle() { return itemTitle; }
    /** @param itemTitle 查询投影，不写入 flow_record */
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    /** @return 卖家当前昵称投影 */
    public String getSellerNickname() { return sellerNickname; }
    /** @param sellerNickname 查询投影 */
    public void setSellerNickname(String sellerNickname) { this.sellerNickname = sellerNickname; }
    /** @return 买家当前昵称投影 */
    public String getBuyerNickname() { return buyerNickname; }
    /** @param buyerNickname 查询投影 */
    public void setBuyerNickname(String buyerNickname) { this.buyerNickname = buyerNickname; }

    /** 确认待处理记录，非法状态抛 IllegalStateException；不自动保存。 */
    public void confirm() {
        transferTo(FlowStatus.DEAL);
    }
    /** @param reason 非空白取消原因，附加到留言后；不自动保存 */
    public void cancel(String reason) {
        if (reason == null || reason.isBlank()) { throw new IllegalArgumentException("取消原因不能为空"); }
        transferTo(FlowStatus.CANCELLED);
        remark = (remark.isEmpty() ? "" : remark + "\n") + "取消原因：" + reason;
    }
    private void transferTo(FlowStatus target) {
        if (!status.canTransferTo(target)) { throw new IllegalStateException("当前流转状态不支持该操作"); }
        status = target;
        finishedAt = LocalDateTime.now();
    }
    private static void requirePositive(int value) {
        if (value <= 0) { throw new IllegalArgumentException("关联编号须为正整数"); }
    }
}
