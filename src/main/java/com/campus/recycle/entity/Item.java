package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import java.time.LocalDateTime;

/** 物品实体与列表投影；状态方法仅修改内存，数据库变更由 Service 调用 DAO。 */
public final class Item {
    private int itemId;
    private int ownerId;
    private Integer categoryId;
    private String title;
    private String description = "";
    private double price;
    private TradeType tradeType = TradeType.EXCHANGE;
    private ItemStatus status = ItemStatus.ON_SALE;
    private String tags = "";
    private long viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String ownerNickname;
    private int ownerCredit;
    private String categoryName;

    /** 创建默认在架物品，保存前须填写标题和发布人。 */
    public Item() { }
    /** @return 物品编号，未保存为 0 */
    public int getItemId() { return itemId; }
    /** @param itemId 非负编号 */
    public void setItemId(int itemId) {
        if (itemId < 0) { throw new IllegalArgumentException("物品编号不合法"); }
        this.itemId = itemId;
    }
    /** @return 发布人编号 */
    public int getOwnerId() { return ownerId; }
    /** @param ownerId 正整数发布人编号 */
    public void setOwnerId(int ownerId) {
        if (ownerId <= 0) { throw new IllegalArgumentException("发布人编号不合法"); }
        this.ownerId = ownerId;
    }
    /** @return 分类编号，可为 null */
    public Integer getCategoryId() { return categoryId; }
    /** @param categoryId 正整数或 null */
    public void setCategoryId(Integer categoryId) {
        if (categoryId != null && categoryId <= 0) { throw new IllegalArgumentException("分类编号不合法"); }
        this.categoryId = categoryId;
    }
    /** @return 标题 */
    public String getTitle() { return title; }
    /** @param title 非空白，1–60 个 Unicode 码点 */
    public void setTitle(String title) {
        if (title == null || title.isBlank() || title.codePointCount(0, title.length()) > 60) {
            throw new IllegalArgumentException("标题须为 1–60 个字符");
        }
        this.title = title;
    }
    /** @return 描述 */
    public String getDescription() { return description; }
    /** @param description 非 null 描述，可为空串 */
    public void setDescription(String description) { this.description = requireString(description); }
    /** @return 期望价格 */
    public double getPrice() { return price; }
    /** @param price 有限非负数，交换物品可为 0 */
    public void setPrice(double price) {
        if (!Double.isFinite(price) || price < 0) { throw new IllegalArgumentException("价格须为有限非负数"); }
        this.price = price;
    }
    /** @return 交易方式 */
    public TradeType getTradeType() { return tradeType; }
    /** @param tradeType 非空交易方式 */
    public void setTradeType(TradeType tradeType) {
        if (tradeType == null) { throw new IllegalArgumentException("交易方式不能为空"); }
        this.tradeType = tradeType;
    }
    /** @return 当前状态 */
    public ItemStatus getStatus() { return status; }
    /** @param status 非空状态，供结果映射使用，业务转移应调用语义方法 */
    public void setStatus(ItemStatus status) {
        if (status == null) { throw new IllegalArgumentException("物品状态不能为空"); }
        this.status = status;
    }
    /** @return 标签文本 */
    public String getTags() { return tags; }
    /** @param tags 非 null 标签文本 */
    public void setTags(String tags) { this.tags = requireString(tags); }
    /** @return 浏览量 */
    public long getViewCount() { return viewCount; }
    /** @param viewCount 非负浏览量 */
    public void setViewCount(long viewCount) {
        if (viewCount < 0) { throw new IllegalArgumentException("浏览量不能为负"); }
        this.viewCount = viewCount;
    }
    /** @return 创建时间，保存前可为 null */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt 创建时间，null 使用数据库默认值 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** @return 更新时间 */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /** @param updatedAt 初始更新时间，后续由数据库维护 */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    /** @return 发布人昵称，查询投影 */
    public String getOwnerNickname() { return ownerNickname; }
    /** @param ownerNickname 发布人昵称，不写入 item 表 */
    public void setOwnerNickname(String ownerNickname) { this.ownerNickname = ownerNickname; }
    /** @return 发布人信用分，查询投影 */
    public int getOwnerCredit() { return ownerCredit; }
    /** @param ownerCredit 发布人信用分，不写入 item 表 */
    public void setOwnerCredit(int ownerCredit) { this.ownerCredit = ownerCredit; }
    /** @return 分类名称，无分类时为 null */
    public String getCategoryName() { return categoryName; }
    /** @param categoryName 分类名称，不写入 item 表 */
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    /** 下架在架物品。@throws IllegalStateException 当前状态不允许 */
    public void offline() { transferTo(ItemStatus.OFF_SHELF); }
    /** 重新上架已下架物品。@throws IllegalStateException 当前状态不允许 */
    public void publish() { transferTo(ItemStatus.ON_SALE); }
    /** 将在架物品标记为已流转。@throws IllegalStateException 当前状态不允许 */
    public void markFlowed() { transferTo(ItemStatus.FLOWED); }

    private void transferTo(ItemStatus target) {
        if (!status.canTransferTo(target)) { throw new IllegalStateException("当前物品状态不支持该操作"); }
        status = target;
    }
    private static String requireString(String value) {
        if (value == null) { throw new IllegalArgumentException("文本字段不能为 null"); }
        return value;
    }
}
