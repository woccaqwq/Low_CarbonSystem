# System Configuration Read Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 提供 sys_config 原始配置查询及外部事务读取能力。
**Architecture:** 不可变 SysConfig + 只读 SysConfigDao + BaseDao JDBC 实现 + 工厂入口，无缓存和隐式回退。
**Tech Stack:** Java 17、SQLite JDBC 3.47.1.0、JUnit 5.11.4、Maven。
**Spec:** docs/superpowers/specs/2026-11-16-system-config-design.md；迭代一 schema/init_data 的 sys_config 与 FR-16。

## Global Constraints

- 保留原 SQL、DAO 和异常 API，不新增依赖，不实现配置写入或业务计算。
- 两个普通查询、两个 Connection 首参数重载；外部事务完全归调用方管理。
- 精确字面键、原始值、不可变列表、无缓存；坏持久化字段包装为 DataAccessException。
- 不新增工作树，不提交或推送，保留此前迭代二所有改动。

## Task 1: 实体与读取接口

Files: entity/SysConfig.java、dao/SysConfigDao.java、dao/impl/SysConfigDaoImpl.java；修改 dao/factory/DaoFactory.java；对应实体与 DAO 测试。
Interfaces: Spec 的构造器、三个 getter、两组查询、DaoFactory.getSysConfigDao()。

- [x] 先写初始配置、空值/缺失、字面键、原文保留、时间、不可变列表、故障与连接/事务测试。
- [x] 运行测试确认缺失实现失败。Expected: SysConfig/SysConfigDao/实现/工厂入口缺失。
- [x] 最小实现，再跑指定测试。Expected: 本模块测试全部通过。

## Task 2: 交接与验证

Files: docs/development/iteration2-module10.md、README.md；验收记录追加后续状态。
Interfaces: 示例必须区分原始读取、Service 校验、业务计算，不暗示备份开关已接入。

- [x] 全量离线 package 通过。Expected: 原 200 项及新增测试通过，构建成功。
- [x] 更新交接文档、独立只读审查一次并处理重要问题，结束本模块。

## Review Focus

- 空串、缺失、坏值不可混为默认值；键值不能拼接进 SQL。
- 查询无缓存；外部连接的快照、生命周期与失败后的事务状态不被改变。
- null 键或非法时间的存量数据应转数据访问异常，不让映射的运行时异常直接逃逸。
- 不可变配置和列表不泄露可变引用；调试描述不输出值。
- 原 schema 无配置更新时间触发器，文档不能暗示本模块会自动维护修改时间。

## Execution Ledger

- Pre-flight: Task 1 API 由 Task 2 接入示例消费；以 Spec 为准，无冲突。
- 基线为模块九全量 package：200 tests，0 failures/errors/skips。
- 延用工作分支；在本文记录 RED/GREEN/验证证据，不运行基于提交范围的任务脚本或自动提交。
- Task 1 RED：指定测试因实体、接口、实现与工厂入口缺失而编译失败；target/system-config-red.log。
- Task 1 GREEN：DAO 17 + 实体 3，共 20 tests 通过；target/system-config-green.log。
- Task 2 package：25 类、220 tests，0 failures/errors/skips，BUILD SUCCESS；target/system-config-package.log。
- 产物检查：SysConfig、SysConfigDao、SysConfigDaoImpl 已打入 JAR，major version 均为 61（Java 17）。文档本地链接与 git diff --check 通过。
- Final review：独立只读审查通过，Critical/Important/Minor 均无问题，无需修复或复审。
- 审查范围决定：CO₂/备份、类型转换与范围校验留在后续业务层；误把原始配置当作已校验值会产生错误业务结果，模块文档明确要求校验。
- 审查范围决定：写入、GUI 与时间自动维护不在读取模块范围；后续写入方须显式维护 updated_at。
- 审查范围决定：保留原 DDL，NULL 主键由映射拒绝；坏数据需修正后再读，不隐式清理或跳过。
- 审查范围决定：同时数据库不可用与查询参数非法时不新增错误优先级契约，沿用现有 BaseDao 先取得连接的普通调用路径；双重失败可能先报告技术异常，不影响正常输入或正常数据库下的键验证。
- 本模块完成，停在本地分支，未提交或推送。
