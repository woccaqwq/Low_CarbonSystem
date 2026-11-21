# Iteration Two Word Design Document Plan

> **For agentic workers:** Apply documents:documents for authoring and render inspection; use verification-before-completion before the authorized local commit.

**Goal:** 形成独立的迭代二 Word 系统设计说明书，并将模块十三、十四作为第二次本地检查点提交。
**Sources:** 1b3ae6f 的 Java/SQL 基线、模块一至十二说明、模块十三图册/接口清单、迭代一设计与需求；不修改原 Word。

## Design

- 正式技术说明书，黑色标题，A4 正文，中文可读字号，固定层级和页码；表格重复标题、灰色细边、适当留白。
- 正文使用适合纸面阅读的简化领域图和关键流程摘要，原十张全图在图册中保持完整。
- 重点保留实现边界、九接口 112 签名、事务回滚与行数语义、信用归属、配置原文、CSV/日志故障边界。
- 不伪造小组评审、Service/GUI 完成情况、JDK17运行验收或本轮 Maven 测试结果。
- 交付 `文本文档/04_迭代2_系统设计说明书.docx`，作者工具源在 scripts；渲染中间文件在 target 下，不加入 Git。

## Steps

- [x] 使用已配置运行时的 python-docx 构建新 Word，保留原设计文档。
- [x] 原生成轮次运行文档技能标记与规范渲染器；Windows 缺少随附 LibreOffice，诊断后用本机 Word 只读渲染，不调用用户安装的 LibreOffice。
- [x] 渲染页面图片并逐页查看，修正排版，核对正文/附录内容和图表。
- [x] 独立只读审查内容一次，更新模块十四和检查点说明。
- [x] 校验图册及链接，确认模块十三/十四的本地提交范围；实际提交结果以 Git 历史为准，不推送。

## Verification

不改生产 Java 或数据库，无理由重跑原 253 项测试。以模块十二的测试基线、模块十三图检查、新 Word 的结构/渲染与内容核对为证据；提交后验证干净工作区。

## Ledger

- 用户明确授权继续 Word 整理，并要求每两模块提交一次；此次检查点覆盖模块十三、十四。
- 本机提供 python-docx/Pillow、Node/sharp，Windows 依赖包没有 LibreOffice，已只读确认 Word COM 注册可用。原 Word 保持历史基线。
- 2026-11-21 接续中断任务：图册检查通过（10 图、9 DAO、112 方法、49 源类型），SVG XML 与本地链接通过；独立只读复核未发现阻塞问题。
- 本次修正 Word 第 3 页简化领域图的框间距及反向连线端点标注，重新生成并用 Word 后台只读导出 17 页；全部页面逐页检查通过，附录 112 个方法与接口清单逐项匹配。
- 本次没有修改生产 Java、原 SQL 或迭代一材料，没有重跑 Maven；模块十二的 253 项测试仍是历史基线。检查输出位于被忽略的 target/design-word/。
