package com.campus.recycle.service;

import com.campus.recycle.dao.impl.*;
import com.campus.recycle.entity.*;
import com.campus.recycle.dao.FlowRecordDao;
import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.service.impl.FlowServiceImpl;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 流转业务：意向校验、成交事务一致性、双方信用与日志、取消。 */
class FlowServiceTest {
    @TempDir Path directory;
    private DBUtil database;
    private UserDao users;
    private ItemDao items;
    private FlowRecordDao flows;
    private com.campus.recycle.dao.OperationLogDao logs;
    private FlowService service;
    private int seller;
    private int buyer;
    private int other;
    private int itemId;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("flow-service.db"));
        ServiceTestSupport.clearBusinessData(database);
        users = new UserDaoImpl(database);
        items = new ItemDaoImpl(database);
        flows = new FlowRecordDaoImpl(database);
        logs = new OperationLogDaoImpl(database);
        service = new FlowServiceImpl(database, flows, items, users, logs);
        seller = users.insert(new User("seller", "hash", "salt", "卖家"));
        buyer = users.insert(new User("buyer", "hash", "salt", "买家"));
        other = users.insert(new User("other", "hash", "salt", "其他"));
        Item item = new Item();
        item.setOwnerId(seller);
        item.setTitle("待流转物品");
        item.setTradeType(TradeType.SALE);
        item.setPrice(30);
        itemId = items.insert(item);
    }

    @Test
    void requestsFlowWithValidation() {
        int flowId = service.requestFlow(itemId, buyer, "想要");
        assertEquals(FlowStatus.PENDING, flows.findById(flowId).orElseThrow().getStatus());
        assertEquals(seller, flows.findById(flowId).orElseThrow().getSellerId());

        assertEquals(ErrorCode.ITEM_OWN_SELF,
                assertThrows(BusinessException.class, () -> service.requestFlow(itemId, seller, "")).getCode());
        assertEquals(ErrorCode.FLOW_DUPLICATED,
                assertThrows(BusinessException.class, () -> service.requestFlow(itemId, buyer, "再次")).getCode());
    }

    @Test
    void rejectRequestOnNonOnSaleItem() {
        items.updateStatus(itemId, ItemStatus.OFF_SHELF);
        assertEquals(ErrorCode.ITEM_NOT_ON_SALE,
                assertThrows(BusinessException.class, () -> service.requestFlow(itemId, buyer, "")).getCode());
    }

    @Test
    void confirmDealCompletesAllWritesInOneTransaction() {
        int winner = service.requestFlow(itemId, buyer, "买家意向");
        int loser = service.requestFlow(itemId, other, "其他意向");
        service.confirmDeal(winner, seller);

        assertEquals(FlowStatus.DEAL, flows.findById(winner).orElseThrow().getStatus());
        assertEquals(FlowStatus.CANCELLED, flows.findById(loser).orElseThrow().getStatus());
        assertEquals(ItemStatus.FLOWED, items.findById(itemId).orElseThrow().getStatus());
        assertEquals(101, users.findById(seller).orElseThrow().getCreditScore());
        assertEquals(101, users.findById(buyer).orElseThrow().getCreditScore());
        assertEquals(100, users.findById(other).orElseThrow().getCreditScore());
        assertEquals(2, logs.findRecent(20).stream()
                .filter(log -> "FLOW_CONFIRM".equals(log.getAction())).count());
        assertEquals(2, service.myFlows(buyer, FlowStatus.DEAL).size()
                + service.myFlows(seller, FlowStatus.DEAL).size());
    }

    @Test
    void onlySellerCanConfirmAndTerminalFlowIsRejected() {
        int flowId = service.requestFlow(itemId, buyer, "意向");
        assertEquals(ErrorCode.PERMISSION_DENIED,
                assertThrows(BusinessException.class, () -> service.confirmDeal(flowId, buyer)).getCode());
        service.confirmDeal(flowId, seller);
        assertEquals(ErrorCode.FLOW_STATUS_ILLEGAL,
                assertThrows(BusinessException.class, () -> service.confirmDeal(flowId, seller)).getCode());
    }

    @Test
    void cancelFlowByParticipantOnly() {
        int flowId = service.requestFlow(itemId, buyer, "意向");
        assertEquals(ErrorCode.PERMISSION_DENIED,
                assertThrows(BusinessException.class, () -> service.cancelFlow(flowId, other, "原因")).getCode());
        assertEquals(ErrorCode.VALIDATION_ERROR,
                assertThrows(BusinessException.class, () -> service.cancelFlow(flowId, buyer, "  ")).getCode());
        service.cancelFlow(flowId, buyer, "改变主意");
        FlowRecord record = flows.findById(flowId).orElseThrow();
        assertEquals(FlowStatus.CANCELLED, record.getStatus());
        assertTrue(record.getRemark().contains("改变主意"));
    }

    @Test
    void confirmDealIsAtomicWhenDownstreamWriteFails() {
        int firstUser = users.insert(new User("atomic_a", "hash", "salt", "A"));
        int secondUser = users.insert(new User("atomic_b", "hash", "salt", "B"));
        Item target = new Item();
        target.setOwnerId(firstUser);
        target.setTitle("失败场景物品");
        target.setTradeType(TradeType.EXCHANGE);
        int targetId = items.insert(target);
        int flowId = flows.insert(new FlowRecord(targetId, firstUser, secondUser, TradeType.EXCHANGE, "意向"));
        int invalidLogActor = Integer.MAX_VALUE;

        // 用不存在的操作人写入末端日志触发外键错误，Service 应整体回滚。
        FlowService failing = new FlowServiceImpl(database, flows, items, users,
                new FailingLogDao(invalidLogActor));
        assertThrows(RuntimeException.class, () -> failing.confirmDeal(flowId, firstUser));

        assertEquals(FlowStatus.PENDING, flows.findById(flowId).orElseThrow().getStatus());
        assertEquals(ItemStatus.ON_SALE, items.findById(targetId).orElseThrow().getStatus());
        assertEquals(100, users.findById(firstUser).orElseThrow().getCreditScore());
        assertEquals(100, users.findById(secondUser).orElseThrow().getCreditScore());
        List<FlowRecord> pending = service.myFlows(secondUser, FlowStatus.PENDING);
        assertTrue(pending.stream().anyMatch(row -> row.getFlowId() == flowId));
    }

    /** 末端日志写入使用固定无效用户编号，模拟事务内失败。 */
    private static final class FailingLogDao implements com.campus.recycle.dao.OperationLogDao {
        private final int forcedActor;
        FailingLogDao(int forcedActor) { this.forcedActor = forcedActor; }
        @Override public int insert(com.campus.recycle.entity.OperationLog log) { return 1; }
        @Override public java.util.List<com.campus.recycle.entity.OperationLog> findRecent(int limit) {
            return java.util.List.of();
        }
        @Override public java.util.Optional<com.campus.recycle.entity.OperationLog> findById(int logId) {
            return java.util.Optional.empty();
        }
        @Override public com.campus.recycle.entity.PageResult<com.campus.recycle.entity.OperationLog> findByPage(
                Integer userId, String action, int pageNo, int pageSize) { return null; }
        @Override public int insert(java.sql.Connection connection, com.campus.recycle.entity.OperationLog log) {
            return new OperationLogDaoImpl().insert(connection,
                    new com.campus.recycle.entity.OperationLog(forcedActor, log.getAction(), log.getDetail()));
        }
        @Override public java.util.List<com.campus.recycle.entity.OperationLog> findRecent(
                java.sql.Connection connection, int limit) { return java.util.List.of(); }
        @Override public java.util.Optional<com.campus.recycle.entity.OperationLog> findById(
                java.sql.Connection connection, int logId) { return java.util.Optional.empty(); }
        @Override public com.campus.recycle.entity.PageResult<com.campus.recycle.entity.OperationLog> findByPage(
                java.sql.Connection connection, Integer userId, String action, int pageNo, int pageSize) { return null; }
    }
}
