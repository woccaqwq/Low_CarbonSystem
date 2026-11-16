# Favorite Data Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 完成收藏实体、添加/取消/判断/分页 DAO 与测试，供 ItemService 调用。

**Architecture:** 复用 BaseDao、DBUtil、PageResult 和物品列表投影。提取 ItemRowMapper 供物品和收藏 DAO 共用，避免 DAO 互相调用或复制行映射。
重复收藏使用定向 ON CONFLICT UPSERT 和 RETURNING 原主键；不覆盖原收藏时间。列表通过 JOIN 获取物品最新状态，不按在架状态过滤。

**Tech Stack:** Java 17、SQLite JDBC 3.47.1.0、JUnit 5.11.4、Maven。

**Spec:** 设计说明书 Favorite、FavoriteDao 以及需求 FR-08；沿用已批准的 SQLite 表结构与逐模块开发方式。

## Global Constraints

- Favorite 字段 favoriteId、userId、itemId、createdAt；额外 Item 投影用于列表，不参与收藏写入。
- FavoriteDao.insert(Favorite): int 返回新主键或同用户同物品已有主键；不改变输入对象。
- deleteByUserAndItem(int userId,int itemId): int 只删除指定用户与物品组合，返回行数。
- exists(int userId,int itemId): boolean；findByUser(int userId,int pageNo,int pageSize): PageResult<Favorite>。
- 四个方法均提供 Connection 首参数重载，不关闭、提交或回滚外部连接。
- 页码从 1 开始、每页 10–100，按收藏时间倒序、收藏编号倒序稳定排序，offset 使用 long。
- 全部业务值使用 PreparedStatement。无效用户/物品外键错误不能被幂等逻辑吞掉。
- 登录身份、收藏资格和界面反馈由 Service 负责；原 SQL 不变，无 GUI 或 FlowRecord 开发。
- 延续现有 feature/iteration2-data 分支，保留既有未提交修改，本轮不提交或推送。

## Review Focus

- 重复添加保留主键和原时间，不增加记录；取消两次返回 1、0。
- 按用户隔离，不能因取消同一物品而删除他人的收藏。
- 下架/流转和分类删除不使收藏消失，查询投影展示当前状态与空分类。
- 不同创建时间与同时间排序稳定，超大页码不溢出。
- 外部事务失败后仍由调用方决定回滚；删除用户/物品时外键级联清理收藏。

## Task 1: 收藏实现与真实数据库测试

Files: entity/Favorite.java、dao/FavoriteDao.java、dao/impl/FavoriteDaoImpl.java、dao/impl/ItemRowMapper.java、dao/impl/FavoriteDaoImplTest.java；修改 ItemDaoImpl 行映射委托、DaoFactory。
Interfaces: Favorite 无参/两参构造(userId,itemId)，getter/setter，getItem/setItem；四个 DAO 方法与连接重载；DaoFactory.getFavoriteDao()。

- [x] 写初始数据、持久化、幂等添加、用户隔离、分页、外键、当前物品投影、事务与级联删除测试。
- [x] 运行目标测试，确认收藏实现缺失而失败。
- [x] 实现实体与 DAO，并提取共享映射；UPSERT 仅针对 UNIQUE(user_id,item_id)。
- [x] 运行全套测试，99/99，既有物品映射不回归。

## Task 2: 交接与验证

- [x] 更新 README 和模块四说明，记录新接口及参数顺序。
- [x] 运行 package，确认全套测试与构建通过，99/99。
- [x] 独立只读审查模块，无 Critical/Important 问题；完成后停下。

## Execution Record

- 基线：84 tests，0 failures/errors/skips。
- 本模块沿用既有设计，四个明确方法补齐未细化的 FavoriteDao，不要求业务层先查询再插入。
- Task 1: 新测试因缺少收藏实现而 RED；实现并提取共享物品映射后，全套 99/99。
- 独立审查通过；按建议让既有日期测试使用三个不同的固定日期，分别断言收藏时间、物品创建时间和更新时间，保护时间别名映射。
