package com.campus.recycle.service;

import com.campus.recycle.dao.impl.*;
import com.campus.recycle.entity.*;
import com.campus.recycle.dao.FlowRecordDao;
import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.ReviewDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.Review;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.service.impl.ReviewServiceImpl;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 评价业务：资格校验、接收人信用调整与防重复。 */
class ReviewServiceTest {
    @TempDir Path directory;
    private DBUtil database;
    private UserDao users;
    private ItemDao items;
    private FlowRecordDao flows;
    private ReviewDao reviews;
    private ReviewService service;
    private int seller;
    private int buyer;
    private int outsider;
    private int flowId;
    private int itemId;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("review-service.db"));
        ServiceTestSupport.clearBusinessData(database);
        users = new UserDaoImpl(database);
        items = new ItemDaoImpl(database);
        flows = new FlowRecordDaoImpl(database);
        reviews = new ReviewDaoImpl(database);
        OperationLogDao logs = new OperationLogDaoImpl(database);
        service = new ReviewServiceImpl(database, reviews, flows, users, logs);
        seller = users.insert(new User("seller", "hash", "salt", "卖家"));
        buyer = users.insert(new User("buyer", "hash", "salt", "买家"));
        outsider = users.insert(new User("outsider", "hash", "salt", "外人"));
        Item item = new Item();
        item.setOwnerId(seller);
        item.setTitle("评价物品");
        item.setTradeType(TradeType.SALE);
        itemId = items.insert(item);
        flowId = flows.insert(new FlowRecord(itemId, seller, buyer, TradeType.SALE, "意向"));
    }

    private void deal() {
        flows.updateStatus(flowId, FlowStatus.DEAL);
    }

    @Test
    void highScoreAddsTwoToReceiverOnly() {
        deal();
        assertTrue(service.canReview(flowId, buyer));
        service.submitReview(flowId, buyer, 5, "很好");
        assertEquals(102, users.findById(seller).orElseThrow().getCreditScore());
        assertEquals(100, users.findById(buyer).orElseThrow().getCreditScore());
        assertEquals(1, reviews.findByUser(seller).size());
        assertFalse(service.canReview(flowId, buyer));
    }

    @Test
    void lowScoreSubtractsThreeAndThreeStarIsNeutral() {
        deal();
        service.submitReview(flowId, buyer, 2, "较差");
        assertEquals(97, users.findById(seller).orElseThrow().getCreditScore());

        Item secondItem = new Item();
        secondItem.setOwnerId(seller);
        secondItem.setTitle("第二次评价物品");
        secondItem.setTradeType(TradeType.SALE);
        int secondItemId = items.insert(secondItem);
        int secondFlow = flows.insert(new FlowRecord(secondItemId, seller, buyer, TradeType.SALE, "第二次"));
        flows.updateStatus(secondFlow, FlowStatus.DEAL);
        service.submitReview(secondFlow, seller, 3, "一般");
        assertEquals(100, users.findById(buyer).orElseThrow().getCreditScore());
        assertEquals(97, users.findById(seller).orElseThrow().getCreditScore());
    }

    @Test
    void rejectsReviewBeforeDealFromOutsiderAndDuplicate() {
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED,
                assertThrows(BusinessException.class, () -> service.submitReview(flowId, buyer, 5, "早了")).getCode());
        deal();
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED,
                assertThrows(BusinessException.class, () -> service.submitReview(flowId, outsider, 5, "外人")).getCode());
        service.submitReview(flowId, buyer, 5, "好评");
        assertEquals(ErrorCode.REVIEW_NOT_ALLOWED,
                assertThrows(BusinessException.class, () -> service.submitReview(flowId, buyer, 4, "重复")).getCode());
    }

    @Test
    void rejectsScoreOutOfRangeAndOverlongContent() {
        deal();
        assertThrows(BusinessException.class, () -> service.submitReview(flowId, buyer, 6, "越界"));
        assertThrows(BusinessException.class,
                () -> service.submitReview(flowId, buyer, 5, "x".repeat(201)));
    }
}
