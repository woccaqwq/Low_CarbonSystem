package com.campus.recycle.service.impl;

import com.campus.recycle.dao.FlowRecordDao;
import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.exception.ValidationException;
import com.campus.recycle.service.FlowService;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;

/**
 * 流转业务实现：意向发起与成交/取消事务。
 * 成交在同一事务内完成流转状态、物品状态、其余意向、双方信用与双方日志；
 * 必需单行更新必须命中 1 行，取消其他意向允许 0 行。
 */
public final class FlowServiceImpl implements FlowService {
    private final DBUtil database;
    private final FlowRecordDao flows;
    private final ItemDao items;
    private final UserDao users;
    private final OperationLogDao logs;
    private final TxSupport tx;

    /** 使用默认连接工厂与 DAO 构造。 */
    public FlowServiceImpl() {
        this(DBUtil.getInstance(), DaoFactory.getFlowRecordDao(), DaoFactory.getItemDao(),
                DaoFactory.getUserDao(), DaoFactory.getOperationLogDao());
    }

    /**
     * 注入依赖，便于测试使用独立数据库。
     */
    public FlowServiceImpl(DBUtil database, FlowRecordDao flows, ItemDao items,
                           UserDao users, OperationLogDao logs) {
        this.database = Objects.requireNonNull(database, "database");
        this.flows = Objects.requireNonNull(flows, "flows");
        this.items = Objects.requireNonNull(items, "items");
        this.users = Objects.requireNonNull(users, "users");
        this.logs = Objects.requireNonNull(logs, "logs");
        this.tx = new TxSupport(database);
    }

    @Override
    public int requestFlow(int itemId, int buyerId, String remark) throws BusinessException {
        requirePositive(itemId, "itemId");
        requirePositive(buyerId, "buyerId");
        if (remark != null && remark.codePointCount(0, remark.length()) > 500) {
            throw new ValidationException("remark", ErrorCode.VALIDATION_ERROR);
        }
        return tx.call(connection -> {
            Item item = items.findById(connection, itemId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
            if (item.getStatus() != ItemStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.ITEM_NOT_ON_SALE);
            }
            if (item.getOwnerId() == buyerId) {
                throw new BusinessException(ErrorCode.ITEM_OWN_SELF);
            }
            if (flows.existsPending(connection, itemId, buyerId)) {
                throw new BusinessException(ErrorCode.FLOW_DUPLICATED);
            }
            FlowRecord record = new FlowRecord(itemId, item.getOwnerId(), buyerId,
                    item.getTradeType(), remark == null ? "" : remark);
            return flows.insert(connection, record);
        });
    }

    @Override
    public void confirmDeal(int flowId, int operatorId) throws BusinessException {
        requirePositive(flowId, "flowId");
        requirePositive(operatorId, "operatorId");
        tx.run(connection -> {
            FlowRecord record = flows.findById(connection, flowId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FLOW_NOT_FOUND));
            if (record.getSellerId() != operatorId) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
            if (!record.getStatus().canTransferTo(FlowStatus.DEAL)) {
                throw new BusinessException(ErrorCode.FLOW_STATUS_ILLEGAL);
            }
            Item item = items.findById(connection, record.getItemId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
            if (item.getStatus() != ItemStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.ITEM_NOT_ON_SALE);
            }
            requireUpdated(flows.updateStatus(connection, flowId, FlowStatus.DEAL));
            requireUpdated(items.updateStatus(connection, record.getItemId(), ItemStatus.FLOWED));
            // 0 表示无其他待确认意向，属合法结果。
            flows.cancelOtherPending(connection, record.getItemId(), flowId);
            requireUpdated(users.updateCreditScore(connection, record.getSellerId(), 1));
            requireUpdated(users.updateCreditScore(connection, record.getBuyerId(), 1));
            logs.insert(connection, new OperationLog(record.getSellerId(), "FLOW_CONFIRM",
                    "成交流转编号=" + flowId + "（卖家）"));
            logs.insert(connection, new OperationLog(record.getBuyerId(), "FLOW_CONFIRM",
                    "成交流转编号=" + flowId + "（买家）"));
            return null;
        });
    }

    @Override
    public void cancelFlow(int flowId, int operatorId, String reason) throws BusinessException {
        requirePositive(flowId, "flowId");
        requirePositive(operatorId, "operatorId");
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason", ErrorCode.VALIDATION_ERROR);
        }
        if (reason.codePointCount(0, reason.length()) > 200) {
            throw new ValidationException("reason", ErrorCode.VALIDATION_ERROR);
        }
        tx.run(connection -> {
            FlowRecord record = flows.findById(connection, flowId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FLOW_NOT_FOUND));
            if (record.getSellerId() != operatorId && record.getBuyerId() != operatorId) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
            if (!record.getStatus().canTransferTo(FlowStatus.CANCELLED)) {
                throw new BusinessException(ErrorCode.FLOW_STATUS_ILLEGAL);
            }
            requireUpdated(flows.cancel(connection, flowId, reason));
            logs.insert(connection, new OperationLog(operatorId, "FLOW_CANCEL", "取消流转编号=" + flowId));
            return null;
        });
    }

    @Override
    public List<FlowRecord> myFlows(int userId, FlowStatus status) {
        if (userId <= 0) { return List.of(); }
        return flows.findByUser(userId, status);
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw new com.campus.recycle.exception.DataAccessException(ErrorCode.DB_ERROR.getMessage(),
                    new IllegalStateException("成交/取消必需更新命中 " + rows + " 行，应为 1 行"));
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) { throw new ValidationException(field, ErrorCode.VALIDATION_ERROR); }
    }
}
