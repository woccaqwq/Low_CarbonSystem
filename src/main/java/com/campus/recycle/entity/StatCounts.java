package com.campus.recycle.entity;

/** 不可变的六类计数，物品与意向记录分别求和，使用 long 避免报表累计截断。 */
public final class StatCounts {
    private final long onSaleItemCount;
    private final long offShelfItemCount;
    private final long flowedItemCount;
    private final long pendingFlowCount;
    private final long dealFlowCount;
    private final long cancelledFlowCount;
    private final long itemCount;
    private final long flowCount;

    /**
     * @param onSaleItemCount 在架物品
     * @param offShelfItemCount 下架物品
     * @param flowedItemCount 已流转物品
     * @param pendingFlowCount 待确认意向
     * @param dealFlowCount 成交记录
     * @param cancelledFlowCount 取消记录
     */
    public StatCounts(long onSaleItemCount, long offShelfItemCount, long flowedItemCount,
                      long pendingFlowCount, long dealFlowCount, long cancelledFlowCount) {
        if (onSaleItemCount < 0 || offShelfItemCount < 0 || flowedItemCount < 0
                || pendingFlowCount < 0 || dealFlowCount < 0 || cancelledFlowCount < 0) {
            throw new IllegalArgumentException("统计数量不能为负");
        }
        try {
            itemCount = Math.addExact(Math.addExact(onSaleItemCount, offShelfItemCount), flowedItemCount);
            flowCount = Math.addExact(Math.addExact(pendingFlowCount, dealFlowCount), cancelledFlowCount);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("统计总数超出 long 范围", overflow);
        }
        this.onSaleItemCount = onSaleItemCount;
        this.offShelfItemCount = offShelfItemCount;
        this.flowedItemCount = flowedItemCount;
        this.pendingFlowCount = pendingFlowCount;
        this.dealFlowCount = dealFlowCount;
        this.cancelledFlowCount = cancelledFlowCount;
    }
    /** @return 在架物品数 */
    public long getOnSaleItemCount() { return onSaleItemCount; }
    /** @return 下架物品数 */
    public long getOffShelfItemCount() { return offShelfItemCount; }
    /** @return 已流转物品数 */
    public long getFlowedItemCount() { return flowedItemCount; }
    /** @return 待确认意向数 */
    public long getPendingFlowCount() { return pendingFlowCount; }
    /** @return 成交记录数 */
    public long getDealFlowCount() { return dealFlowCount; }
    /** @return 取消记录数 */
    public long getCancelledFlowCount() { return cancelledFlowCount; }
    /** @return 三类物品状态之和 */
    public long getItemCount() { return itemCount; }
    /** @return 三类流转状态之和 */
    public long getFlowCount() { return flowCount; }
}
