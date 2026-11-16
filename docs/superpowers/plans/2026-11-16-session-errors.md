# Session And Business Errors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 为 Service/GUI 提供无凭据的不可变会话快照与业务异常契约。
**Architecture:** 五字段 SessionUser 独立于持久化 User；固定 ErrorCode 消息；BusinessException 与字段定位子类 ValidationException 保留 cause。
**Tech Stack:** Java 17，JUnit 5.11.4，现有 Maven/SQLite 测试工程。
**Spec:** docs/superpowers/specs/2026-11-16-session-errors-design.md；迭代一设计说明书第 5.2、8.1–8.2 节。

## Global Constraints

- 本模块仅新增公共实体/异常，不变更现有 DAO、DataAccessException 或表结构，不添加依赖。
- 十三个错误码；登录不存在与口令错误外部提示一致；禁止把异常原因或输入值拼进固定提示。
- Connection 事务模板仍须捕获 RuntimeException | SQLException；新异常为非受检异常。
- 继续 feature/iteration2-data；保留当前未提交源码，本轮不进行 Git 提交或推送。

## Task 1: 公共对象与异常

Files: entity/SessionUser.java；exception/ErrorCode.java、BusinessException.java、ValidationException.java；对应四个测试类。
Interfaces: 以 Spec 中所有字段、构造器、getters、from、isAdmin、getCode、getField、getMessage 的签名和空值语义为准。

- [x] 先新增测试：角色、快照与真实用户映射、输入边界、无凭据、原始错误码、认证失败相同提示、固定安全消息、原因保留及字段定位。
- [x] 运行指定测试确认因缺失类失败。Expected: 缺失上述四个类导致编译失败，不能是既有用例失败。
- [x] 最小实现四个类，再跑指定测试。Expected: 新增测试全通过。

## Task 2: 文档与完整验证

Files: docs/development/iteration2-module9.md；README.md；验收记录追加后续状态。
Interfaces: 文档必须展示真实构造器，说明快照不替代鉴权、信用需刷新，以及业务异常与数据异常的区分。

- [x] 更新接入示例、错误码清单和边界，不把已完成的历史验收改写为当时不存在的状态。
- [x] 全量离线 package。Expected: 原 182 项及新增测试均通过，构建成功。
- [x] 独立只读审查一次，处理重要问题；本模块结束后停下。

## Review Focus

- SessionUser 不应间接保留 User 或凭据；不能把角色快照当身份认证或实时授权依据。
- 非法构造和异常继承正确；Throwable cause 保留但不能进入固定消息。
- ValidationException 的 field 是字段标识，不是表单值；null 表示整表单，不虚构自动表单校验。
- 不对现有技术异常或 DAO 引入破坏性变更；DB_ERROR 的使用不能暗示所有异常已自动转换。

## Execution Ledger

- Pre-flight: Task 1 产出的公共 API 由 Task 2 示例消费；完全采用 Spec 签名，无冲突。
- 基线：上轮 package 为 182 tests，0 failures/errors/skips。
- 执行适配：现有分支与大量未跟踪迭代二代码共同保留；以本文记录测试证据，不使用基于已提交范围的任务脚本或自动提交步骤。
- Task 1 RED：指定测试因 SessionUser/ErrorCode/BusinessException/ValidationException 缺失而编译失败，日志 target/session-errors-red.log。
- Task 1 GREEN：指定四类 18 tests 通过；日志 target/session-errors-green.log。
- Task 2 package：200 tests，0 failures/errors/skips，BUILD SUCCESS；日志 target/session-errors-package.log。
- 产物检查：四个新增 class 均在 JAR 中，major version 61（Java 17）；23 份测试报告合计 200 项。
- Final review：独立只读审查通过，Critical/Important/Minor 均无问题，无需修复或复审。
- Final: Ruling: Service/SessionManager/GUI 的认证、授权刷新、展示与日志脱敏无实现可审查 — 遵循本轮仅公共契约的范围，保留为后续接入验收 — 若误当已实现会造成认证和提示缺口，模块文档及最终回复明确边界。
- Final: Ruling: 本轮未重复全面审查历史 DAO — 其代码未改，沿用上一轮独立审查与 182 项验收，本次全量回归亦通过 — 后续 DAO 变更需重新审查和测试。
- 本模块完成，停在本地分支，未提交或推送。
