package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.FlowRecordDao;
import com.campus.recycle.entity.FlowRecord;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.enums.FlowStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 流转 JDBC 实现；状态通过条件 SQL 更新，成交事务由 Service 编排。 */
public final class FlowRecordDaoImpl extends BaseDao<FlowRecord> implements FlowRecordDao {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SELECT = "SELECT f.*,i.title AS item_title,s.nickname AS seller_nickname,"
            + "b.nickname AS buyer_nickname FROM flow_record f LEFT JOIN item i ON i.item_id=f.item_id "
            + "LEFT JOIN sys_user s ON s.user_id=f.seller_id LEFT JOIN sys_user b ON b.user_id=f.buyer_id";
    private static final String ORDER = " ORDER BY f.created_at DESC,f.flow_id DESC";

    private enum Side { BOTH, BUYER, SELLER }
    private record Filter(String where, List<Object> parameters) { }

    /** 使用默认数据库。 */
    public FlowRecordDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 独立数据库工厂 */
    public FlowRecordDaoImpl(DBUtil database) { super(database); }

    @Override
    public int insert(FlowRecord record) {
        return withConnection(connection -> insert(connection, record));
    }
    @Override
    public Optional<FlowRecord> findById(int flowId) {
        return withConnection(connection -> findById(connection, flowId));
    }
    @Override
    public int updateStatus(int flowId, FlowStatus status) {
        return withConnection(connection -> updateStatus(connection, flowId, status));
    }
    @Override
    public boolean existsPending(int itemId, int buyerId) {
        return withConnection(connection -> existsPending(connection, itemId, buyerId));
    }
    @Override
    public List<FlowRecord> findByUser(int userId, FlowStatus status) {
        return withConnection(connection -> findByUser(connection, userId, status));
    }
    @Override
    public int cancelOtherPending(int itemId, int exceptFlowId) {
        return withConnection(connection -> cancelOtherPending(connection, itemId, exceptFlowId));
    }
    @Override
    public int cancel(int flowId, String reason) {
        return withConnection(connection -> cancel(connection, flowId, reason));
    }
    @Override
    public PageResult<FlowRecord> findByUser(int userId, FlowStatus status, int pageNo, int pageSize) {
        return withConnection(connection -> findByUser(connection, userId, status, pageNo, pageSize));
    }
    @Override
    public PageResult<FlowRecord> findByBuyer(int buyerId, FlowStatus status, int pageNo, int pageSize) {
        return withConnection(connection -> findByBuyer(connection, buyerId, status, pageNo, pageSize));
    }
    @Override
    public PageResult<FlowRecord> findBySeller(int sellerId, FlowStatus status, int pageNo, int pageSize) {
        return withConnection(connection -> findBySeller(connection, sellerId, status, pageNo, pageSize));
    }
    @Override
    public int insert(Connection connection, FlowRecord record) {
        validateNew(record);
        return onConnection(connection, current -> insertAndReturnId(current,
                "INSERT INTO flow_record(item_id,seller_id,buyer_id,flow_type,status,remark,created_at) "
                        + "SELECT ?,?,?,?,'PENDING',?,COALESCE(?,datetime('now','localtime')) "
                        + "WHERE NOT EXISTS(SELECT 1 FROM flow_record "
                        + "WHERE item_id=? AND buyer_id=? AND status='PENDING')",
                record.getItemId(), record.getSellerId(), record.getBuyerId(), record.getFlowType().name(),
                record.getRemark(), format(record.getCreatedAt()), record.getItemId(), record.getBuyerId()));
    }
    @Override
    public Optional<FlowRecord> findById(Connection connection, int flowId) {
        positive(flowId);
        return onConnection(connection, current -> query(current, SELECT + " WHERE f.flow_id=?", flowId)
                .stream().findFirst());
    }
    @Override
    public int updateStatus(Connection connection, int flowId, FlowStatus status) {
        positive(flowId);
        if (status == null || status == FlowStatus.PENDING) {
            throw new IllegalArgumentException("更新目标须为成交或取消");
        }
        String sql = "UPDATE flow_record AS target SET status=? WHERE flow_id=? AND status='PENDING'";
        if (status == FlowStatus.DEAL) {
            sql += " AND NOT EXISTS(SELECT 1 FROM flow_record done "
                    + "WHERE done.item_id=target.item_id AND done.status='DEAL')";
        }
        String statement = sql;
        return onConnection(connection, current -> update(current, statement, status.name(), flowId));
    }
    @Override
    public boolean existsPending(Connection connection, int itemId, int buyerId) {
        positive(itemId);
        positive(buyerId);
        return onConnection(connection, current -> count(current,
                "SELECT count(*) FROM flow_record WHERE item_id=? AND buyer_id=? AND status='PENDING'",
                itemId, buyerId) != 0);
    }
    @Override
    public List<FlowRecord> findByUser(Connection connection, int userId, FlowStatus status) {
        Filter filter = filter(userId, status, Side.BOTH);
        return onConnection(connection, current -> query(current, SELECT + filter.where() + ORDER,
                filter.parameters().toArray()));
    }
    @Override
    public int cancelOtherPending(Connection connection, int itemId, int exceptFlowId) {
        positive(itemId);
        positive(exceptFlowId);
        return onConnection(connection, current -> update(current,
                "UPDATE flow_record SET status='CANCELLED' WHERE item_id=? AND flow_id<>? AND status='PENDING' "
                        + "AND EXISTS(SELECT 1 FROM flow_record winner "
                        + "WHERE winner.flow_id=? AND winner.item_id=? AND winner.status='DEAL')",
                itemId, exceptFlowId, exceptFlowId, itemId));
    }
    @Override
    public int cancel(Connection connection, int flowId, String reason) {
        positive(flowId);
        if (reason == null || reason.isBlank()) { throw new IllegalArgumentException("取消原因不能为空"); }
        String detail = "取消原因：" + reason;
        return onConnection(connection, current -> update(current,
                "UPDATE flow_record SET status='CANCELLED',"
                        + "remark=CASE WHEN remark='' THEN ? ELSE remark||char(10)||? END "
                        + "WHERE flow_id=? AND status='PENDING'", detail, detail, flowId));
    }
    @Override
    public PageResult<FlowRecord> findByUser(Connection connection, int userId, FlowStatus status,
                                            int pageNo, int pageSize) {
        return page(connection, userId, status, Side.BOTH, pageNo, pageSize);
    }
    @Override
    public PageResult<FlowRecord> findByBuyer(Connection connection, int buyerId, FlowStatus status,
                                             int pageNo, int pageSize) {
        return page(connection, buyerId, status, Side.BUYER, pageNo, pageSize);
    }
    @Override
    public PageResult<FlowRecord> findBySeller(Connection connection, int sellerId, FlowStatus status,
                                              int pageNo, int pageSize) {
        return page(connection, sellerId, status, Side.SELLER, pageNo, pageSize);
    }

