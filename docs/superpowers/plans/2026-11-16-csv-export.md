# CSV Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 提供可复用的 UTF-8 BOM CSV 文件导出，供统计业务调用。
**Architecture:** 无状态 CsvExporter，类型明确的通用表格输入，单次迭代、同目录暂存、无覆盖提交、异常清理。
**Tech Stack:** Java 17 JDK API、JUnit 5.11.4，既有 Maven 工程，无新依赖。
**Spec:** docs/superpowers/specs/2026-11-16-csv-export-design.md；FR-17 与设计 util/CsvExporter。

## Global Constraints

- 保持数据库、DAO、Service 契约和依赖不变，不增加业务计算或 GUI。
- API/类型集合、BOM/CRLF/转义、公式前缀处理、严格编码与不覆盖语义以 Spec 为准。
- 文件只写调用方指定位置；临时文件在同目录，失败清理只作用于本次临时文件。
- 沿用现有本地分支及未提交内容，不自动提交或推送。

## Task 1: 测试与实现

Files: util/CsvExporter.java、test/util/CsvExporterTest.java。
Interfaces: export(Path,List<String>,Iterable<? extends List<?>>):Path throws IOException；公开无参构造器。

- [x] 先写格式、中文/特殊字符、数值、空值、输入边界、流式数据、统计 DAO 接入及失败不覆盖/清理测试。
- [x] 运行指定测试确认 CsvExporter 缺失失败。Expected: 缺失工具类，而非既有测试失败。
- [x] 最小实现，再跑指定测试。Expected: 新增测试全部通过。

## Task 2: 接入说明与验证

Files: docs/development/iteration2-module11.md、README.md，验收记录追加后续状态。
Interfaces: 示例用当前 CategoryStat/StatCounts 映射数字列；强调调用方授权、公式前缀文本转换及未实测 Excel 界面。

- [x] 全量离线 package 成功。Expected: 原 220 项及新增测试通过。
- [x] 独立只读审查一次，处理重要问题，更新交接说明并结束本模块。

## Review Focus

- 暂存与无覆盖提交是否保护原文件，包括目标在导出期间出现的冲突；失败清理不掩盖主异常。
- 文本转义和数值输出、公式前缀处理是否按字段边界工作，不能把 typed 负数变成文本。
- BOM、CRLF、Unicode 与严格编码；流式输入只迭代一次，行宽错误不能留下目标半成品。
- 不把 CsvExporter 等同完整 StatService.exportCsv 或 Excel GUI 验收，不承诺跨应用通用公式防护。

## Execution Ledger

- Pre-flight: Task 1 API 由 Task 2 示例消费，签名与异常边界按 Spec，无冲突。
- 基线：上轮 package 220 tests，0 failures/errors/skips。
- 不使用基于提交范围的任务脚本或自动提交；在本文记录测试证据。
- Task 1 RED：CsvExporter 类缺失导致指定测试编译失败；target/csv-export-red.log。
- Task 1 GREEN：新增 16 tests 通过；target/csv-export-green.log。
- Task 2 package：26 类、236 tests，0 failures/errors/skips，BUILD SUCCESS；target/csv-export-package.log。
- 文件发布文案明确写入阶段与移动阶段的区别：写入完成前不发布目标，移动语义遵从文件系统提供者，不承诺掉电或通用原子发布保证。
- 产物检查：CsvExporter 已打入 JAR，major version 61（Java 17）；文档本地链接与 git diff --check 通过。
- Final review：独立只读审查通过，Critical/Important/Minor 均无问题，无需修复或复审。
- 审查范围决定：Excel GUI、跨应用/二次编辑后的公式行为未验收，保留为后续人工检查，不扩大文本前缀处理承诺。
- 审查范围决定：任意文件系统提供者的竞争发布与掉电恢复不在本工具保证内；原文件默认不请求覆盖，移动失败状态遵从 JDK/提供者，文档保留限制。
- 审查范围决定：清理失败分支已静态核对 suppressed 保存逻辑，未注入实际删除失败；若文件系统拒绝清理可能遗留暂存文件，主异常仍保留。
- 审查范围决定：StatService.exportCsv、权限、报表范围由后续业务集成验证；本轮只有通用文件工具。
- 审查范围决定：审查者不复跑 Maven，以主执行者的全量日志与报告为执行证据；已有 236 项通过，不重复无变化测试。
- 本模块结束，保留本地改动，未提交或推送。
