package com.campus.recycle.dao.impl;

import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 物品详情与收藏列表共用的行映射，不执行 SQL 或调用 DAO。 */
final class ItemRowMapper {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ItemRowMapper() { }

    static Item read(ResultSet result) throws SQLException {
        try {
            Item item = new Item();
            item.setItemId(result.getInt("item_id"));
            item.setOwnerId(result.getInt("owner_id"));
            int category = result.getInt("category_id");
            item.setCategoryId(result.wasNull() ? null : category);
            item.setTitle(result.getString("title"));
            item.setDescription(result.getString("description"));
            item.setPrice(result.getDouble("price"));
            item.setTradeType(TradeType.valueOf(result.getString("trade_type")));
            item.setStatus(ItemStatus.valueOf(result.getString("status")));
            item.setTags(result.getString("tags"));
            item.setViewCount(result.getLong("view_count"));
            item.setCreatedAt(parse(result.getString("created_at")));
            item.setUpdatedAt(parse(result.getString("updated_at")));
            item.setOwnerNickname(result.getString("owner_nickname"));
            item.setOwnerCredit(result.getInt("owner_credit"));
            item.setCategoryName(result.getString("category_name"));
            return item;
        } catch (IllegalArgumentException | DateTimeException failure) {
            throw new SQLException("物品记录包含非法字段", failure);
        }
    }

    private static LocalDateTime parse(String text) {
        return text == null ? null : LocalDateTime.parse(text, DATE_FORMAT);
    }
}
