// Editable diagram model. Generated previews are rebuilt by render-diagrams.cjs.
const node = (id, file, methods = [], tag = "") => ({ id, file, methods, tag });
export const classDiagrams = [
  {
    id: "01-domain", title: "迭代二领域类图", subtitle: "字段来自当前 Java 源；关系表示业务关联，非对象自动级联。",
    rows: [["User", "Item", "Category"], ["Favorite", "FlowRecord", "Review"]],
    nodes: [
      node("User", "entity/User.java", ["isAdmin", "isEnabled", "adjustCredit"]),
      node("Item", "entity/Item.java", ["publish", "offline", "markFlowed"]),
      node("Category", "entity/Category.java", ["isRoot"]),
      node("Favorite", "entity/Favorite.java"), node("FlowRecord", "entity/FlowRecord.java", ["confirm", "cancel"]),
      node("Review", "entity/Review.java", ["validateScore"])
    ],
    relations: [
      ["User", "Item", "owner", "1", "0..*"],
      ["Category", "Item", "category", "0..1", "0..*"],
      ["Category", "Category", "parent / children", "0..1", "0..*"],
      ["User", "Favorite", "user", "1", "0..*"],
      ["Item", "Favorite", "item", "1", "0..*"],
      ["Item", "FlowRecord", "item", "1", "0..*"],
      ["User", "FlowRecord", "seller / buyer", "1", "0..*"],
      ["FlowRecord", "Review", "reviews", "1", "0..2"],
      ["User", "Review", "from / to", "1", "0..*"]
    ],
    note: "Item.price 为 double；categoryId / parentId 为 Integer，可为 null。查询昵称/信用/标题是当前投影。\nReview(flowId, fromUserId) 唯一；最多两条评价需 Service 校验交易双方，数据库单独不保证只有两名评价人。"
  },
  {
    id: "02-data-access", title: "迭代二数据访问与查询类图", subtitle: "接口展示普通方法名称，完整 112 个签名见 dao-api.md；每个普通方法都有 Connection 重载。",
    rows: [["DaoFactory", "UserDaoImpl", "BaseDao", "DBUtil"], ["UserDao", "CategoryDao", "ItemDao"],
      ["FavoriteDao", "FlowRecordDao", "ReviewDao"], ["StatDao", "OperationLogDao", "SysConfigDao"],
      ["ItemQuery", "PageResult", "SortStrategy"], ["TimeDescStrategy", "PriceAscStrategy", "CreditDescStrategy"]],
    nodes: [
      ...["User", "Category", "Item", "Favorite", "FlowRecord", "Review", "Stat", "OperationLog", "SysConfig"]
        .map(prefix => node(prefix + "Dao", "dao/" + prefix + "Dao.java", [], "interface")),
      node("UserDaoImpl", "dao/impl/UserDaoImpl.java", [], "implementation sample"),
      node("BaseDao", "dao/impl/BaseDao.java", ["withConnection", "onConnection", "query", "update"], "abstract"),
      node("DBUtil", "util/DBUtil.java", ["getInstance", "forPath", "getConnection"]),
      node("DaoFactory", "dao/factory/DaoFactory.java", ["getUserDao", "getStatDao", "getSysConfigDao"]),
      node("ItemQuery", "entity/ItemQuery.java", ["builder", "getSort", "getOrderBy"], "immutable"),
      node("PageResult", "entity/PageResult.java", ["getTotalPages", "isEmpty"], "immutable container"),
      node("SortStrategy", "strategy/SortStrategy.java", ["toOrderBy"], "interface"),
      ...["TimeDescStrategy", "PriceAscStrategy", "CreditDescStrategy"].map(name => node(name, "strategy/" + name + ".java"))
    ],
    relations: [
      ["UserDaoImpl", "UserDao", "implements", "", "", "implements"],
      ["UserDaoImpl", "BaseDao", "extends", "", "", "extends"],
      ["BaseDao", "DBUtil", "opens ordinary connections", "", "", "dependency"],
      ["DaoFactory", "UserDao", "returns 9 DAO interfaces (sample)", "", "", "dependency"],
      ["ItemQuery", "SortStrategy", "validates and freezes ORDER BY", "", "", "dependency"],
      ...["TimeDescStrategy", "PriceAscStrategy", "CreditDescStrategy"].map(name => [name, "SortStrategy", "implements", "", "", "implements"])
    ],
    note: "以真实 UserDaoImpl 示范实现关系，其余八个 XxxDaoImpl 同样实现对应接口并继承 BaseDao；完整映射见 dao-api.md。\nBaseDao.onConnection 不关闭、提交或回滚外部连接。Factory 返回九个接口的惰性单例，不持有长连接。\nItemDao.search(ItemQuery) 返回 PageResult<Item>；ItemQuery 冻结安全 orderBy。PageResult 不深拷贝可变实体。"
  },
  {
    id: "03-shared", title: "迭代二会话配置统计与异常类图", subtitle: "实线为类型关系，虚线为使用或复制关系；下列类型均已有代码。",
    rows: [["SessionUser", "OperationLog", "SysConfig", "StatQuery"], ["CategoryStat", "MonthStat", "StatOverview", "StatCounts"],
      ["ValidationException", "BusinessException", "ErrorCode", "DataAccessException"], ["CsvExporter", "AppLogger", "DailyLogHandler", "SafeLogFormatter"]],
    nodes: [
      node("SessionUser", "entity/SessionUser.java", ["from", "isAdmin"], "immutable snapshot"),
      node("OperationLog", "entity/OperationLog.java"), node("SysConfig", "entity/SysConfig.java", [], "immutable"),
      node("StatQuery", "entity/StatQuery.java", ["builder"], "immutable"),
      node("StatCounts", "entity/StatCounts.java", ["getItemCount", "getFlowCount"], "immutable"),
      node("CategoryStat", "entity/CategoryStat.java", [], "immutable"),
      node("MonthStat", "entity/MonthStat.java", [], "immutable"),
      node("StatOverview", "entity/StatOverview.java", [], "immutable"),
      node("ErrorCode", "exception/ErrorCode.java", ["getMessage"], "enum"),
      node("BusinessException", "exception/BusinessException.java", ["getCode"]),
      node("ValidationException", "exception/ValidationException.java", ["getField"]),
      node("DataAccessException", "exception/DataAccessException.java"),
      node("CsvExporter", "util/CsvExporter.java", ["export"]),
      node("AppLogger", "util/AppLogger.java", ["log", "close"]),
      node("DailyLogHandler", "util/DailyLogHandler.java", ["publish", "close"], "internal"),
      node("SafeLogFormatter", "util/SafeLogFormatter.java", ["format"], "internal")
    ],
    relations: [
      ["CategoryStat", "StatCounts", "counts", "0..*", "1"],
      ["MonthStat", "StatCounts", "counts", "0..*", "1"],
      ["StatOverview", "StatCounts", "counts", "0..*", "1"],
      ["ValidationException", "BusinessException", "extends", "", "", "extends"],
      ["BusinessException", "ErrorCode", "fixed message", "", "", "dependency"],
      ["AppLogger", "DailyLogHandler", "owns / closes", "", "", "composition"],
      ["DailyLogHandler", "SafeLogFormatter", "formats", "", "", "dependency"]
    ],
    note: "SessionUser.from(User) 只复制五个安全字段，不是身份凭证；OperationLog.userId 可为 null。\nBusinessException 与 DataAccessException 均为 RuntimeException，彼此不继承。CSV / AppLogger 的 I/O 故障为 IOException。\nStatCounts 区分物品与意向；配置按原文读取；文件日志独立于数据库 operation_log。"
  }
];

