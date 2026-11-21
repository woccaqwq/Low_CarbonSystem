# Iteration Two Design Sync Plan

> **For agentic workers:** Use superpowers:executing-plans for this documentation module.

**Goal:** 同步迭代二 UML、事务时序和接口交接材料，反映模块一至十二的真实代码。
**Architecture:** 三张类图、六张业务接入时序图和一张状态图；图模型生成可编辑源、SVG/PNG，DAO 签名与实体字段从 Java 源自动提取。
**Spec:** 用户“继续”同步设计的授权；源码基线 1b3ae6f；原需求/设计与模块验收文档。

## Constraints

- 保留迭代一 Word、PNG、SQL，不把尚未实现的 Service/GUI 画成现有实现。
- 明确价格 double、可空分类、每次流转最多两条评价、连接重载、事务失败回滚、双方成交 +1 与收评人的评分增量。
- 运行日志独立于数据库事务；CSV、SessionUser 与配置读取的边界保持真实。
- 本模块只改图与文档/生成脚本，不改生产 Java 或数据库；不新增 Maven 依赖。
- 用户约定每两模块提交；模块十三不提交，等模块十四完成后一起提交。

## Task 1: 源码与图模型

- [x] 对照原图、当前源码和 SQL，生成九 DAO 的完整方法清单及十六实体字段清单。
- [x] 编写领域、DAO 依赖、值对象/工具类图和状态图。
- [x] 编写登录、检索、成交、评价、统计导出、违规下架六张时序图，标明拟接入 Service 和失败路径。

## Task 2: 渲染与交接

- [x] 使用已配置的本地 Graphviz WASM 与 SVG 渲染工具生成 SVG/PNG，保留可编辑源。
- [x] 校验方法/类名、关系、事务顺序、图片布局及本地链接；独立只读核对一次。
- [x] 更新 README、模块十三和接口变更清单，保留 Git 基线与待模块十四合并提交的记录。

## Verification

本模块不改业务行为，不新增形式化单元测试，也不无故重跑 Maven。检查源图引用、接口成对连接重载、图渲染、图片可读性、链接和 git diff --check。
已有 253 项测试的通过证据属于模块十二的代码基线，不写成此次重新运行结果。

## Ledger

- Pre-flight: Java 源为类型/签名权威，原设计与需求为业务规则依据；Service 以“待实现”标注，无接口修改决定。
- 图册共 10 张：3 类图、6 时序图、1 状态图；SVG/PNG 与可编辑源已生成，49 个 Java 源文件参与核对与摘要。
- 自动校验：9 DAO、56 普通 + 56 Connection，共 112 个签名成对；实现/BaseDao 映射/工厂方法匹配。16 个实体字段独立提取。
- 视觉检查修正了初版标签越界、额外空白列和说明遮挡；全部十张预览已查看。
- 独立核对发现 Important：笼统把更新 0 行当失败。已对照 FlowRecordDaoImpl 限定为必需单行状态/信用更新，明确 cancelOtherPending 返回 0 合法，并重建图与说明。
- 同次核对的 CSV 清理措辞也按源码修正为尝试清理、失败附 suppressed；无生产代码修复或新业务需求。
- 统计时序明确跨年逐年调用月份接口；登录快照使用事务内新读用户，最终认证/并发策略仍留给 Service。
- 验证：图生成及 --check、十份 SVG XML、本地 Markdown 链接、git diff --check 通过。Java、pom、原 SQL、Word、PNG 均无改动，不重跑 Maven。
- 审查范围决定：Mermaid 引擎兼容性未单独运行，主要预览由 DOT/时序模型生成；原 Word 未发布新冻结版本，业务和 GUI 尚未实现。相应限制已写入图册。
- 模块十三完成，按用户节奏保留未提交修改，待模块十四一起本地提交。
