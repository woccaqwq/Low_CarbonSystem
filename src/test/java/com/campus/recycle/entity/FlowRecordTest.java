package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.TradeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowRecordTest {
    @Test
    void defaultsToPendingAndExchange() {
        FlowRecord record = new FlowRecord();
        assertEquals(FlowStatus.PENDING, record.getStatus());
        assertEquals(TradeType.EXCHANGE, record.getFlowType());
        assertEquals("", record.getRemark());
        assertNull(record.getFinishedAt());
        assertEquals("待确认", record.getStatus().getLabel());
    }

    @Test
    void confirmsPendingRecordAndRejectsFurtherTransitions() {
        FlowRecord record = new FlowRecord(1, 2, 3, TradeType.SALE, "购买意向");
        record.confirm();
        assertEquals(FlowStatus.DEAL, record.getStatus());
        assertNotNull(record.getFinishedAt());
        assertThrows(IllegalStateException.class, record::confirm);
        assertThrows(IllegalStateException.class, () -> record.cancel("再次取消"));
        assertFalse(FlowStatus.DEAL.canTransferTo(FlowStatus.PENDING));
    }

    @Test
    void cancelsPendingRecordPreservingRemarkAndReason() {
        FlowRecord record = new FlowRecord(1, 2, 3, TradeType.SALE, "最初留言");
        record.cancel("不再需要");
        assertEquals(FlowStatus.CANCELLED, record.getStatus());
        assertEquals("最初留言\n取消原因：不再需要", record.getRemark());
        assertNotNull(record.getFinishedAt());
        assertThrows(IllegalStateException.class, record::confirm);
        assertThrows(IllegalStateException.class, () -> record.cancel("重复取消"));
        assertFalse(FlowStatus.CANCELLED.canTransferTo(FlowStatus.DEAL));
    }

    @Test
    void handlesEmptyOriginalRemarkWithoutLeadingNewline() {
        FlowRecord record = new FlowRecord(1, 2, 3, TradeType.SALE, "");
        record.cancel("无法交接");
        assertEquals("取消原因：无法交接", record.getRemark());
    }

    @Test
    void rejectsInvalidIdentifiersAndSelfTrade() {
        assertThrows(IllegalArgumentException.class, () -> new FlowRecord(0, 2, 3, TradeType.SALE, ""));
        assertThrows(IllegalArgumentException.class, () -> new FlowRecord(1, 2, 2, TradeType.SALE, ""));
        FlowRecord record = new FlowRecord(1, 2, 3, TradeType.SALE, "");
        assertThrows(IllegalArgumentException.class, () -> record.setSellerId(3));
        assertThrows(IllegalArgumentException.class, () -> record.setBuyerId(-1));
        assertThrows(IllegalArgumentException.class, () -> record.setStatus(null));
        assertThrows(IllegalArgumentException.class, () -> record.setFlowType(null));
        assertThrows(IllegalArgumentException.class, () -> record.setRemark(null));
    }

    @Test
    void rejectsBlankCancellationWithoutChangingState() {
        FlowRecord record = new FlowRecord(1, 2, 3, TradeType.SALE, "保留");
        assertThrows(IllegalArgumentException.class, () -> record.cancel(" "));
        assertEquals(FlowStatus.PENDING, record.getStatus());
        assertEquals("保留", record.getRemark());
        assertNull(record.getFinishedAt());
    }
}
