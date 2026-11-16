package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.strategy.SortStrategy;
import com.campus.recycle.strategy.TimeDescStrategy;
import java.util.regex.Pattern;

/** 不可变检索条件，Builder 每次 build 生成独立快照。 */
public final class ItemQuery {
    private static final String SORT_TERM = "(?:i\\.(?:price|created_at|item_id)|u\\.credit_score)\\s+(?:ASC|DESC)";
    private static final Pattern SAFE_SORT = Pattern.compile(
            SORT_TERM + "(?:\\s*,\\s*" + SORT_TERM + ")*", Pattern.CASE_INSENSITIVE);
    private final String keyword;
    private final Integer categoryId;
    private final Integer ownerId;
    private final Double minPrice;
    private final Double maxPrice;
    private final TradeType tradeType;
    private final ItemStatus status;
    private final String orderBy;
    private final int pageNo;
    private final int pageSize;

    private ItemQuery(Builder builder) {
        validateId(builder.categoryId);
        validateId(builder.ownerId);
        validatePrice(builder.minPrice);
        validatePrice(builder.maxPrice);
        if (builder.minPrice != null && builder.maxPrice != null && builder.minPrice > builder.maxPrice) {
            throw new IllegalArgumentException("最低价格不能大于最高价格");
        }
        if (builder.pageNo < 1 || builder.pageSize < 10 || builder.pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        String ordering = (builder.sort == null ? new TimeDescStrategy() : builder.sort).toOrderBy();
        if (ordering == null || ordering.length() > 256 || !SAFE_SORT.matcher(ordering).matches()) {
            throw new IllegalArgumentException("排序表达式不在允许范围内");
        }
        keyword = builder.keyword;
        categoryId = builder.categoryId;
        ownerId = builder.ownerId;
        minPrice = builder.minPrice;
        maxPrice = builder.maxPrice;
        tradeType = builder.tradeType;
        status = builder.status;
        orderBy = ordering;
        pageNo = builder.pageNo;
        pageSize = builder.pageSize;
    }

    /** @return 使用默认条件的新 Builder */
    public static Builder builder() { return new Builder(); }
    /** @return 关键词，null 或空白表示全部 */
    public String getKeyword() { return keyword; }
    /** @return 分类编号，null 不限 */
    public Integer getCategoryId() { return categoryId; }
    /** @return 发布人编号，null 不限 */
    public Integer getOwnerId() { return ownerId; }
    /** @return 最低价格，null 不限 */
    public Double getMinPrice() { return minPrice; }
    /** @return 最高价格，null 不限 */
    public Double getMaxPrice() { return maxPrice; }
    /** @return 交易方式，null 不限 */
    public TradeType getTradeType() { return tradeType; }
    /** @return 物品状态，null 不限 */
    public ItemStatus getStatus() { return status; }
    /** @return 冻结的安全排序策略，不再调用传入的外部策略 */
    public SortStrategy getSort() { return () -> orderBy; }
    /** @return 已验证的排序文本 */
    public String getOrderBy() { return orderBy; }
    /** @return 页码 */
    public int getPageNo() { return pageNo; }
    /** @return 每页大小 */
    public int getPageSize() { return pageSize; }

    private static void validateId(Integer id) {
        if (id != null && id <= 0) { throw new IllegalArgumentException("筛选编号须为正整数"); }
    }
    private static void validatePrice(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException("价格须为有限非负数");
        }
    }

    /** 默认在架、时间倒序、1 页、每页 20 条。 */
    public static final class Builder {
        private String keyword;
        private Integer categoryId;
        private Integer ownerId;
        private Double minPrice;
        private Double maxPrice;
        private TradeType tradeType;
        private ItemStatus status = ItemStatus.ON_SALE;
        private SortStrategy sort = new TimeDescStrategy();
        private int pageNo = 1;
        private int pageSize = 20;

        private Builder() { }
        /** @param value 关键词 @return 当前 Builder */
        public Builder keyword(String value) { keyword = value; return this; }
        /** @param value 分类编号 @return 当前 Builder */
        public Builder categoryId(Integer value) { categoryId = value; return this; }
        /** @param value 发布人编号 @return 当前 Builder */
        public Builder ownerId(Integer value) { ownerId = value; return this; }
        /** @param value 最低价格 @return 当前 Builder */
        public Builder minPrice(Double value) { minPrice = value; return this; }
        /** @param value 最高价格 @return 当前 Builder */
        public Builder maxPrice(Double value) { maxPrice = value; return this; }
        /** @param value 交易方式 @return 当前 Builder */
        public Builder tradeType(TradeType value) { tradeType = value; return this; }
        /** @param value 物品状态，null 不限 @return 当前 Builder */
        public Builder status(ItemStatus value) { status = value; return this; }
        /** @param value 排序策略，null 使用时间倒序 @return 当前 Builder */
        public Builder sort(SortStrategy value) { sort = value; return this; }
        /** @param value 页码 @return 当前 Builder */
        public Builder pageNo(int value) { pageNo = value; return this; }
        /** @param value 页大小 @return 当前 Builder */
        public Builder pageSize(int value) { pageSize = value; return this; }
        /** @return 校验后的不可变查询条件 */
        public ItemQuery build() { return new ItemQuery(this); }
    }
}