    private PageResult<FlowRecord> page(Connection connection, int userId, FlowStatus status,
                                       Side side, int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 10 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        Filter filter = filter(userId, status, side);
        return onConnection(connection, current -> {
            int total = count(current, "SELECT count(*) FROM flow_record f" + filter.where(),
                    filter.parameters().toArray());
            List<Object> parameters = new ArrayList<>(filter.parameters());
            parameters.add(pageSize);
            parameters.add(((long) pageNo - 1) * pageSize);
            List<FlowRecord> records = query(current, SELECT + filter.where() + ORDER + " LIMIT ? OFFSET ?",
                    parameters.toArray());
            return new PageResult<>(records, total, pageNo, pageSize);
        });
    }

    private static Filter filter(int userId, FlowStatus status, Side side) {
        positive(userId);
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        String where = switch (side) {
            case BOTH -> " WHERE (f.buyer_id=? OR f.seller_id=?)";
            case BUYER -> " WHERE f.buyer_id=?";
            case SELLER -> " WHERE f.seller_id=?";
        };
        if (side == Side.BOTH) { parameters.add(userId); }
        if (status != null) {
            where += " AND f.status=?";
            parameters.add(status.name());
        }
        return new Filter(where, List.copyOf(parameters));
    }
    @Override
    protected FlowRecord mapRow(ResultSet result) throws SQLException {
        try {
            FlowRecord record = new FlowRecord(result.getInt("item_id"), result.getInt("seller_id"),
                    result.getInt("buyer_id"), TradeType.valueOf(result.getString("flow_type")),
                    result.getString("remark"));
            record.setFlowId(result.getInt("flow_id"));
            record.setStatus(FlowStatus.valueOf(result.getString("status")));
            record.setCreatedAt(parse(result.getString("created_at")));
            record.setFinishedAt(parse(result.getString("finished_at")));
            record.setItemTitle(result.getString("item_title"));
            record.setSellerNickname(result.getString("seller_nickname"));
            record.setBuyerNickname(result.getString("buyer_nickname"));
            return record;
        } catch (IllegalArgumentException | DateTimeException failure) {
            throw new SQLException("流转记录包含非法字段", failure);
        }
    }

    private static void validateNew(FlowRecord record) {
        if (record == null) { throw new IllegalArgumentException("流转记录不能为空"); }
        positive(record.getItemId());
        positive(record.getSellerId());
        positive(record.getBuyerId());
        if (record.getStatus() != FlowStatus.PENDING || record.getFinishedAt() != null) {
            throw new IllegalArgumentException("新增意向须处于未完成的待确认状态");
        }
    }
    private static void positive(int value) {
        if (value <= 0) { throw new IllegalArgumentException("编号须为正整数"); }
    }
    private static String format(LocalDateTime time) {
        return time == null ? null : DATE_FORMAT.format(time);
    }
    private static LocalDateTime parse(String text) {
        return text == null ? null : LocalDateTime.parse(text, DATE_FORMAT);
    }
}
