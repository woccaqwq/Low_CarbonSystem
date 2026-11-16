# Runtime Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 JDK 运行日志、按天追加与七天清理，并完成本次本地 Git 提交。
**Architecture:** AppLogger facade + JUL anonymous logger + DailyLogHandler + SafeLogFormatter，Clock 可注入，目录文件锁保证单写入实例。
**Tech Stack:** Java 17、java.util.logging、JDK 文件 API、JUnit 5，保留既有依赖。
**Spec:** docs/superpowers/specs/2026-11-16-runtime-logging-design.md；原设计 8.5。

## Global Constraints

- DEBUG 默认关闭，UTF-8 按本地日期 app-yyyyMMdd.log；七天为当天及前六天。
- 元数据转义；异常消息不输出，类型和栈保留。调用方仍负责元数据不含口令/SQL 个人参数。
- 只清理严格匹配的过期普通日志，不递归、不跟随链接。
- 不改 root logger、DAO、schema、Service；运行日志不等于数据库业务日志。
- 用户已授权本地提交：模块十二通过后提交此前全部未提交的迭代二成果；不推送。

## Task 1: 日志实现与测试

Files: util/AppLogger.java、DailyLogHandler.java、SafeLogFormatter.java；test/util/AppLoggerTest.java。
Interfaces: Spec 的三个构造器、Level、log 两重载与 close；私有实现可按职责拆分。

- [x] 先写格式/级别、跨日/时区/追加、七天边界、文件保护、锁、并发、异常原因/循环、I/O 失败与重试、资源释放和非法参数测试。
- [x] 运行测试确认缺失实现。Expected: AppLogger 类缺失导致失败。
- [x] 最小实现后指定测试通过。Expected: 所有新增测试通过。

## Task 2: 交接、验证和提交

Files: docs/development/iteration2-module12.md、README.md、验收记录后续状态、docs/development/git-checkpoints.md。
Interfaces: 示例不吞原业务异常，不能把工具完成当作全项目已经自动接入日志。

- [x] 全量离线 package，通过既有 236 项及新增测试。
- [x] 独立只读审查一次并处理重要问题，更新文档。
- 本地提交步骤：核对暂存范围和忽略规则，提交当前迭代二成果，再用 git log/status 验证。本文随该检查点保存，实际结果以提交历史和最终回复为准，不推送。

## Review Focus

- 级别映射与 DEBUG 过滤；固定单事件时刻、轮转及追加是否一致。
- 保留窗口边界、名字/类型保护、目录锁释放；不能误删目录、未来日期或不相关文件。
- 字段转义和异常安全格式不得调用 getMessage/toString 输出秘密；循环异常不能无限递归。
- I/O 故障不被 JUL 吞掉；close 失败仍释放其他资源，失败后恢复路径可重试。
- 不扩大日志脱敏、自动业务接入、定时/大小轮转的承诺。

## Execution Ledger

- Pre-flight: Task 1 API 由 Task 2 示例消费，清理边界和安全输入以 Spec 为准，无冲突。
- 基线：上轮 package 236 tests，0 failures/errors/skips。
- 用户本轮新增规则：每两个模块一次本地提交；此次提交覆盖尚未提交的模块一至十二，后续检查点为十四、十六等。
- Task 1 RED：测试修正 JDK 17 ExecutorService 不支持 AutoCloseable 后重跑，仅因 AppLogger 缺失失败；target/runtime-logging-red.log。
- Task 1 GREEN：16 tests 全通过；target/runtime-logging-green.log。
- Task 2 package：27 类、252 tests，0 failures/errors/skips，BUILD SUCCESS；target/runtime-logging-package.log。
- 提交预检：117 个未跟踪文件均处于约定范围；target/.mvn-cache/data/logs 忽略规则有效，原 SQL/Word/设计图无差异。
- Final review：发现一项 Important，目录遍历可能抛未受检 DirectoryIteratorException，绕过 facade 的 IOException 契约。
- Final fixed：directoryTraversalFailureBecomesIOExceptionAndRetainsCloseFailure 先复现错误异常类型，再修复还原原始 IOException 并转移 suppressed；最终全量 253/253 通过，target/runtime-logging-review-red.log 与 runtime-logging-package.log 留存执行证据。
- 审查范围决定：真实磁盘、跨进程实测、符号链接竞态和掉电持久性不扩张承诺；文件系统异常与安全元数据仍需后续真实环境接入验证。
- 审查范围决定：本次审查聚焦模块十二；一至十一沿用前次审查/验收并通过本次全量回归，Git 范围由主执行者单独核对。
- 审查范围决定：审查者不重跑 Maven；主执行者完整日志和 27 份测试报告为 253 项通过的证据，修复后不重复独立审查。
