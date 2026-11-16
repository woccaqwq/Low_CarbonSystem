# Category and Item Data Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 先完成分类数据层，再完成物品数据层，为成员 2 的 ItemService 提供实现与测试。

**Architecture:** 沿用 BaseDao、DBUtil、PageResult 和 Connection 首参数重载。SQL 参数化，事务和权限由 Service 编排。
ItemQuery 使用不可变 Builder，排序策略只允许物品价格、发布时间、编号和用户信用列，按物品编号补稳定排序。

**Tech Stack:** Java 17、SQLite JDBC 3.47.1.0、JUnit 5.11.4、Maven。

**Spec:** 文本文档/02_迭代1_系统设计说明书.docx 第 4.2、4.4、5、7、9.3、10 章；已批准的分类先行、物品后续开发顺序。

## Global Constraints

- 保留 ItemDao 的 findById、insert、update、updateStatus、increaseViewCount、existsOnSale、search 七个普通签名，增加同连接重载。
- CategoryDao 补齐 findById、findAll、insert、update、deleteById 及 Connection 重载；修改接口说明，不修改数据库结构。
- 分类同级名称不重复，包括 parent_id 为 NULL 的根分类；根分类用单条条件 SQL 补足 SQLite NULL 唯一键语义。
- 分类层级和权限由 Service 校验；父分类删除受外键 RESTRICT 保护，删除被物品引用的叶分类使 item.category_id 置 NULL。
- 物品 update 只更新可编辑字段，不修改发布人、状态、浏览量及创建时间；状态变更使用专门方法。
- ItemQuery 默认在架、时间倒序、第 1 页、每页 20 条，null 状态表示不限；增加 ownerId 供“我的物品”使用。
- 分类筛选包含选中分类及后代；使用 UNION 去重的递归查询，避免损坏层级形成无限递归。
- 普通方法关闭自身连接；外部连接重载不提交、回滚或关闭连接；保留已有未提交修改，不推送。

## Review Focus

- NULL 分类读取、删除后物品无分类时仍可查询。
- 根分类同名不依赖 SQLite UNIQUE(NULL,name)，子分类同名仅在同父级拒绝。
- 排序策略 SQL 注入及可变策略，Builder 冻结安全排序文本，DAO 不再次读取外部策略。
- NaN/Infinity、负价格、倒置区间、非法分页参数及 long offset。
- 物品修改不覆盖成交状态/所有权，外部事务中的分类和物品变更可一起回滚。

## Task 1: 分类模块

Files: entity/Category.java，dao/CategoryDao.java，dao/impl/CategoryDaoImpl.java，dao/impl/CategoryDaoImplTest.java。
Interfaces: Category 无参/三参构造(name,parentId,sortNo)、getter/setter/isRoot；DAO 五个普通方法与 Connection 重载。

- [x] 先写分类读取、排序、增改删、根/子分类唯一性、外键和回滚测试，运行确认缺失实现。
- [x] 实现分类实体与 DAO，复用 BaseDao，不承担管理员权限或完整分类树业务规则。
- [x] 运行全套测试验证分类模块，57/57。

## Task 2: 物品实体与查询对象

Files: entity/Item.java、ItemQuery.java、enums/ItemStatus.java、TradeType.java，strategy/SortStrategy.java、TimeDescStrategy.java、PriceAscStrategy.java、CreditDescStrategy.java，以及 entity/ItemTest.java、ItemQueryTest.java。
Interfaces: Item 对应 item 字段及发布人昵称/信用、分类名投影；offline/publish/markFlowed；ItemQuery.builder() fluent setters/build/getters；SortStrategy.toOrderBy()。

- [x] 写状态机、实体不变量、Builder 默认值/快照/非法数字/非法排序/极端分页测试，运行确认缺失实现。
- [x] 实现实体与不可变查询对象，保留既有字段类型 Double 区间及 TradeType/ItemStatus 枚举。
- [x] 运行全套测试验证，64/64。

## Task 3: 物品 DAO 与交接

Files: dao/ItemDao.java、dao/impl/ItemDaoImpl.java、ItemSearchSql.java、dao/impl/ItemDaoImplTest.java，修改 DaoFactory、README 和模块说明。
Consumes: Task 1 分类，Task 2 实体与查询对象，BaseDao 与 DBUtil。
Produces: ItemDao 七个冻结方法与七个 Connection 重载，DaoFactory.getCategoryDao/getItemDao。

- [x] 编写初始数据映射、插入持久化、字段修改保护、状态/浏览量、组合检索/排序/分页、注入、外键/事务测试，运行确认缺失实现。
- [x] 实现 JDBC DAO，使用完整 item JOIN 查询而非缺少 description/updated_at 的列表视图，不改视图。
- [x] 更新使用说明与接口补充记录，运行全套测试和 package。
- [x] 独立只读审查实际未提交代码，无阻塞问题；模块完成后停下。

## Execution Record

- 基线验证：49 tests，0 failures/errors/skips。
- 延续 feature/iteration2-data；本轮不创建新工作树、不提交或推送。
- Task 1: 缺失分类实体/DAO 的 RED 后实现，通过 57/57；分类新增测试 8 项。
- Task 2: 缺失物品实体/查询对象的 RED 后实现，通过 64/64；新增 7 项。
- Task 3: 缺失物品 DAO 的 RED 后实现，通过 81/81；物品 DAO 新增 17 项。
- 独立审查通过；按非阻塞建议补充 3 项现有行为的边界测试，不修改生产逻辑：long 浏览量读写、错误发生后调用方决定事务回滚、损坏分类环的 UNION 终止。
