package com.campus.recycle.service.impl;

import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.entity.User;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.Role;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.exception.ValidationException;
import com.campus.recycle.service.AdminService;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;

/** 管理员业务实现：角色在事务内复核，不信任界面快照；下架原因写入操作日志。 */
public final class AdminServiceImpl implements AdminService {
    private final DBUtil database;
    private final ItemDao items;
    private final UserDao users;
    private final OperationLogDao logs;
    private final TxSupport tx;

    /** 使用默认连接工厂与 DAO 构造。 */
    public AdminServiceImpl() {
        this(DBUtil.getInstance(), DaoFactory.getItemDao(), DaoFactory.getUserDao(), DaoFactory.getOperationLogDao());
    }

    /**
     * 注入依赖，便于测试使用独立数据库。
     */
    public AdminServiceImpl(DBUtil database, ItemDao items, UserDao users, OperationLogDao logs) {
        this.database = Objects.requireNonNull(database, "database");
        this.items = Objects.requireNonNull(items, "items");
        this.users = Objects.requireNonNull(users, "users");
        this.logs = Objects.requireNonNull(logs, "logs");
        this.tx = new TxSupport(database);
    }

    @Override
    public void offlineIllegalItem(int itemId, String reason, SessionUser admin) throws BusinessException {
        if (itemId <= 0) { throw new ValidationException("itemId", ErrorCode.VALIDATION_ERROR); }
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > 200) {
            throw new ValidationException("reason", ErrorCode.VALIDATION_ERROR);
        }
        requireAdminSnapshot(admin);
        tx.run(connection -> {
            requireAdmin(connection, admin.getUserId());
            Item item = items.findById(connection, itemId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
            if (item.getStatus() == ItemStatus.FLOWED) {
                throw new BusinessException(ErrorCode.FLOW_STATUS_ILLEGAL);
            }
            requireUpdated(items.updateStatus(connection, itemId, ItemStatus.OFF_SHELF));
            logs.insert(connection, new OperationLog(admin.getUserId(), "ADMIN_OFFLINE",
                    "违规下架物品编号=" + itemId + "，原因=" + reason));
            return null;
        });
    }

    @Override
    public void toggleUserStatus(int userId, boolean enable, SessionUser admin) throws BusinessException {
        if (userId <= 0) { throw new ValidationException("userId", ErrorCode.VALIDATION_ERROR); }
        requireAdminSnapshot(admin);
        if (userId == admin.getUserId()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        tx.run(connection -> {
            requireAdmin(connection, admin.getUserId());
            User target = users.findById(connection, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_ID));
            if (target.getRole() == Role.ADMIN) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
            requireUpdated(users.updateStatus(connection, userId, enable ? 0 : 1));
            logs.insert(connection, new OperationLog(admin.getUserId(), "ADMIN_TOGGLE_USER",
                    (enable ? "启用" : "禁用") + "用户编号=" + userId));
            return null;
        });
    }

    @Override
    public List<OperationLog> recentLogs(int limit) throws BusinessException {
        if (limit < 1 || limit > 1000) {
            throw new ValidationException("limit", ErrorCode.VALIDATION_ERROR);
        }
        return logs.findRecent(limit);
    }

    private static void requireAdminSnapshot(SessionUser admin) {
        if (admin == null || !admin.isAdmin()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }

    private void requireAdmin(Connection connection, int adminId) {
        User current = users.findById(connection, adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED));
        if (current.getRole() != Role.ADMIN || !current.isEnabled()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw new com.campus.recycle.exception.DataAccessException(ErrorCode.DB_ERROR.getMessage(),
                    new IllegalStateException("预期更新 1 行，实际 " + rows + " 行"));
        }
    }
}
