package com.campus.recycle.service;

import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.exception.BusinessException;
import java.util.List;

/** 管理员业务：违规下架、账号启停与日志查询；所有操作在 Service 层复核角色。 */
public interface AdminService {
    /** @param itemId 物品编号 @param reason 下架原因 @param admin 操作人快照 @throws BusinessException 非管理员或参数不合法 */
    void offlineIllegalItem(int itemId, String reason, SessionUser admin) throws BusinessException;

    /** @param userId 目标用户 @param enable true 启用 / false 禁用 @param admin 操作人 @throws BusinessException 非管理员或禁止禁用自身 */
    void toggleUserStatus(int userId, boolean enable, SessionUser admin) throws BusinessException;

    /** @param limit 1–1000 @return 最近业务日志 @throws BusinessException 非管理员或参数越界 */
    List<OperationLog> recentLogs(int limit) throws BusinessException;
}
