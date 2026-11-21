# 迭代二实体字段清单

从 Java 顶层 private 实例字段生成，排除静态常量和 Builder 内部字段。final 是字段不可重新赋值，不代表所引用的元素深度不可变。

## Category

[类型源](../../../src/main/java/com/campus/recycle/entity/Category.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| categoryId | int | 否 |
| name | String | 否 |
| parentId | Integer | 否 |
| sortNo | int | 否 |

## CategoryStat

[类型源](../../../src/main/java/com/campus/recycle/entity/CategoryStat.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| categoryId | Integer | 是 |
| categoryName | String | 是 |
| counts | StatCounts | 是 |

## Favorite

[类型源](../../../src/main/java/com/campus/recycle/entity/Favorite.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| favoriteId | int | 否 |
| userId | int | 否 |
| itemId | int | 否 |
| createdAt | LocalDateTime | 否 |
| item | Item | 否 |

## FlowRecord

[类型源](../../../src/main/java/com/campus/recycle/entity/FlowRecord.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| flowId | int | 否 |
| itemId | int | 否 |
| sellerId | int | 否 |
| buyerId | int | 否 |
| flowType | TradeType | 否 |
| status | FlowStatus | 否 |
| remark | String | 否 |
| createdAt | LocalDateTime | 否 |
| finishedAt | LocalDateTime | 否 |
| itemTitle | String | 否 |
| sellerNickname | String | 否 |
| buyerNickname | String | 否 |

## Item

[类型源](../../../src/main/java/com/campus/recycle/entity/Item.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| itemId | int | 否 |
| ownerId | int | 否 |
| categoryId | Integer | 否 |
| title | String | 否 |
| description | String | 否 |
| price | double | 否 |
| tradeType | TradeType | 否 |
| status | ItemStatus | 否 |
| tags | String | 否 |
| viewCount | long | 否 |
| createdAt | LocalDateTime | 否 |
| updatedAt | LocalDateTime | 否 |
| ownerNickname | String | 否 |
| ownerCredit | int | 否 |
| categoryName | String | 否 |

## ItemQuery

[类型源](../../../src/main/java/com/campus/recycle/entity/ItemQuery.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| keyword | String | 是 |
| categoryId | Integer | 是 |
| ownerId | Integer | 是 |
| minPrice | Double | 是 |
| maxPrice | Double | 是 |
| tradeType | TradeType | 是 |
| status | ItemStatus | 是 |
| orderBy | String | 是 |
| pageNo | int | 是 |
| pageSize | int | 是 |

## MonthStat

[类型源](../../../src/main/java/com/campus/recycle/entity/MonthStat.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| month | YearMonth | 是 |
| counts | StatCounts | 是 |

## OperationLog

[类型源](../../../src/main/java/com/campus/recycle/entity/OperationLog.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| logId | int | 否 |
| userId | Integer | 否 |
| action | String | 否 |
| detail | String | 否 |
| createdAt | LocalDateTime | 否 |
| username | String | 否 |
| nickname | String | 否 |

## PageResult

[类型源](../../../src/main/java/com/campus/recycle/entity/PageResult.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| items | List<T> | 是 |
| total | int | 是 |
| pageNo | int | 是 |
| pageSize | int | 是 |

## Review

[类型源](../../../src/main/java/com/campus/recycle/entity/Review.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| reviewId | int | 否 |
| flowId | int | 否 |
| fromUserId | int | 否 |
| toUserId | int | 否 |
| score | int | 否 |
| content | String | 否 |
| createdAt | LocalDateTime | 否 |
| fromNickname | String | 否 |
| toNickname | String | 否 |
| itemTitle | String | 否 |

## SessionUser

[类型源](../../../src/main/java/com/campus/recycle/entity/SessionUser.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| userId | int | 是 |
| username | String | 是 |
| nickname | String | 是 |
| role | Role | 是 |
| creditScore | int | 是 |

## StatCounts

[类型源](../../../src/main/java/com/campus/recycle/entity/StatCounts.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| onSaleItemCount | long | 是 |
| offShelfItemCount | long | 是 |
| flowedItemCount | long | 是 |
| pendingFlowCount | long | 是 |
| dealFlowCount | long | 是 |
| cancelledFlowCount | long | 是 |
| itemCount | long | 是 |
| flowCount | long | 是 |

## StatOverview

[类型源](../../../src/main/java/com/campus/recycle/entity/StatOverview.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| counts | StatCounts | 是 |
| userCount | long | 是 |
| averageCreditScore | double | 是 |

## StatQuery

[类型源](../../../src/main/java/com/campus/recycle/entity/StatQuery.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| from | LocalDate | 是 |
| to | LocalDate | 是 |
| userId | Integer | 是 |

## SysConfig

[类型源](../../../src/main/java/com/campus/recycle/entity/SysConfig.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| configKey | String | 是 |
| configValue | String | 是 |
| updatedAt | LocalDateTime | 是 |

## User

[类型源](../../../src/main/java/com/campus/recycle/entity/User.java)

| 字段 | Java 类型 | final |
| --- | --- | --- |
| userId | int | 否 |
| username | String | 否 |
| passwordHash | String | 否 |
| salt | String | 否 |
| nickname | String | 否 |
| role | Role | 否 |
| creditScore | int | 否 |
| status | int | 否 |
| createdAt | LocalDateTime | 否 |
| lastLoginAt | LocalDateTime | 否 |
