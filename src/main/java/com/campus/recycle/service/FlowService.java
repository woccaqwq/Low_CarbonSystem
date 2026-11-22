package com.campus.recycle.service;

import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.exception.BusinessException;
import java.util.List;

/** 流转业务：发起意向、确认成交、取消与历史查询，事务边界集中在此层。 */
public interface FlowService {
    /**
     * 对在架、非本人物品发起意向，校验重复意向。
     * @param itemId 物品编号 @param buyerId 买家编号 @param remark 留言
     * @return 新流转编号 @throws BusinessException 校验失败
     */
    int requestFlow(int itemId, int buyerId, String remark) throws BusinessException;

    /**
     * 持有人确认成交：流转状态、物品状态、其他意向、双方信用与双方日志同事务。
     * @param flowId 流转编号 @param operatorId 操作人编号
     * @throws BusinessException 权限不足、状态非法或数据写入失败
     */
    void confirmDeal(int flowId, int operatorId) throws BusinessException;

    /**
     * 取消待确认意向，仅买卖双方可执行。
     * @param flowId 流转编号 @param operatorId 操作人编号 @param reason 取消原因
     * @throws BusinessException 权限不足、状态非法或原因缺失
     */
    void cancelFlow(int flowId, int operatorId, String reason) throws BusinessException;

    /** @param userId 用户编号 @param status 状态或 null @return 参与历史 */
    List<FlowRecord> myFlows(int userId, FlowStatus status);
}
