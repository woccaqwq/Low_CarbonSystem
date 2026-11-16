package com.campus.recycle.entity.enums;

/** 流转状态，成交与取消均为终态。 */
public enum FlowStatus {
    PENDING("待确认"), DEAL("已成交"), CANCELLED("已取消");

    private final String label;
    FlowStatus(String label) { this.label = label; }
    /** @return 展示名称 */
    public String getLabel() { return label; }
    /** @param target 目标状态 @return 是否允许从当前状态转移 */
    public boolean canTransferTo(FlowStatus target) {
        return this == PENDING && (target == DEAL || target == CANCELLED);
    }
}
