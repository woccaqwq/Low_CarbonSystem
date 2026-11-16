package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemTest {
    @Test
    void defaultsToOnSaleAndSupportsAllowedTransitions() {
        Item item = new Item();
        assertEquals(ItemStatus.ON_SALE, item.getStatus());
        assertEquals(TradeType.EXCHANGE, item.getTradeType());
        assertEquals(0.0, item.getPrice());
        assertEquals("", item.getDescription());
        assertEquals("", item.getTags());
        item.offline();
        assertEquals(ItemStatus.OFF_SHELF, item.getStatus());
        item.publish();
        item.markFlowed();
        assertEquals(ItemStatus.FLOWED, item.getStatus());
        assertThrows(IllegalStateException.class, item::publish);
        assertThrows(IllegalStateException.class, item::offline);
        assertThrows(IllegalStateException.class, item::markFlowed);
    }

    @Test
    void preventsSellingAnOfflineItemWithoutRepublishing() {
        Item item = new Item();
        item.offline();
        assertThrows(IllegalStateException.class, item::markFlowed);
    }

    @Test
    void rejectsInvalidValuesAndAcceptsSixtyUnicodeCodePoints() {
        Item item = new Item();
        assertThrows(IllegalArgumentException.class, () -> item.setPrice(-1));
        assertThrows(IllegalArgumentException.class, () -> item.setPrice(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> item.setPrice(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> item.setTitle(" "));
        assertThrows(IllegalArgumentException.class, () -> item.setTitle("字".repeat(61)));
        item.setTitle("😀".repeat(60));
        assertEquals(60, item.getTitle().codePointCount(0, item.getTitle().length()));
        assertThrows(IllegalArgumentException.class, () -> item.setStatus(null));
        assertThrows(IllegalArgumentException.class, () -> item.setTradeType(null));
        assertThrows(IllegalArgumentException.class, () -> item.setViewCount(-1));
    }
}
