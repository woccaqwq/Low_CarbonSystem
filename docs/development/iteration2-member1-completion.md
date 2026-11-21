# 成员一迭代二交付与验收清单

截至 2026年11月21日，成员一的数据层、公共工具和配套设计交接材料已完成本机验收，可交给成员二接入业务层。当前实现基线为 `1b3ae6f`，设计图册与说明书提交为 `f789576`；本清单补充最新验证和职责范围。

本次用户明确选择：只完成成员一的数据层、公共工具和配套设计交接，统计业务交给成员二。因此，StatService、StatReport、减碳估算及报表 CSV 业务接入不列为成员一未完成项。此处记录用户本次确认，不代替小组三方接口评审或实际联调签收。

## 交付清单

| 职责 | 已交付内容 | 验证或阅读入口 |
| --- | --- | --- |
| 数据库与持久化基础 | SQLite 连接、首次原子初始化、外键与 WAL、版本识别、已有库保护 | [模块一](iteration2-module1.md)，DBUtilTest |
| 领域与公共数据对象 | 用户、分类、物品、收藏、流转、评价、日志及统计/配置对象；角色与状态枚举；分页与查询对象 | [实体字段清单](../design/iteration2/entity-fields.md)，entity 测试 |
| 数据访问层 | User、Category、Item、Favorite、FlowRecord、Review、Stat、OperationLog、SysConfig 九个 DAO 及实现；BaseDao、DaoFactory | [112 个接口方法](../design/iteration2/dao-api.md)，各 DaoImplTest |
| 事务支持 | 56 个普通方法与 56 个外部 Connection 重载；条件更新、唯一约束、同连接读写与失败回滚支持 | [数据层验收](iteration2-data-acceptance.md)，DataLayerAcceptanceTest |
| 口令工具 | PBKDF2 随机盐、哈希与验证、初始账号兼容 | [模块二](iteration2-module2.md)，PasswordUtilTest |
| 会话数据与异常 | 无凭据的不可变 SessionUser，ErrorCode、BusinessException、ValidationException、DataAccessException | [模块九](iteration2-module9.md)，会话与异常测试 |
| 配置与文件工具 | 配置原文读取、UTF-8 BOM CSV 导出、按天追加的运行日志和七天清理 | [模块十](iteration2-module10.md)、[十一](iteration2-module11.md)、[十二](iteration2-module12.md) |
| 设计与接入说明 | 10 张设计图及可编辑源、接口/字段清单、事务与权限接入边界、17 页 Word 说明书 | [图册](../design/iteration2/README.md)、[说明书](../../文本文档/04_迭代2_系统设计说明书.docx)、[模块十四](iteration2-module14.md) |

## 本次验收结果

本轮重新执行全量测试及打包，不只引用模块十二的历史结果。

| 检查 | 2026年11月21日结果 |
| --- | --- |
| 全量 Maven package | BUILD SUCCESS；27 个测试类，253 项测试，失败 0、错误 0、跳过 0 |
| 跨 DAO 验收 | 共同提交、提交前隔离、重开连接后数据/投影一致、末端日志写入失败后整体回滚通过 |
| 一万条物品检索 | 5 次查询结果正确；本轮最慢 32.036 毫秒，仅为本机该样本的观察值 |
| 设计图与源码核对 | 10 图、9 DAO、112 方法、49 个源类型，检查通过 |
| Word | 17 页已重新渲染并逐页检查；13 个表格、3 张内嵌图；112 个 DAO 签名逐项齐全 |
| JAR SQL 资源 | 两份 SQL 与 `初始化脚本/` 原件字节完全相同，SHA-256 一致 |
| JAR 内容 | 所有项目 class 的 major version 为 61（Java 17）；未含测试类、JUnit、业务数据库或日志 |
| 代码交付复核 | 未发现本次范围内的阻塞问题；生产源码未发现 TODO/FIXME、printStackTrace 或 System.out/System.err 调试输出 |

