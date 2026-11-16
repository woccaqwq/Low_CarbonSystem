package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.strategy.CreditDescStrategy;
import com.campus.recycle.strategy.PriceAscStrategy;
import com.campus.recycle.strategy.SortStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemQueryTest {
    @Test
    void suppliesDefaultsAndFreezesBuilderState() {
        ItemQuery.Builder builder = ItemQuery.builder();
        ItemQuery initial = builder.build();
        builder.keyword("new").status(null).pageNo(2).pageSize(10);
        assertNull(initial.getKeyword());
        assertEquals(ItemStatus.ON_SALE, initial.getStatus());
        assertEquals(1, initial.getPageNo());
        assertEquals(20, initial.getPageSize());
        assertEquals("i.created_at DESC", initial.getOrderBy());
        assertNull(builder.build().getStatus());
    }

    @Test
    void combinesFiltersAndAcceptsBuiltInSortingStrategies() {
        ItemQuery query = ItemQuery.builder().keyword("教材").categoryId(1).ownerId(2)
                .minPrice(0.0).maxPrice(25.0).tradeType(TradeType.SALE)
                .status(ItemStatus.OFF_SHELF).sort(new PriceAscStrategy()).pageNo(3).pageSize(10).build();
        assertEquals(2, query.getOwnerId());
        assertEquals(1, query.getCategoryId());
        assertEquals(0.0, query.getMinPrice());
        assertEquals(25.0, query.getMaxPrice());
        assertEquals(TradeType.SALE, query.getTradeType());
        assertEquals("i.price ASC, i.created_at DESC", query.getOrderBy());
        assertEquals("u.credit_score DESC, i.created_at DESC",
                ItemQuery.builder().sort(new CreditDescStrategy()).build().getOrderBy());
    }

    @Test
    void rejectsInvalidNumbersIntervalsAndPagination() {
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().minPrice(-1.0).build());
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().minPrice(Double.NaN).build());
        assertThrows(IllegalArgumentException.class,
                () -> ItemQuery.builder().maxPrice(Double.POSITIVE_INFINITY).build());
        assertThrows(IllegalArgumentException.class,
                () -> ItemQuery.builder().minPrice(20.0).maxPrice(10.0).build());
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().categoryId(0).build());
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().ownerId(-1).build());
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().pageNo(0).build());
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().pageSize(9).build());
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().pageSize(101).build());
        assertEquals(Integer.MAX_VALUE, ItemQuery.builder().pageNo(Integer.MAX_VALUE).build().getPageNo());
    }

    @Test
    void rejectsRawSqlAndSnapshotsMutableStrategies() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemQuery.builder().sort(() -> "i.price; DROP TABLE item;--").build());
        assertThrows(IllegalArgumentException.class,
                () -> ItemQuery.builder().sort(() -> "(SELECT password_hash FROM sys_user)").build());
        assertThrows(IllegalArgumentException.class, () -> ItemQuery.builder().sort(() -> null).build());
        SortStrategy changing = new SortStrategy() {
            private int calls;
            @Override public String toOrderBy() { return calls++ == 0 ? "i.price ASC" : "malicious SQL"; }
        };
        ItemQuery safe = ItemQuery.builder().sort(changing).build();
        assertEquals("i.price ASC", safe.getOrderBy());
        assertEquals("i.price ASC", safe.getSort().toOrderBy());
    }
}
