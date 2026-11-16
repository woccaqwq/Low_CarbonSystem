package com.campus.recycle.entity.enums;

/** 与 item.status 一致；已流转为终态。 */
public enum ItemStatus {
    ON_SALE, OFF_SHELF, FLOWED;

    /** @param target 目标状态 @return 是否允许实体状态转移 */
    public boolean canTransferTo(ItemStatus target) {
        return (this == ON_SALE && (target == OFF_SHELF || target == FLOWED))
                || (this == OFF_SHELF && target == ON_SALE);
    }
}
