# 低碳校园资源循环流转桌面系统

本系统面向高校校园，实现闲置物品发布、物品分类检索、供需匹配、交易记录、信用评价、数据统计，支持管理员后台管理，实现完整业务闭环。

## 开发环境

使用 JDK 17 或以上、Maven 3.6.3 或以上。Maven 编译目标为 Java 17。
SQLite JDBC 和 JUnit 5 由 Maven 管理，应用不需要外部数据库服务。
请通过 `mvn -version` 确认 Maven 使用的 JDK；IDE 的 Project SDK 也应设为 17 或以上。

在仓库根目录运行测试：

```powershell
mvn "-Dmaven.repo.local=.mvn-cache" test
```

首次运行需联网下载依赖；缓存目录 `.mvn-cache/` 已加入 Git 忽略规则。
也可以使用普通的 `mvn test`，依赖会写入本机 Maven 配置指定的仓库。

## 迭代二进度

- 已完成模块一：Maven 数据层工程骨架、SQLite 连接与安全初始化、数据访问异常和 12 项 JUnit 测试。
- 已完成模块二：用户实体与角色、分页结果、公共 DAO、用户 DAO 与工厂、口令工具。
- 已完成模块三：分类与物品实体、DAO、分页组合筛选、排序策略和事务支持。
- 已完成模块四：收藏实体、幂等添加/取消、是否收藏、分页列表及事务支持。
- 已完成模块五：流转实体、状态条件更新、取消原因、双方历史与分页、成交数据事务支持。
- 已完成模块六：评价实体、唯一约束防重复、收到评价列表/分页/平均分及信用更新事务支持。
- 已完成模块七：分类/月度/状态统计、个人与日期范围、平台概览。
- 已完成模块八：操作日志追加、最近记录、编号查询、用户/动作筛选分页及事务支持。
- 已完成数据层本机验收：跨 DAO 提交、回滚与一万条物品检索验证，当时共 182 项测试。
- 已完成模块九：无凭据的不可变 SessionUser、业务错误码与异常、字段校验异常；新增 18 项测试。
- 已完成模块十：系统配置按键与全部读取、不可变快照、外部事务支持；新增 20 项测试。
- 已完成模块十一：通用 UTF-8 BOM CSV 导出、文本转义、数字列、流式输出与失败清理；新增 16 项测试。
- 已完成模块十二：JDK 运行日志、按天追加、七天清理、DEBUG 开关与安全异常栈；新增 17 项，当前共 253 项测试，package 通过。
- 已完成模块十三：同步迭代二类图、六张接入时序图、状态图及九 DAO 的完整接口清单，入口见 [迭代二设计图册](docs/design/iteration2/README.md)。
- 已完成模块十四：整理独立的 [迭代二系统设计说明书](文本文档/04_迭代2_系统设计说明书.docx)，涵盖当前实现、事务与接入边界及全部 DAO 签名。
- 成员一范围已完成本机验收：数据层、公共工具与配套设计交接，见 [成员一交付与验收清单](docs/development/iteration2-member1-completion.md)。2026年11月21日重新执行 253 项测试及 package，全部通过。
- 后续团队接入：Service、SessionManager 与 GUI；用户本次明确统计业务交给成员二，成员一提供统计 DAO 与公共工具。
- 已完成成员二迭代二业务层：UserService、ItemService、FlowService、ReviewService、StatService、AdminService 六个服务及工厂、会话快照与统一事务模板；新增 24 项业务层测试，全量共 277 项测试通过，package 成功。见 [成员二业务层交付说明](docs/development/iteration2-member2-completion.md)。当前仍无 GUI 与启动入口，成员三负责界面集成。

完整核对结果、事务模板更正及成员交接清单见 [数据层验收与交接记录](docs/development/iteration2-data-acceptance.md)。数据层验收通过不代表整个迭代二已经完成。

当前尚未提供 GUI 或应用启动入口；目前通过 JUnit 验证数据层。
后续入口或 DAO 调用 `DBUtil.getInstance().getConnection()` 时自动完成首次初始化。
默认库文件为运行工作目录下的 `data/campus.db`，也可用 `campus.db.path` 系统属性指定路径。

详细接口和验收说明见 [数据库基础模块说明](docs/development/iteration2-module1.md)、
[用户数据模块说明](docs/development/iteration2-module2.md)、
[分类与物品模块说明](docs/development/iteration2-module3.md)、
[收藏模块说明](docs/development/iteration2-module4.md)、
[流转模块说明](docs/development/iteration2-module5.md)、
[评价模块说明](docs/development/iteration2-module6.md)、
[统计模块说明](docs/development/iteration2-module7.md) 和
[操作日志模块说明](docs/development/iteration2-module8.md)。

公共业务对象见 [会话快照与业务异常说明](docs/development/iteration2-module9.md)。
配置读取见 [系统配置模块说明](docs/development/iteration2-module10.md)。
文件导出见 [CSV 导出工具说明](docs/development/iteration2-module11.md)。
运行日志见 [运行日志模块说明](docs/development/iteration2-module12.md)。
设计同步见 [模块十三说明](docs/development/iteration2-module13.md)。
Word 设计说明书及验证见 [模块十四说明](docs/development/iteration2-module14.md)。
Git 按每两个模块一次本地提交管理，见 [Git 检查点记录](docs/development/git-checkpoints.md)。

## 项目目录

```text
src/main/java/com/campus/recycle/
  entity/User.java PageResult.java Category.java Item.java ItemQuery.java Favorite.java FlowRecord.java Review.java
  entity/StatQuery.java StatCounts.java CategoryStat.java MonthStat.java StatOverview.java
  entity/OperationLog.java
  entity/SessionUser.java
  entity/SysConfig.java
  entity/enums/Role.java ItemStatus.java TradeType.java FlowStatus.java
  dao/UserDao.java CategoryDao.java ItemDao.java FavoriteDao.java FlowRecordDao.java ReviewDao.java StatDao.java
  dao/OperationLogDao.java
  dao/SysConfigDao.java
  dao/impl/BaseDao.java UserDaoImpl.java CategoryDaoImpl.java ItemDaoImpl.java FavoriteDaoImpl.java
  dao/impl/FlowRecordDaoImpl.java ReviewDaoImpl.java ItemSearchSql.java ItemRowMapper.java
  dao/impl/StatDaoImpl.java StatSql.java
  dao/impl/OperationLogDaoImpl.java
  dao/impl/SysConfigDaoImpl.java
  dao/factory/DaoFactory.java
  strategy/SortStrategy.java TimeDescStrategy.java PriceAscStrategy.java CreditDescStrategy.java
  exception/DataAccessException.java
  exception/ErrorCode.java BusinessException.java ValidationException.java
  util/DBUtil.java
  util/DatabaseInitializer.java
  util/PasswordUtil.java
  util/CsvExporter.java
  util/AppLogger.java DailyLogHandler.java SafeLogFormatter.java
src/test/java/com/campus/recycle/   用户、分类、物品、收藏、流转、评价、统计、操作日志及公共基础测试
src/test/resources/sql/invalid_init_data.sql
初始化脚本/             迭代一 SQL 原件，Maven 自动复制到 classpath 的 sql/ 目录
文本文档/               迭代一文档原件及独立的迭代二设计说明书
设计图/                 迭代一设计图
docs/development/       迭代二实现与交接说明
docs/design/iteration2/  迭代二图册、接口及字段清单
```

设计图与 Word 成品已随仓库交付。文档生成、渲染及检查脚本仅保留在本地，不再随 GitHub 仓库分发；Java 编译、测试、打包和运行不依赖这些脚本。
