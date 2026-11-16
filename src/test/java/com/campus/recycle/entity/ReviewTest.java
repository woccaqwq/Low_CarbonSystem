package com.campus.recycle.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewTest {
    @Test
    void validatesScoreBeforePersistence() {
        Review review = new Review();
        assertThrows(IllegalArgumentException.class, review::validateScore);
        review.setScore(1);
        assertDoesNotThrow(review::validateScore);
        review.setScore(5);
        assertThrows(IllegalArgumentException.class, () -> review.setScore(0));
        assertThrows(IllegalArgumentException.class, () -> review.setScore(6));
        assertEquals(5, review.getScore());
    }

    @Test
    void acceptsTwoHundredUnicodeCodePointsAndRejectsLongerContent() {
        Review review = new Review(1, 2, 3, 5, "😀".repeat(200));
        assertEquals(200, review.getContent().codePointCount(0, review.getContent().length()));
        assertThrows(IllegalArgumentException.class, () -> review.setContent("字".repeat(201)));
        assertThrows(IllegalArgumentException.class, () -> review.setContent(null));
        review.setContent("");
        assertEquals("", review.getContent());
    }

    @Test
    void rejectsSelfReviewAndInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new Review(1, 2, 2, 5, ""));
        assertThrows(IllegalArgumentException.class, () -> new Review(0, 2, 3, 5, ""));
        Review review = new Review(1, 2, 3, 5, "");
        assertThrows(IllegalArgumentException.class, () -> review.setFromUserId(3));
        assertThrows(IllegalArgumentException.class, () -> review.setToUserId(-1));
        assertThrows(IllegalArgumentException.class, () -> review.setReviewId(-1));
    }

    @Test
    void defaultsContentAndCreationTimeWithoutInventingValidScore() {
        Review review = new Review();
        assertEquals("", review.getContent());
        assertNull(review.getCreatedAt());
        assertNull(review.getItemTitle());
        assertEquals(0, review.getReviewId());
    }
}
