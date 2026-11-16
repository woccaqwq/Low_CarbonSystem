package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.FlowRecordDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowRecordDaoImplTest {
    @TempDir Path directory;
    private DBUtil database;
    private FlowRecordDao flows;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("flows.db"));
        flows = new FlowRecordDaoImpl(database);
    }

    @Test
    void mapsSeedFieldsDatesAndJoinedNames() {
        FlowRecord completed = flows.findById(1).orElseThrow();
        assertEquals(4, completed.getItemId());
        assertEquals(3, completed.getSellerId());
        assertEquals(2, completed.getBuyerId());
        assertEquals(TradeType.EXCHANGE, completed.getFlowType());
        assertEquals(FlowStatus.DEAL, completed.getStatus());
        assertTrue(completed.getRemark().contains("风扇"));
        assertNotNull(completed.getCreatedAt());
        assertNotNull(completed.getFinishedAt());
        assertTrue(completed.getItemTitle().contains("水壶"));
        assertEquals("李同学", completed.getSellerNickname());
        assertEquals("徐同学", completed.getBuyerNickname());
        assertNull(flows.findById(2).orElseThrow().getFinishedAt());
        assertTrue(flows.findById(9999).isEmpty());
    }

    @Test
    void insertsAndPersistsPendingIntentWithoutMutatingInput() {
        FlowRecord input = record(2, 2, 3);
        input.setRemark("中文留言 '); DROP TABLE flow_record;--");
        input.setCreatedAt(LocalDateTime.of(2026, 11, 1, 12, 30, 45, 123));
        int id = flows.insert(input);
        assertTrue(id > 3);
        assertEquals(0, input.getFlowId());
        FlowRecord saved = new FlowRecordDaoImpl(DBUtil.forPath(directory.resolve("flows.db")))
                .findById(id).orElseThrow();
        assertEquals(input.getRemark(), saved.getRemark());
        assertEquals(FlowStatus.PENDING, saved.getStatus());
        assertEquals(TradeType.SALE, saved.getFlowType());
        assertEquals(LocalDateTime.of(2026, 11, 1, 12, 30, 45), saved.getCreatedAt());
        assertNull(saved.getFinishedAt());
        assertEquals(4, flows.findByUser(3, null).size());
    }

    @Test
    void rejectsDuplicatePendingButAllowsNewIntentAfterCancellation() {
        assertTrue(flows.existsPending(1, 3));
        assertFalse(flows.existsPending(2, 3));
        assertThrows(DataAccessException.class, () -> flows.insert(record(1, 2, 3)));
        assertEquals(1, flows.updateStatus(2, FlowStatus.CANCELLED));
        assertFalse(flows.existsPending(1, 3));
        int id = flows.insert(record(1, 2, 3));
        assertTrue(id > 3);
        assertTrue(flows.existsPending(1, 3));
    }

    @Test
    void updatesOnlyPendingRecordsAndPreservesTerminalTimestamp() {
        assertEquals(1, flows.updateStatus(2, FlowStatus.DEAL));
        FlowRecord saved = flows.findById(2).orElseThrow();
        assertEquals(FlowStatus.DEAL, saved.getStatus());
        assertNotNull(saved.getFinishedAt());
        assertEquals(0, flows.updateStatus(2, FlowStatus.DEAL));
        assertEquals(0, flows.updateStatus(2, FlowStatus.CANCELLED));
        assertEquals(saved.getFinishedAt(), flows.findById(2).orElseThrow().getFinishedAt());
        assertEquals(0, flows.updateStatus(9999, FlowStatus.DEAL));
        assertEquals(0, flows.updateStatus(1, FlowStatus.CANCELLED));
        assertThrows(IllegalArgumentException.class, () -> flows.updateStatus(2, FlowStatus.PENDING));
    }

    @Test
    void allowsOnlyOneDealForTheSameItem() {
        int contender = flows.insert(record(1, 2, 1));
        assertEquals(1, flows.updateStatus(2, FlowStatus.DEAL));
        assertEquals(0, flows.updateStatus(contender, FlowStatus.DEAL));
        assertEquals(FlowStatus.PENDING, flows.findById(contender).orElseThrow().getStatus());
        assertEquals(1, flows.updateStatus(contender, FlowStatus.CANCELLED));
    }

    @Test
    void cancelsOnlyOtherPendingRecordsOfConfirmedItem() {
        int contender = flows.insert(record(1, 2, 1));
        assertEquals(0, flows.cancelOtherPending(1, 2), "保留记录尚未成交时不能取消其他意向");
        assertEquals(1, flows.updateStatus(2, FlowStatus.DEAL));
        assertEquals(0, flows.cancelOtherPending(3, 2), "不能误取消另一个存在待确认意向的物品");
        assertEquals(FlowStatus.PENDING, flows.findById(3).orElseThrow().getStatus());
        assertEquals(0, flows.cancelOtherPending(1, 9999));
        assertEquals(1, flows.cancelOtherPending(1, 2));
        assertEquals(0, flows.cancelOtherPending(1, 2));
        FlowRecord cancelled = flows.findById(contender).orElseThrow();
        assertEquals(FlowStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getFinishedAt());
        assertEquals(FlowStatus.DEAL, flows.findById(2).orElseThrow().getStatus());
        assertEquals(FlowStatus.PENDING, flows.findById(3).orElseThrow().getStatus());
        assertEquals(FlowStatus.DEAL, flows.findById(1).orElseThrow().getStatus());
    }

    @Test
    void appendsCancellationReasonAtomicallyAndNeverRepeatsIt() {
        String original = flows.findById(2).orElseThrow().getRemark();
        String reason = "计划变化 '); DELETE FROM flow_record;--";
        assertEquals(1, flows.cancel(2, reason));
        FlowRecord cancelled = flows.findById(2).orElseThrow();
        assertEquals(FlowStatus.CANCELLED, cancelled.getStatus());
        assertEquals(original + "\n取消原因：" + reason, cancelled.getRemark());
        assertNotNull(cancelled.getFinishedAt());
        assertEquals(0, flows.cancel(2, "重复原因"));
        assertEquals(cancelled.getRemark(), flows.findById(2).orElseThrow().getRemark());
        assertEquals(cancelled.getFinishedAt(), flows.findById(2).orElseThrow().getFinishedAt());
        assertEquals(3, flows.findByUser(3, null).size());
        assertEquals(0, flows.cancel(9999, "不存在"));
    }

    @Test
    void cancelsEmptyRemarkWithoutLeadingNewline() {
        int id = flows.insert(record(2, 2, 3));
        assertEquals(1, flows.cancel(id, "不再需要"));
        assertEquals("取消原因：不再需要", flows.findById(id).orElseThrow().getRemark());
    }

    @Test
    void filtersStatusAcrossBothBuyerAndSellerAndSeparatesDirections() {
        assertEquals(List.of(3, 2, 1), ids(flows.findByUser(2, null)));
        assertEquals(List.of(3, 2), ids(flows.findByUser(3, FlowStatus.PENDING)));
        assertEquals(List.of(1), ids(flows.findByUser(2, FlowStatus.DEAL)));
        assertTrue(flows.findByUser(1, null).isEmpty());
        assertEquals(List.of(3, 1), ids(flows.findByBuyer(2, null, 1, 10).getItems()));
        assertEquals(List.of(2), ids(flows.findBySeller(2, null, 1, 10).getItems()));
        assertEquals(List.of(3), ids(flows.findBySeller(3, FlowStatus.PENDING, 1, 10).getItems()));
        assertTrue(flows.findByUser(9999, null, 1, 10).isEmpty());
    }

    @Test
    void paginatesTiedDatesAndHandlesExtremeOffset() throws Exception {
        ItemDaoImpl items = new ItemDaoImpl(database);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (int index = 0; index < 12; index++) {
                Item item = new Item();
                item.setOwnerId(2);
                item.setTitle("流转分页" + index);
                FlowRecord input = record(items.insert(connection, item), 2, 3);
                input.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                int id = flows.insert(connection, input);
                flows.updateStatus(connection, id, FlowStatus.CANCELLED);
            }
            connection.commit();
        }
        PageResult<FlowRecord> first = flows.findByUser(3, FlowStatus.CANCELLED, 1, 10);
        PageResult<FlowRecord> second = flows.findByUser(3, FlowStatus.CANCELLED, 2, 10);
        assertEquals(12, first.getTotal());
        assertEquals(2, first.getTotalPages());
        assertEquals(10, first.getItems().size());
        assertEquals(15, first.getItems().get(0).getFlowId());
        assertEquals(List.of(5, 4), ids(second.getItems()));
        assertEquals(12, flows.findByBuyer(3, FlowStatus.CANCELLED, 1, 10).getTotal());
        assertEquals(12, flows.findBySeller(2, FlowStatus.CANCELLED, 1, 10).getTotal());
        PageResult<FlowRecord> far = flows.findByUser(3, FlowStatus.CANCELLED, Integer.MAX_VALUE, 100);
        assertEquals(12, far.getTotal());
        assertTrue(far.isEmpty());
    }

    @Test
    void rejectsInvalidInputsAndPropagatesForeignKeyErrors() {
        assertThrows(DataAccessException.class, () -> flows.insert(record(9999, 2, 3)));
        assertThrows(DataAccessException.class, () -> flows.insert(record(2, 9999, 3)));
        assertThrows(DataAccessException.class, () -> flows.insert(record(2, 2, 9999)));
        assertThrows(IllegalArgumentException.class, () -> flows.insert(null));
        assertThrows(IllegalArgumentException.class, () -> flows.insert(new FlowRecord()));
        FlowRecord terminal = record(2, 2, 3);
        terminal.confirm();
        assertThrows(IllegalArgumentException.class, () -> flows.insert(terminal));
        FlowRecord inconsistent = record(2, 2, 3);
        inconsistent.setFinishedAt(LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> flows.insert(inconsistent));
        assertThrows(IllegalArgumentException.class, () -> flows.updateStatus(2, null));
        assertThrows(IllegalArgumentException.class, () -> flows.cancel(2, " "));
        assertThrows(IllegalArgumentException.class, () -> flows.existsPending(0, 3));
        assertThrows(IllegalArgumentException.class, () -> flows.cancelOtherPending(1, 0));
        assertThrows(IllegalArgumentException.class, () -> flows.findByUser(0, null));
        assertThrows(IllegalArgumentException.class, () -> flows.findByUser(3, null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> flows.findByBuyer(3, null, 1, 9));
        assertThrows(IllegalArgumentException.class, () -> flows.findBySeller(2, null, 1, 101));
    }

    @Test
    void commitsDealItemAndOtherCancellationsOnlyAtCallerCommit() throws Exception {
        ItemDaoImpl items = new ItemDaoImpl(database);
        int contender = flows.insert(record(1, 2, 1));
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            assertEquals(1, flows.updateStatus(connection, 2, FlowStatus.DEAL));
            assertEquals(1, items.updateStatus(connection, 1, ItemStatus.FLOWED));
            assertEquals(1, flows.cancelOtherPending(connection, 1, 2));
            assertEquals(FlowStatus.DEAL, flows.findById(connection, 2).orElseThrow().getStatus());
            assertEquals(FlowStatus.PENDING, flows.findById(2).orElseThrow().getStatus());
            assertFalse(flows.existsPending(connection, 1, 1));
            assertEquals(0, flows.findByUser(connection, 1, FlowStatus.PENDING).size());
            assertEquals(1, flows.findByUser(connection, 1, null, 1, 10).getTotal());
            assertEquals(1, flows.findByBuyer(connection, 1, null, 1, 10).getTotal());
            assertEquals(1, flows.findBySeller(connection, 2, FlowStatus.DEAL, 1, 10).getTotal());
            assertFalse(connection.getAutoCommit());
            assertFalse(connection.isClosed());
            connection.commit();
        }
        assertEquals(FlowStatus.DEAL, flows.findById(2).orElseThrow().getStatus());
        assertEquals(FlowStatus.CANCELLED, flows.findById(contender).orElseThrow().getStatus());
        assertEquals(ItemStatus.FLOWED, items.findById(1).orElseThrow().getStatus());
    }

    @Test
    void rollsBackFirstStepWhenSecondItemUpdateFails() throws Exception {
        ItemDaoImpl items = new ItemDaoImpl(database);
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TEMP TRIGGER reject_item_flow BEFORE UPDATE OF status ON item "
                    + "WHEN NEW.status='FLOWED' BEGIN SELECT RAISE(ABORT,'test failure'); END");
            connection.setAutoCommit(false);
            assertEquals(1, flows.updateStatus(connection, 2, FlowStatus.DEAL));
            assertThrows(DataAccessException.class, () -> items.updateStatus(connection, 1, ItemStatus.FLOWED));
            assertEquals(FlowStatus.DEAL, flows.findById(connection, 2).orElseThrow().getStatus());
            connection.rollback();
        }
        FlowRecord original = flows.findById(2).orElseThrow();
        assertEquals(FlowStatus.PENDING, original.getStatus());
        assertNull(original.getFinishedAt());
        assertEquals(ItemStatus.ON_SALE, items.findById(1).orElseThrow().getStatus());
    }

    @Test
    void rollsBackAllWritesIncludingOtherCancellationsAfterLaterFailure() throws Exception {
        ItemDaoImpl items = new ItemDaoImpl(database);
        int temporary;
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            temporary = flows.insert(connection, record(1, 2, 1));
            flows.updateStatus(connection, 2, FlowStatus.DEAL);
            items.updateStatus(connection, 1, ItemStatus.FLOWED);
            assertEquals(1, flows.cancelOtherPending(connection, 1, 2));
            assertThrows(DataAccessException.class, () -> flows.insert(connection, record(9999, 2, 3)));
            assertEquals(FlowStatus.CANCELLED, flows.findById(connection, temporary).orElseThrow().getStatus());
            connection.rollback();
        }
        assertTrue(flows.findById(temporary).isEmpty());
        assertEquals(FlowStatus.PENDING, flows.findById(2).orElseThrow().getStatus());
        assertNull(flows.findById(2).orElseThrow().getFinishedAt());
        assertEquals(ItemStatus.ON_SALE, items.findById(1).orElseThrow().getStatus());
    }

    @Test
    void letsCallerRollBackReasonCancellation() throws Exception {
        String original = flows.findById(2).orElseThrow().getRemark();
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            assertEquals(1, flows.cancel(connection, 2, "临时原因"));
            assertEquals(FlowStatus.CANCELLED, flows.findById(connection, 2).orElseThrow().getStatus());
            connection.rollback();
        }
        assertEquals(FlowStatus.PENDING, flows.findById(2).orElseThrow().getStatus());
        assertEquals(original, flows.findById(2).orElseThrow().getRemark());
        assertNull(flows.findById(2).orElseThrow().getFinishedAt());
    }

    @Test
    void reportsClosedExternalConnectionWithoutOpeningAnother() throws Exception {
        Connection connection = database.getConnection();
        connection.close();
        assertThrows(DataAccessException.class, () -> flows.findById(connection, 2));
        assertThrows(DataAccessException.class, () -> flows.insert(connection, record(2, 2, 3)));
        assertThrows(DataAccessException.class, () -> flows.existsPending(connection, 1, 3));
        assertThrows(DataAccessException.class, () -> flows.updateStatus(connection, 2, FlowStatus.DEAL));
        assertThrows(DataAccessException.class, () -> flows.findByUser(connection, 3, null));
        assertThrows(DataAccessException.class, () -> flows.cancelOtherPending(connection, 1, 2));
        assertThrows(DataAccessException.class, () -> flows.cancel(connection, 2, "原因"));
    }

    @Test
    void factoryExposesStableFlowDao() {
        assertSame(DaoFactory.getFlowRecordDao(), DaoFactory.getFlowRecordDao());
        assertInstanceOf(FlowRecordDao.class, DaoFactory.getFlowRecordDao());
    }

    private static FlowRecord record(int itemId, int sellerId, int buyerId) {
        return new FlowRecord(itemId, sellerId, buyerId, TradeType.SALE, "");
    }

    private static List<Integer> ids(List<FlowRecord> records) {
        return records.stream().map(FlowRecord::getFlowId).toList();
    }
}
