package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.ReviewDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.Review;
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

class ReviewDaoImplTest {
    @TempDir Path directory;
    private DBUtil database;
    private ReviewDao reviews;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("reviews.db"));
        reviews = new ReviewDaoImpl(database);
    }

    @Test
    void mapsSeedReviewFieldsAndCurrentProjection() {
        Review review = reviews.findById(1).orElseThrow();
        assertEquals(1, review.getFlowId());
        assertEquals(2, review.getFromUserId());
        assertEquals(3, review.getToUserId());
        assertEquals(5, review.getScore());
        assertTrue(review.getContent().contains("推荐"));
        assertNotNull(review.getCreatedAt());
        assertEquals("徐同学", review.getFromNickname());
        assertEquals("李同学", review.getToNickname());
        assertTrue(review.getItemTitle().contains("水壶"));
        assertTrue(reviews.findById(9999).isEmpty());
    }

    @Test
    void queriesReceivedReviewsNotAuthoredReviews() {
        assertEquals(List.of(2), ids(reviews.findByUser(2)));
        assertEquals(List.of(1), ids(reviews.findByUser(3)));
        assertEquals(4.0, reviews.avgScoreOf(2));
        assertEquals(5.0, reviews.avgScoreOf(3));
        assertEquals(0.0, reviews.avgScoreOf(1));
        assertTrue(reviews.findByUser(9999).isEmpty());
        assertEquals(0.0, reviews.avgScoreOf(9999));
        assertTrue(reviews.existsByFlowAndFrom(1, 2));
        assertFalse(reviews.existsByFlowAndFrom(2, 2));
    }

    @Test
    void insertsAndPersistsExactContentAndSecondPrecisionTime() {
        int flow = createDeal(3);
        Review input = new Review(flow, 2, 3, 5, "评价 '); DROP TABLE review;-- 中文");
        input.setCreatedAt(LocalDateTime.of(2026, 11, 16, 12, 30, 45, 123));
        int id = reviews.insert(input);
        assertTrue(id > 2);
        assertEquals(0, input.getReviewId());
        Review saved = new ReviewDaoImpl(DBUtil.forPath(directory.resolve("reviews.db")))
                .findById(id).orElseThrow();
        assertEquals(input.getContent(), saved.getContent());
        assertEquals(5, saved.getScore());
        assertEquals(flow, saved.getFlowId());
        assertEquals(LocalDateTime.of(2026, 11, 16, 12, 30, 45), saved.getCreatedAt());
        assertEquals(2, reviews.findByUser(3).size());
    }

    @Test
    void roundTripsMaximumUnicodeContentThroughJdbc() {
        int flow = createDeal(3);
        int id = reviews.insert(new Review(flow, 2, 3, 5, "😀".repeat(200)));
        String saved = reviews.findById(id).orElseThrow().getContent();
        assertEquals("😀".repeat(200), saved);
        assertEquals(200, saved.codePointCount(0, saved.length()));
    }

    @Test
    void usesDefaultCreationTimeAndDoesNotAutomaticallyModifyCredit() {
        int flow = createDeal(3);
        Review input = new Review(flow, 2, 3, 5, "");
        int id = reviews.insert(input);
        assertNotNull(reviews.findById(id).orElseThrow().getCreatedAt());
        assertNull(input.getCreatedAt());
        assertEquals(104, new UserDaoImpl(database).findById(3).orElseThrow().getCreditScore());
    }

    @Test
    void allowsBothPartiesOnceButRejectsDuplicateAuthorInSameFlow() {
        int flow = createDeal(3);
        int first = reviews.insert(new Review(flow, 2, 3, 5, "买家评价"));
        int second = reviews.insert(new Review(flow, 3, 2, 4, "卖家评价"));
        assertNotEquals(first, second);
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> reviews.insert(new Review(flow, 2, 3, 1, "重复")));
        assertInstanceOf(java.sql.SQLException.class, failure.getCause());
        assertTrue(reviews.existsByFlowAndFrom(flow, 2));
        assertTrue(reviews.existsByFlowAndFrom(flow, 3));
        assertEquals(2, reviews.findByUser(2).size());
        assertEquals(2, reviews.findByUser(3).size());
        assertEquals(5, reviews.findById(first).orElseThrow().getScore());
    }

    @Test
    void computesFractionalAverageOnlyForRecipient() {
        int flow = createDeal(3);
        reviews.insert(new Review(flow, 3, 2, 1, "低分"));
        assertEquals(2.5, reviews.avgScoreOf(2), 0.000001);
        assertEquals(5.0, reviews.avgScoreOf(3));
        assertEquals(0.0, reviews.avgScoreOf(1));
    }

    @Test
    void paginatesTiedTimesDeterministicallyAndHandlesHugeOffset() {
        for (int index = 0; index < 12; index++) {
            Review input = new Review(createDeal(1), 2, 1, 3, "分页" + index);
            input.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            reviews.insert(input);
        }
        PageResult<Review> first = reviews.findByUser(1, 1, 10);
        PageResult<Review> second = reviews.findByUser(1, 2, 10);
        assertEquals(12, first.getTotal());
        assertEquals(2, first.getTotalPages());
        assertEquals(10, first.getItems().size());
        assertEquals(14, first.getItems().get(0).getReviewId());
        assertEquals(List.of(4, 3), ids(second.getItems()));
        PageResult<Review> far = reviews.findByUser(1, Integer.MAX_VALUE, 100);
        assertEquals(12, far.getTotal());
        assertTrue(far.isEmpty());
    }

    @Test
    void keepsOriginalEvaluationWhenItemTitleChanges() {
        int flow = createDeal(3);
        int id = reviews.insert(new Review(flow, 2, 3, 5, "当时的评价"));
        int itemId = new FlowRecordDaoImpl(database).findById(flow).orElseThrow().getItemId();
        Item item = new ItemDaoImpl(database).findById(itemId).orElseThrow();
        item.setTitle("当前新标题");
        new ItemDaoImpl(database).update(item);
        Review saved = reviews.findById(id).orElseThrow();
        assertEquals("当前新标题", saved.getItemTitle());
        assertEquals("当时的评价", saved.getContent());
        assertEquals(5, saved.getScore());
    }

    @Test
    void rejectsBadForeignKeysIncompleteRecordsAndInvalidPagination() {
        assertThrows(DataAccessException.class, () -> reviews.insert(new Review(9999, 2, 3, 5, "")));
        int flow = createDeal(3);
        assertThrows(DataAccessException.class, () -> reviews.insert(new Review(flow, 9999, 3, 5, "")));
        assertThrows(DataAccessException.class, () -> reviews.insert(new Review(flow, 2, 9999, 5, "")));
        assertThrows(IllegalArgumentException.class, () -> reviews.insert(new Review()));
        assertThrows(IllegalArgumentException.class, () -> reviews.insert(null));
        assertThrows(IllegalArgumentException.class, () -> reviews.existsByFlowAndFrom(0, 2));
        assertThrows(IllegalArgumentException.class, () -> reviews.findByUser(-1));
        assertThrows(IllegalArgumentException.class, () -> reviews.avgScoreOf(0));
        assertThrows(IllegalArgumentException.class, () -> reviews.findByUser(3, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> reviews.findByUser(3, 1, 9));
        assertThrows(IllegalArgumentException.class, () -> reviews.findByUser(3, 1, 101));
    }

    @Test
    void commitsReviewAndRecipientCreditOnlyAtCallerCommit() throws Exception {
        int flow = createDeal(3);
        UserDaoImpl users = new UserDaoImpl(database);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            int id = reviews.insert(connection, new Review(flow, 2, 3, 5, "事务评价"));
            assertEquals(1, users.updateCreditScore(connection, 3, 2));
            assertTrue(reviews.findById(connection, id).isPresent());
            assertTrue(reviews.existsByFlowAndFrom(connection, flow, 2));
            assertFalse(reviews.existsByFlowAndFrom(flow, 2));
            assertEquals(2, reviews.findByUser(connection, 3).size());
            assertEquals(2, reviews.findByUser(connection, 3, 1, 10).getTotal());
            assertEquals(5.0, reviews.avgScoreOf(connection, 3));
            assertEquals(104, users.findById(3).orElseThrow().getCreditScore());
            assertFalse(connection.isClosed());
            assertFalse(connection.getAutoCommit());
            connection.commit();
        }
        assertTrue(reviews.existsByFlowAndFrom(flow, 2));
        assertEquals(106, users.findById(3).orElseThrow().getCreditScore());
        assertEquals(112, users.findById(2).orElseThrow().getCreditScore());
    }

    @Test
    void rollsBackInsertedReviewWhenCreditWriteFails() throws Exception {
        int flow = createDeal(3);
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TEMP TRIGGER reject_credit BEFORE UPDATE OF credit_score ON sys_user "
                    + "WHEN NEW.user_id=3 BEGIN SELECT RAISE(ABORT,'test credit failure'); END");
            connection.setAutoCommit(false);
            reviews.insert(connection, new Review(flow, 2, 3, 5, "需要回滚"));
            assertThrows(DataAccessException.class,
                    () -> new UserDaoImpl(database).updateCreditScore(connection, 3, 2));
            assertTrue(reviews.existsByFlowAndFrom(connection, flow, 2));
            connection.rollback();
        }
        assertFalse(reviews.existsByFlowAndFrom(flow, 2));
        assertEquals(104, new UserDaoImpl(database).findById(3).orElseThrow().getCreditScore());
    }

    @Test
    void rollsBackCreditAndNewReviewAfterDuplicateFailure() throws Exception {
        int flow = createDeal(3);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            reviews.insert(connection, new Review(flow, 2, 3, 5, "初次"));
            new UserDaoImpl(database).updateCreditScore(connection, 3, 2);
            assertThrows(DataAccessException.class,
                    () -> reviews.insert(connection, new Review(flow, 2, 3, 1, "重复")));
            assertEquals(106, new UserDaoImpl(database).findById(connection, 3).orElseThrow().getCreditScore());
            connection.rollback();
        }
        assertFalse(reviews.existsByFlowAndFrom(flow, 2));
        assertEquals(104, new UserDaoImpl(database).findById(3).orElseThrow().getCreditScore());
        assertEquals(1, reviews.findByUser(3).size());
    }

    @Test
    void cascadesReviewsWhenFlowRecordIsDeleted() throws Exception {
        assertTrue(reviews.existsByFlowAndFrom(1, 2));
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM flow_record WHERE flow_id=1");
        }
        assertTrue(new FlowRecordDaoImpl(database).findById(1).isEmpty());
        assertTrue(reviews.findById(1).isEmpty());
        assertTrue(reviews.findById(2).isEmpty());
        assertEquals(0.0, reviews.avgScoreOf(2));
        assertFalse(reviews.existsByFlowAndFrom(1, 2));
    }

    @Test
    void reportsClosedExternalConnectionForAllOperations() throws Exception {
        Connection connection = database.getConnection();
        connection.close();
        assertThrows(DataAccessException.class, () -> reviews.findById(connection, 1));
        assertThrows(DataAccessException.class, () -> reviews.insert(connection, new Review(1, 2, 3, 5, "")));
        assertThrows(DataAccessException.class, () -> reviews.existsByFlowAndFrom(connection, 1, 2));
        assertThrows(DataAccessException.class, () -> reviews.findByUser(connection, 3));
        assertThrows(DataAccessException.class, () -> reviews.findByUser(connection, 3, 1, 10));
        assertThrows(DataAccessException.class, () -> reviews.avgScoreOf(connection, 3));
    }

    @Test
    void factoryExposesStableReviewDao() {
        assertSame(DaoFactory.getReviewDao(), DaoFactory.getReviewDao());
        assertInstanceOf(ReviewDao.class, DaoFactory.getReviewDao());
    }

    private int createDeal(int buyerId) {
        Item item = new Item();
        item.setOwnerId(2);
        item.setTitle("评价测试物品");
        int itemId = new ItemDaoImpl(database).insert(item);
        FlowRecordDaoImpl flows = new FlowRecordDaoImpl(database);
        int flowId = flows.insert(new FlowRecord(itemId, 2, buyerId, TradeType.EXCHANGE, ""));
        assertEquals(1, flows.updateStatus(flowId, FlowStatus.DEAL));
        new ItemDaoImpl(database).updateStatus(itemId, ItemStatus.FLOWED);
        return flowId;
    }
    private static List<Integer> ids(List<Review> reviews) {
        return reviews.stream().map(Review::getReviewId).toList();
    }
}