const p = (id, label, planned = false) => ({ id, label, planned });
const m = (from, to, text, reply = false) => ({ kind: "message", from, to, text, reply });
const note = text => ({ kind: "note", text });
const alt = text => ({ kind: "alt", text });
const otherwise = text => ({ kind: "else", text });
const end = () => ({ kind: "end" });

export const sequences = [
  {
    id: "04-login", title: "登录接入时序", subtitle: "UserService / GUI 尚未实现；图示推荐接入顺序，DAO 和工具已有实现。",
    participants: [p("UI", "登录界面（待实现）", true), p("S", "UserService（待实现）", true), p("U", "UserDao"),
      p("P", "PasswordUtil"), p("D", "DBUtil"), p("C", "Connection"), p("L", "OperationLogDao"), p("V", "SessionUser")],
    events: [m("UI", "S", "login(username, rawPassword)"), note("先做输入规则与失败次数/锁定检查。口令仅在认证内存流程使用，不写日志或 SessionUser。"),
      m("S", "U", "findByUsername(username)"), m("U", "S", "Optional<User>", true),
      m("S", "P", "verify(rawPassword, hash, salt)"),
      alt("不存在、口令错误、禁用或锁定"), m("S", "UI", "固定业务错误；不存在与口令错误使用同一提示", true),
      otherwise("认证成功且账号可用"), m("S", "D", "getConnection()"), m("D", "S", "conn（首次空库自动初始化）", true),
      m("S", "C", "setAutoCommit(false)"), m("S", "U", "findById(conn, userId)；再次确认账号可用"),
      m("S", "V", "from(currentUser)；用事务内新读用户构造快照"),
      m("S", "U", "updateLastLogin(conn, userId, now)；检查 1 行"),
      m("S", "L", "insert(conn, OperationLog(userId, LOGIN, 安全摘要))"),
      alt("更新命中且所有写入成功"), m("S", "C", "commit()"), otherwise("RuntimeException 或 SQLException"),
      m("S", "C", "rollback()；回滚失败加入 suppressed"), end(),
      m("S", "C", "close()（try-with-resources）"),
      m("S", "UI", "仅成功返回安全快照；失败抛受控异常", true), end(),
      note("SessionManager、失败锁定、GUI 错误展示与 Service 事务编排仍待实现。快照不是实时权限来源。")]
  },
  {
    id: "05-search", title: "物品检索接入时序", subtitle: "ItemQuery / DAO / JDBC 已实现；GUI 与 Service 为待接入组件。",
    participants: [p("UI", "列表界面（待实现）", true), p("S", "ItemService（待实现）", true), p("Q", "ItemQuery / SortStrategy"),
      p("D", "ItemDao"), p("B", "DBUtil / Connection"), p("SQL", "SQLite")],
    events: [m("UI", "S", "search(筛选条件)"), m("S", "Q", "builder().keyword(...).sort(...).build()"),
      note("默认 status=ON_SALE；status(null) 才不限状态。分页 1 起、每页 10–100。排序白名单验证后冻结。"),
      alt("非法范围、分页或排序"), m("Q", "S", "IllegalArgumentException", true), m("S", "UI", "映射字段校验错误", true),
      otherwise("有效查询"), m("S", "D", "search(query)"), m("D", "B", "getConnection()"),
      m("D", "SQL", "PreparedStatement：COUNT + 组合筛选"), m("SQL", "D", "total", true),
      m("D", "SQL", "PreparedStatement：JOIN + 安全排序 + LIMIT/OFFSET"), m("SQL", "D", "当前昵称、信用、分类投影", true),
      m("D", "B", "close()（普通方法持有的独立连接）"), m("D", "S", "PageResult<Item>", true),
      m("S", "UI", "获准范围内的列表与分页元数据", true), end(),
      note("计数与列表是两次查询。需要严格一致快照时，Service 开启读事务并调用 search(conn, query)。")]
  },
  {
    id: "06-deal", title: "确认成交事务接入时序", subtitle: "FlowService 尚未实现；同连接原子能力已通过数据层集成测试。",
    participants: [p("S", "FlowService（待实现）", true), p("D", "DBUtil / Connection"), p("F", "FlowRecordDao"),
      p("I", "ItemDao"), p("U", "UserDao"), p("L", "OperationLogDao")],
    events: [m("S", "D", "getConnection(); setAutoCommit(false)"), m("S", "F", "findById(conn, flowId)"),
      m("S", "I", "findById(conn, itemId)"), m("S", "U", "findById(conn, operatorId)"),
      note("事务内校验当前身份/账号、持有人、物品在架及流转 PENDING。不能只信任 GUI 或旧 SessionUser。"),
      alt("校验和每一步写入成功"), m("S", "F", "updateStatus(conn, flowId, DEAL)"),
      m("F", "S", "必须命中 1 行；0 为终态或成交冲突", true),
      m("S", "I", "updateStatus(conn, itemId, FLOWED)；检查 1 行"),
      m("S", "F", "cancelOtherPending(conn, itemId, flowId)"),
      m("F", "S", "取消数量允许为 0（没有其他待确认意向）", true),
      m("S", "U", "updateCreditScore(conn, sellerId, +1)；检查 1 行"),
      m("S", "U", "updateCreditScore(conn, buyerId, +1)；检查 1 行"),
      m("S", "L", "insert(conn, 卖家 FLOW_CONFIRM 日志)"), m("S", "L", "insert(conn, 买家 FLOW_CONFIRM 日志)"),
      m("S", "D", "commit()"), otherwise("业务拒绝、必需单行更新未命中，或 RuntimeException / SQLException"),
      m("S", "D", "rollback()；回滚失败附 suppressed"), end(), m("S", "D", "close()（try-with-resources）"),
      note("JDBC 错误由 DAO 包装为 DataAccessException，属于 RuntimeException。只有提交成功才返回成功。文件日志不加入此数据库事务。")]
  },
  {
    id: "07-review", title: "评价与信用事务接入时序", subtitle: "ReviewService 尚未实现；评价、唯一约束与原子信用增量已有实现。",
    participants: [p("S", "ReviewService（待实现）", true), p("D", "DBUtil / Connection"), p("F", "FlowRecordDao"),
      p("U", "UserDao"), p("R", "ReviewDao"), p("L", "OperationLogDao")],
    events: [m("S", "D", "getConnection(); setAutoCommit(false)"), m("S", "F", "findById(conn, flowId)"),
      m("S", "U", "findById(conn, currentUserId)"), m("S", "R", "existsByFlowAndFrom(conn, flowId, fromId)"),
      note("校验 DEAL、当前操作者为交易双方之一、7 天窗口、评分 1–5、内容最多 200 码点；toId 由交易记录推导。"),
      alt("校验与写入成功"), m("S", "R", "insert(conn, Review(flowId, fromId, toId, score, content))"),
      m("S", "U", "updateCreditScore(conn, toId, delta)；检查 1 行"),
      note("只调整本条评价的接收人：4–5 星 +2，1–2 星 -3，3 星不变。成交双方各 +1 在成交事务中完成。"),
      m("S", "L", "insert(conn, REVIEW_SUBMIT 安全日志)"), m("S", "D", "commit()"),
      otherwise("资格失败、重复约束或 RuntimeException / SQLException"), m("S", "D", "rollback()；回滚失败附 suppressed"),
      end(), m("S", "D", "close()（try-with-resources）"),
      note("exists 是业务提示辅助，数据库 UNIQUE(flow_id, from_user_id) 才是重复写入最终约束。DAO 不自动更新信用。")]
  },
  {
    id: "08-stat-export", title: "统计与 CSV 导出接入时序", subtitle: "StatService / StatReport / GUI 尚未实现；统计、配置和 CSV 工具已有实现。",
    participants: [p("UI", "统计界面（待实现）", true), p("S", "StatService（待实现）", true), p("D", "DBUtil / Connection"),
      p("T", "StatDao"), p("C", "SysConfigDao"), p("X", "CsvExporter")],
    events: [m("UI", "S", "buildReport(from, to)"), note("从受信任当前身份决定个人/平台范围。日期与用户形成不可变 StatQuery。"),
      m("S", "D", "getConnection(); setAutoCommit(false)"), m("S", "T", "getOverview(conn, query)"),
      m("S", "T", "countByCategory(conn, query)"), { kind: "loop", text: "需要展示的每个年份；跨年范围逐年读取" },
      m("S", "T", "countByMonth(conn, year, query)；单次返回 12 个月"), end(),
      m("S", "C", "findByKey(conn, stat.co2.per_item_kg)"),
      alt("配置和所有读取有效"), note("Service 校验系数格式、非负及有限值；若启用减碳估算，按明确已流转/成交口径计算，不乘全部意向数。"),
      m("S", "D", "commit()"), otherwise("配置缺失/损坏或 RuntimeException / SQLException"),
      m("S", "D", "rollback()；保留原异常"), end(), m("S", "D", "close()"),
      m("S", "UI", "仅成功时返回同一份报表快照", true), { kind: "opt", text: "已有有效报表且用户选择导出" },
      m("UI", "S", "exportCsv(当前报表, 目标新路径)"),
      note("导出不重新扩大数据范围。Service 将选定字段映射为表头和数值行，GUI 后台执行。"),
      m("S", "X", "export(target, headers, rows)"),
      alt("写入与发布成功"), m("X", "S", "Path（UTF-8 BOM；不请求覆盖已有目标）", true), m("S", "UI", "导出成功路径", true),
      otherwise("IOException 或输入格式错误"), m("X", "S", "尝试清理暂存文件；失败附 suppressed，保留原异常", true),
      m("S", "UI", "受控导出失败提示（业务错误码待定义）", true), end(), end(),
      note("CsvExporter 不依赖 StatReport，尚未实测 Excel GUI。超长数字识别和二次保存行为不由 CSV 工具保证。")]
  },
  {
    id: "09-admin-offline", title: "违规下架事务接入时序", subtitle: "AdminService 与界面尚未实现；状态写入和业务日志的同连接能力已有实现。",
    participants: [p("S", "AdminService（待实现）", true), p("D", "DBUtil / Connection"), p("U", "UserDao"),
      p("I", "ItemDao"), p("L", "OperationLogDao"), p("A", "AppLogger")],
    events: [m("S", "D", "getConnection(); setAutoCommit(false)"), m("S", "U", "findById(conn, operatorId)"),
      m("S", "I", "findById(conn, itemId)"), note("重新校验账号可用/管理员角色、物品状态与下架原因；理由先做必要内容处理，不记录凭据或完整表单。"),
      alt("校验和所有写入成功"), m("S", "I", "updateStatus(conn, itemId, OFF_SHELF)；检查 1 行"),
      m("S", "L", "insert(conn, 管理员动作、物品编号及安全原因摘要)"), m("S", "D", "commit()"),
      otherwise("权限/状态拒绝、0 行或 RuntimeException / SQLException"), m("S", "D", "rollback()；保留回滚失败为 suppressed"),
      end(), m("S", "D", "close()"), m("S", "A", "按已确定结果记录运行日志（可选接入）"),
      note("运行日志 IOException 独立处理，不将已提交业务误报成回滚；已有业务异常不能被日志错误覆盖。"),
      note("FR-19 所有者查看下架原因仍缺少专门授权查询/对象关联设计，不能以管理员能看 operation_log 替代。")]
  }
];
