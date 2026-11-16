package com.campus.recycle.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class StatValuesTest {
    @Test
    void derivesTotalsAndKeepsLongCounts() {
        StatCounts counts = new StatCounts(3_000_000_000L, 1, 2, 4, 5, 6);
        assertEquals(3_000_000_003L, counts.getItemCount());
        assertEquals(15, counts.getFlowCount());
        assertEquals(5, counts.getDealFlowCount());
        assertThrows(IllegalArgumentException.class, () -> new StatCounts(-1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StatCounts(Long.MAX_VALUE, 1, 0, 0, 0, 0));
    }

    @Test
    void freezesQueryBuilderAndDefaultsToAllData() {
        StatQuery.Builder builder = StatQuery.builder();
        StatQuery all = builder.build();
        StatQuery scoped = builder.from(LocalDate.of(2026, 1, 1)).to(LocalDate.of(2026, 12, 31))
                .userId(2).build();
        assertNull(all.getFrom());
        assertNull(all.getTo());
        assertNull(all.getUserId());
        assertEquals(2, scoped.getUserId());
        assertEquals(LocalDate.of(2026, 1, 1), scoped.getFrom());
    }

    @Test
    void rejectsReversedDatesUnsupportedYearsAndInvalidUser() {
        assertThrows(IllegalArgumentException.class, () -> StatQuery.builder()
                .from(LocalDate.of(2026, 2, 1)).to(LocalDate.of(2026, 1, 1)).build());
        assertThrows(IllegalArgumentException.class, () -> StatQuery.builder().userId(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> StatQuery.builder().from(LocalDate.of(0, 1, 1)).build());
        assertThrows(IllegalArgumentException.class,
                () -> StatQuery.builder().to(LocalDate.of(10000, 1, 1)).build());
    }
}