验收环境为 Windows 11、Maven 3.6.3、Oracle JDK 23.0.1，编译目标 Java 17。尚未单独使用 JDK 17 运行验收。测试使用临时数据库，不会重建用户的业务库。

已缓存依赖的本机可复现：

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

队友首次运行去掉 `-o` 联网下载依赖，Java/Maven 运行环境须使用 `mvn -version` 核对。图册与 Word 的生成、渲染及检查脚本已按用户要求停止在仓库分发；现有检查结论保留为历史验收证据，图册成品与可编辑源继续交付。Java 工程不需要这些脚本或 Node.js。

测试报告在 `target/surefire-reports/`，数据层构建产物在 `target/low-carbon-system-0.2.0-SNAPSHOT.jar`，Word 检查输出在 `target/design-word/`。这些目录被 Git 忽略，团队应从源码重建。当前 JAR 无 Main-Class，也未内嵌 SQLite JDBC；可双击的最终应用由后续 GUI 与打包集成提供。

SQL 资源摘要：

```text
04_数据库建表脚本_schema.sql
0a6d19a3ce6f8074dd1d46e516ed5f736676694d16639d74c40392dbd03db72c

05_初始化数据脚本_init_data.sql
8186064187da56da28fa6f0ecc719dfd33c5925c035a08d3c34b207f157353e6
```

## 成员二接入清单

- 使用 `dao.factory.DaoFactory` 获取 DAO；测试可构造 DAO 实现并注入 `DBUtil.forPath(Path)`。精确签名及返回语义以接口清单和源码为准。
- 多 DAO 业务由 Service 打开一个连接、开启事务，并将同一连接传给所有查询和写入。捕获 `RuntimeException | SQLException` 后回滚，回滚失败附为 suppressed，最后关闭连接。完整模板见 [流转数据模块说明](iteration2-module5.md)。
- 成交的流转状态、物品状态与双方信用等必需单行更新应命中 1 行；`cancelOtherPending` 返回 0 合法。成交双方各加 1 分、双方各一条业务日志均由 Service 编排。
- 评价资格、交易双方、七天期限、每人一次及接收人身份由 Service 校验。每条评价只调整接收人信用，不能再次给成交双方加分。
- 注册登录、账号禁用与权限、SessionManager、分类维护规则、统计报表/减碳估算均由业务层接入。SessionUser 只是快照，不能把界面传来的对象直接当作认证凭证。
- 统计 DAO 与通用 CsvExporter 已提供。StatService/StatReport 应决定获准范围、跨年汇总、系数校验、列映射及 I/O 业务提示；不能公开 User 的哈希与盐。详见 [统计口径](iteration2-module7.md)。

## 成员三接入清单

- GUI 通过 Service 调用业务，分页使用 PageResult；耗时 I/O 放后台任务。
- 程序首次使用 DBUtil 连接时自动初始化；默认库为工作目录的 `data/campus.db`，可通过 `campus.db.path` 系统属性指定。无需安装 SQLite 命令行工具。
- App 统一管理运行日志工具的生命周期；CSV 的选址与结果提示、界面展示、可执行 JAR 和部署说明在应用集成时完成。

## 后续协作事项

在本次确认的成员一迭代二范围内，没有待补的必需实现模块。队友接口评审、真实 Service/GUI 联调仍需小组继续推进，不能据此宣称整个迭代二或最终系统已验收。

旧库迁移、分类停用/引用删除策略、物品所有者查看违规下架原因等仍为后续设计协作事项；不在本次收尾中单方面变更表结构或既有接口。备份恢复是任务书的选做项，未作为本次必交功能补写。

本清单是当前范围与状态入口。原迭代一资料及早期验收说明保留当时事实；其中“统计分工待定”等历史条目，以本清单和 [接口交接更新](../design/iteration2/interface-changes.md) 的本次范围说明为准。
