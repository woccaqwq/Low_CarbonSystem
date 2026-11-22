package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.TradeType;

/**
 * 物品发布/修改表单，不含发布人、状态与主键等由 Service 决定的字段。
 * 构造时完成字段级校验，Service 只需处理业务权限与状态规则。
 */
public final class ItemForm {
    private final String title;
    private final Integer categoryId;
    private final String description;
    private final double price;
    private final TradeType tradeType;
    private final String tags;

    private ItemForm(Builder builder) {
        if (builder.title == null || builder.title.isBlank()
                || builder.title.codePointCount(0, builder.title.length()) > 60) {
            throw new IllegalArgumentException("标题须为 1–60 个字符");
        }
        if (builder.categoryId != null && builder.categoryId <= 0) {
            throw new IllegalArgumentException("分类编号须为正整数");
        }
        if (builder.description == null || builder.description.codePointCount(0, builder.description.length()) > 1000) {
            throw new IllegalArgumentException("描述不能为 null 或超过 1000 字");
        }
        if (!Double.isFinite(builder.price) || builder.price < 0) {
            throw new IllegalArgumentException("价格须为有限非负数");
        }
        if (builder.tradeType == null) {
            throw new IllegalArgumentException("交易方式不能为空");
        }
        if (builder.tags == null || builder.tags.codePointCount(0, builder.tags.length()) > 200) {
            throw new IllegalArgumentException("标签不能为 null 或超过 200 字");
        }
        title = builder.title;
        categoryId = builder.categoryId;
        description = builder.description;
        price = builder.price;
        tradeType = builder.tradeType;
        tags = builder.tags;
    }

    /** @return 使用默认值的新 Builder */
    public static Builder builder() { return new Builder(); }

    /** @return 标题 */
    public String getTitle() { return title; }
    /** @return 分类编号，可为 null 表示未分类 */
    public Integer getCategoryId() { return categoryId; }
    /** @return 描述 */
    public String getDescription() { return description; }
    /** @return 价格 */
    public double getPrice() { return price; }
    /** @return 交易方式 */
    public TradeType getTradeType() { return tradeType; }
    /** @return 标签 */
    public String getTags() { return tags; }

    /** 默认值：无分类、空描述、价格 0、交换方式、空标签。 */
    public static final class Builder {
        private String title;
        private Integer categoryId;
        private String description = "";
        private double price;
        private TradeType tradeType = TradeType.EXCHANGE;
        private String tags = "";

        private Builder() { }

        /** @param value 标题 @return 当前 Builder */
        public Builder title(String value) { title = value; return this; }
        /** @param value 分类编号或 null @return 当前 Builder */
        public Builder categoryId(Integer value) { categoryId = value; return this; }
        /** @param value 描述 @return 当前 Builder */
        public Builder description(String value) { description = value; return this; }
        /** @param value 价格 @return 当前 Builder */
        public Builder price(double value) { price = value; return this; }
        /** @param value 交易方式 @return 当前 Builder */
        public Builder tradeType(TradeType value) { tradeType = value; return this; }
        /** @param value 标签 @return 当前 Builder */
        public Builder tags(String value) { tags = value; return this; }
        /** @return 校验后的不可变表单 */
        public ItemForm build() { return new ItemForm(this); }
    }
}
