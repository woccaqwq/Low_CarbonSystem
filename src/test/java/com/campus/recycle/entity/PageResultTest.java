package com.campus.recycle.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {
    @Test
    void copiesItemsAndExposesUnmodifiableList() {
        List<String> input = new ArrayList<>(List.of("one"));
        PageResult<String> page = new PageResult<>(input, 11, 1, 10);
        input.clear();
        assertEquals(List.of("one"), page.getItems());
        assertThrows(UnsupportedOperationException.class, () -> page.getItems().clear());
        assertEquals(2, page.getTotalPages());
        assertFalse(page.isEmpty());
    }

    @Test
    void handlesEmptyResultsAndLargeTotalsWithoutOverflow() {
        assertEquals(0, new PageResult<>(List.of(), 0, 1, 10).getTotalPages());
        assertTrue(new PageResult<>(List.of(), 0, 1, 10).isEmpty());
        assertEquals(214748365, new PageResult<>(List.of(), Integer.MAX_VALUE, 1, 10).getTotalPages());
    }

    @Test
    void rejectsInvalidPaginationMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), -1, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0, 1, 9));
        assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0, 1, 101));
    }
}
