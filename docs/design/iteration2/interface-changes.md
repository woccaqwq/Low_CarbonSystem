# 迭代二接口变更与交接

本文记录相对迭代一设计文档的实现补充。状态为当前代码说明，需向成员 2、3 同步；不是小组三方确认记录，也不改变原 Word 的冻结版本。

2026年11月21日范围更新：用户已明确本次仅完成成员一的数据层、公共工具和配套设计交接，统计业务交给成员二。当前完成状态与最新测试证据见 [成员一交付与验收清单](../../development/iteration2-member1-completion.md)。本次不修改 Service 契约或表结构，不代替小组三方评审。

| 范围 | 当前实现 | 接入影响 |
| --- | --- | --- |
| 原有 User/Item/Flow/Review/Stat 方法 | 原设计第 4.2 节明确列出的普通方法保留 | 可继续按原普通签名调用；多 DAO 原子操作需改用同一连接重载 |
| Connection 重载 | 九 DAO 共 56 个 Connection 首参数方法 | Service 控制事务；测试替身/内存实现需要补接口方法 |
| Category/Favorite/OperationLog | 原文仅给数量，现已补齐具体接口 | 以各模块说明与当前 Java 源核对参数顺序和返回语义 |
| SysConfigDao | 新增 findByKey/findAll 及连接重载 | 只读、无缓存、不回退默认；配置格式/范围由使用方校验 |
| 流转补充 | cancelOtherPending、cancel(reason)、参与人/买家/卖家分页 | 成交状态等必需单行更新为 0 时中止；cancelOtherPending 数量可为 0，只取消同物品其他 PENDING |
| 评价补充 | findById、收到评价分页，保留均分/exists | 收评人按 toUserId；不自动调整信用或检查资格 |
| 统计补充 | StatQuery、StatCounts、StatOverview、范围重载 | 时间/用户口径明示；StatReport 和估算/导出 Service 尚未实现 |
| SessionUser | 不可变五字段，无凭据，from(User) | 替换直接把 User 暴露给界面；不提供 SessionManager 或认证状态 |
| 业务异常 | ErrorCode、BusinessException、ValidationException(field) | 验证异常先定位字段，再处理一般业务错误；技术异常仍独立 |
| CsvExporter | export(Path,List<String>,Iterable rows) throws IOException | 由业务映射数字列/脱敏字段、处理旧文件冲突和 I/O 错误；不是 StatService |
| AppLogger | 四级运行日志，目录锁、日期文件、七天清理 | App 统一持有/关闭；元数据须安全；不替代事务内业务日志 |

九个 DAO 的实际实现类、BaseDao 映射类型、工厂方法及完整签名由 [DAO 清单](dao-api.md) 自动生成。实体全部实例字段见 [实体清单](entity-fields.md)。

## 保留的重要调用约定

- insert 返回新主键，不把编号回填到原输入对象；收藏重复插入返回已存在主键，日志重复写入仍新增。
- 页码从 1 起、每页 10–100；极大 offset 用 long。PageResult 的容器不可变，User/Item 等元素仍可变。
- 数据访问故障是 DataAccessException，保留 cause。Service 事务应 catch RuntimeException 与 SQLException，不能只 catch SQLException。
- DataAccessException 构造器仍是 `(String, Throwable)`，原设计中的 `(ErrorCode, Throwable)` 不是现有 API。
- Optional.empty 表示不存在；SysConfig 的空字符串值与不存在不同；null 查询范围与匿名用户筛选不可混淆。
- ErrorCode 的字符串提示不自动转换 SQL/文件错误；导出失败专用业务码仍待定义，不能直接拿 DB_ERROR 代替所有文件错误。

## 尚需决定的设计事项

| 事项 | 当前情况 | 后续动作 |
| --- | --- | --- |
| 统计与公共工具分工 | 用户本次明确统计业务交给成员二，成员一负责统计 DAO 与通用工具 | 成员二接入 StatService/StatReport/估算与报表 CSV；成员一提供接口说明，仍待队友实际接入确认 |
| 分类停用与删除策略 | schema 无停用字段，删除分类将物品分类置空；FR-20 要求被引用时拒绝 | Service 在同事务检查引用；停用先形成结构迁移方案 |
| 所有者查看下架原因 | 日志无独立对象外键与普通用户受限查询契约 | 设计对象关联、授权查询或新字段，不能公开整个日志表 |
| 会话与运行入口 | SessionUser 已有，SessionManager/App/GUI 未实现 | 明确登录状态、权限刷新、工具关闭与后台执行 |
| 原 Word/UML 发布版本 | 本目录已同步实现，原件仍为迭代一 | 小组评审后再将本材料纳入正式设计版本；本轮不伪造评审结论 |

本轮没有修改公共 Java 签名、表结构或原 Word/PNG，仅补充图与交接说明。代码基线的 253 项测试结果来自模块十二；此文档模块未重跑 Maven。
