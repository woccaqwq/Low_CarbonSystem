# Review Data Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 完成评价实体与数据访问，为 ReviewService 提供防重复存储、收到的评价查询、平均分及同连接事务支持。

**Architecture:** 复用 BaseDao/DBUtil/PageResult，保持原 ReviewDao 四个普通方法，新增编号查询与收到评价的分页重载。
数据库 UNIQUE(flow_id,from_user_id) 拒绝重复评价；DAO 不自动更新信用分，Service 用同一连接调用 UserDao。

**Tech Stack:** Java 17、SQLite JDBC 3.47.1.0、JUnit 5.11.4、Maven。

**Spec:** 设计说明书第 4.2、5.2、8.3 章；需求 FR-13/14 与 BR-06；原 review 表。

## Global Constraints

- 保留 insert(Review): int、existsByFlowAndFrom(int flowId,int fromUserId): boolean、findByUser(int userId): List<Review>、avgScoreOf(int userId): double。
- findByUser 与 avgScoreOf 均针对收到的评价，即 to_user_id；无评价平均分返回 0.0。
- 新增 findById(int): Optional<Review>、findByUser(int,int pageNo,int pageSize): PageResult<Review>；六个方法均提供 Connection 重载。
- Review 表字段全部映射；额外 fromNickname、toNickname、itemTitle 为当前展示投影，不写入原表。
- 评分须 1–5，内容非 null、最多 200 Unicode 码点，可为空串；关联编号须正数，不能评价自己。
- 重复评价抛 DataAccessException，不使用幂等插入，避免业务重试时重复加信用。
- 只由 Service 判断成交状态、交易双方身份、收评对象和 7 天窗口；DAO 不承担身份授权或自动加减分。
- 收评排序 created_at DESC、review_id DESC；分页 1 起、10–100，offset 使用 long。
- SQL 参数化，外部连接不由 DAO 提交、回滚或关闭。表结构及已有代码修改保留，不提交或推送。

## Review Focus

- 收到/发出评价不能混淆；平均值按收评人过滤，保留小数，无评价为 0.0。
- 同一流转双方各一次允许，同一评价人第二次必须拒绝。
- 200 字边界、非 BMP Unicode、评分越界和不完整实体在写入前拒绝。
- 评价插入与信用更新中途失败均可回滚；单独插入不偷偷更新信用。
- 分页同时间稳定排序，超大 offset 不溢出，查询投影不改变原评价。

## Task 1: 实体与 DAO 测试驱动实现

Files: entity/Review.java、dao/ReviewDao.java、dao/impl/ReviewDaoImpl.java、entity/ReviewTest.java、dao/impl/ReviewDaoImplTest.java，修改 DaoFactory。
Interfaces: Review 无参/五参构造(flowId,fromUserId,toUserId,score,content)、表字段与投影 getter/setter、validateScore()；六个 DAO 方法与 Connection 重载；DaoFactory.getReviewDao()。

- [x] 先写实体、初始映射、插入回读、互评/重复、收评与均值、边界、分页、外键及事务测试，确认缺失实现而失败。
- [x] 实现实体和 JDBC 存取，保持业务策略在 Service 层。
- [x] 全套测试通过，含审查后补充的 Unicode 存取测试共 142 项，原模块不回归。

## Task 2: 交接与验证

- [x] 更新模块六说明与 README，记录收到评价语义和评价/信用同连接示例。
- [x] package 测试与构建通过；独立只读审查无 Critical/Important 问题。
- [x] 完成本模块后停下。

## Execution Record

- 基线：122 tests，0 failures/errors/skips。
- 信用规则以需求 FR-14 为准：收到 4–5 星 +2，1–2 星 -3，3 星不变；DAO 只提供存取，由 ReviewService 更新被评价人。
- 测试调试：级联删除的 Statement.executeUpdate 返回 3 而非 1。核对 SQLite JDBC 字节码确认使用 total_changes 差值，包含两个级联子行；改为验证父记录与子评价确实删除，不绑定驱动返回行数。生产代码无需修改。
- 新测试因缺少 Review/ReviewDao 而 RED；实现并修正测试对驱动行数的假设后，全套 141/141。
- 独立审查通过；按建议补充 200 个非 BMP 字符经 JDBC 写入读回的边界测试，新增测试累计 20 项。
