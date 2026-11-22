# 成员二迭代二业务层交付说明

交付日期：2026-11-22。实现人：刘高杰（成员 2，业务逻辑与测试负责人）。
实现基线：成员一数据层 `main`（数据层 253 项测试通过）。本分支：`feature/iteration2-service`。

## 实现范围

依据《系统设计说明书》第 4.3 节冻结的 Service 接口与第 10 章交接清单，以及成员一
[交付清单](iteration2-member1-completion.md) 中“统计业务交给成员二”的确认，本次实现完整的六类业务服务：

| 服务 | 实现 | 业务规则要点 |
| --- | --- | --- |
| UserService | UserServiceImpl | 注册用户名/口令规则、口令加盐哈希、失败 5 次锁定 10 分钟、登录时间/日志事务、改密作废旧口令 |
| ItemService | ItemServiceImpl | 发布/修改/下架的发布人校验、分类存在性、状态守卫、收藏幂等、发布与下架日志 |
| FlowService | FlowServiceImpl | 意向在架/非本人/防重复校验；成交同事务完成流转、物品、其他意向、双方信用与双方日志；取消权限校验 |
| ReviewService | ReviewServiceImpl | 成交双方资格、七天窗口、每人一次、接收人由流转推导；4–5 星 +2、1–2 星 −3、3 星不变 |
| StatService | StatServiceImpl | 单一读事务组装报表、跨年月份合并、配置系数校验、UTF-8 BOM CSV 导出、减碳估算 |
| AdminService | AdminServiceImpl | 角色在事务内复核、违规下架原因入日志、禁止禁用管理员自身与禁用管理员账号 |

配套支撑：`ItemForm`、`StatReport` 值对象，`SessionManager` 内存会话快照，
`ServiceFactory` 工厂，`TxSupport` 统一事务模板。

## 事务与接入约定

- 事务模板遵循[迭代二数据层验收](iteration2-data-acceptance.md)的更正版本：Service 打开一个连接、
  关闭自动提交、同一连接调用 DAO 的 Connection 重载；正常时 commit，捕获
  `RuntimeException | SQLException` 后 rollback（失败作为 suppressed），try-with-resources 关闭连接，
  不恢复 autoCommit。DAO 的 `DataAccessException` 是非受检异常，不会被只捕获 SQLException 的旧模板漏掉。
- 成交与评价按[接入时序图](../../docs/design/iteration2/README.md) 06、07：成交中状态/物品/信用等必需
  单行更新必须命中 1 行，`cancelOtherPending` 允许 0 行；评价只影响接收人信用，成交双方各 +1 属于成交事务。
- 管理员操作不信任界面传入的 `SessionUser`，在事务内重新读取账号并复核角色与状态。
  统计个人/平台范围由会话身份决定，不把 `User` 的哈希与盐交给界面或 CSV。

## 相对冻结契约的向后兼容新增

为满足业务层错误提示，向成员一的 `ErrorCode` 枚举新增以下常量，已有 13 个常量的名称、顺序前缀与提示保持不变，
`ErrorCodeTest` 全部通过：

- `ITEM_NOT_FOUND`、`CATEGORY_NOT_FOUND`、`USER_NOT_FOUND_BY_ID`
- `LOGIN_LOCKED`（登录失败锁定）
- `CSV_EXPORT_FAILED`（导出失败专用码，未用 DB_ERROR 代替文件错误）
- `CONFIG_INVALID`（统计系数缺失或损坏，不静默回退默认值）

> 按分工文档第 5 章变更流程，此新增属于公共契约扩展，需小组三方在接口评审中确认；本次未修改任何已有签名或表结构。

## 验证

| 检查 | 结果 |
| --- | --- |
| 全量 Maven test | 277 项测试，失败 0、错误 0、跳过 0（成员一 253 + 成员二新增 24） |
| 全量 Maven package | BUILD SUCCESS，产出 `target/low-carbon-system-0.2.0-SNAPSHOT.jar` |
| 新增测试类 | UserServiceTest(6)、ItemServiceTest(5)、FlowServiceTest(6)、ReviewServiceTest(4)、StatAndAdminServiceTest(3) |
| 数据层回归 | 成员一全部测试与跨 DAO 验收保持通过，未修改生产 DAO/实体/工具 |

本机验证命令（首次需联网下载依赖）：

```powershell
mvn -B test
mvn -B package
```

## 交接边界与后续事项

- 本轮未修改迭代一 Word、SQL 脚本与原有设计图；成员一的 10 张迭代二图册与说明书继续作为设计基线。
- GUI、应用启动入口、后台任务与可执行 Jar 的双击运行属于成员三范围，尚未集成。
- 报表 CSV 已用 `CsvExporter` 产出 UTF-8 BOM 文件，Excel 人工打开验收与覆盖确认策略仍待集成阶段处理。
- 分类停用/删除策略、违规下架原因的所有者可见接口、旧库迁移为迭代三设计协作事项，本次未变更表结构。
- 统计个人范围需由 `SessionManager` 的当前身份传入；当前 `StatService.buildReport(from, to)` 采用全平台口径，
  个人范围组装方法在 GUI 接入时按会话补充，避免仅信任界面参数。
