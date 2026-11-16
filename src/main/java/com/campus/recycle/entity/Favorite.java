package com.campus.recycle.entity;

import java.time.LocalDateTime;

/** 收藏记录，Item 是查询时的展示投影，不参与收藏写入。 */
public final class Favorite {
    private int favoriteId;
    private int userId;
    private int itemId;
    private LocalDateTime createdAt;
    private Item item;

    /** 创建待填充的收藏记录。 */
    public Favorite() { }
    /** @param userId 收藏人编号 @param itemId 物品编号，均须为正整数 */
    public Favorite(int userId, int itemId) {
        setUserId(userId);
        setItemId(itemId);
    }
    /** @return 收藏主键，未保存为 0 */
    public int getFavoriteId() { return favoriteId; }
    /** @param favoriteId 非负主键 */
    public void setFavoriteId(int favoriteId) {
        if (favoriteId < 0) { throw new IllegalArgumentException("收藏编号不合法"); }
        this.favoriteId = favoriteId;
    }
    /** @return 收藏人编号 */
    public int getUserId() { return userId; }
    /** @param userId 正整数收藏人编号 */
    public void setUserId(int userId) {
        if (userId <= 0) { throw new IllegalArgumentException("收藏人编号不合法"); }
        this.userId = userId;
    }
    /** @return 物品编号 */
    public int getItemId() { return itemId; }
    /** @param itemId 正整数物品编号 */
    public void setItemId(int itemId) {
        if (itemId <= 0) { throw new IllegalArgumentException("物品编号不合法"); }
        this.itemId = itemId;
    }
    /** @return 收藏时间，保存前可为 null */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt 收藏时间，null 表示首次写入时使用数据库默认值 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** @return 查询时的完整物品投影，未查询时可为 null */
    public Item getItem() { return item; }
    /** @param item 物品投影，不通过此属性修改数据库物品 */
    public void setItem(Item item) { this.item = item; }
}
