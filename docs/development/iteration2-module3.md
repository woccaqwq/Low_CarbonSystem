# 迭代二模块三 分类与物品数据层

本模块在用户数据层基础上实现分类、物品实体与 JDBC DAO，提供物品发布存取、内容编辑、状态更新、浏览量和分页筛选能力。
成员 2 可通过工厂接口接入 ItemService；GUI、发布人权限、成交编排和管理员权限由相应成员实现。

## 分类数据层

`Category` 对应 category 表：`categoryId`、`name`、`parentId`、`sortNo`。
`parentId == null` 表示根分类，使用 `isRoot()` 判断。

```java
CategoryDao categories = DaoFactory.getCategoryDao();
List<Category> all = categories.findAll();
int newId = categories.insert(new Category("闲置文具", null, 5));
Category saved = categories.findById(newId).orElseThrow();
saved.setName("文具用品");
categories.update(saved);
categories.deleteById(newId);
```

`findAll()` 返回平面列表，按 `sort_no`、`category_id` 升序。界面可根据 parentId 组织层级。
`insert` 返回生成主键且不修改输入对象；更新/删除返回受影响行数，编号不存在时返回 0。
五个普通方法都提供 Connection 首参数重载。

同级名称不重复，根分类也不例外。SQLite 的 `UNIQUE(parent_id,name)` 无法阻止空父级的根分类重名，
实现使用单条带条件的写入 SQL 处理这一情况，不修改原表结构。
父分类存在子分类时删除受外键 RESTRICT 保护；删除被物品引用的叶分类，物品 category_id 自动变为 null，物品仍保留。
分类实体防止直接引用自己；二级层级限制、祖先循环检查及分类管理权限由 CategoryService/AdminService 校验。

## 物品实体与接口

`Item` 包含 item 表全部字段，另带 `ownerNickname`、`ownerCredit`、`categoryName` 三个只读查询投影用途的字段。
这些投影字段即便在内存中被设置，也不会写回 item 表或修改用户/分类。
浏览量使用 long，价格沿用设计的 double/SQLite REAL；标题限制为 1–60 个 Unicode 码点。

`ItemStatus`：ON_SALE、OFF_SHELF、FLOWED；`TradeType`：EXCHANGE、SALE。
新 Item 默认 ON_SALE。实体 `offline()` 允许在架到下架，`publish()` 负责重新上架，`markFlowed()` 允许在架到已流转。
FLOWED 为终态，非法实体状态转移抛 IllegalStateException。这些方法只修改内存，不自动保存。

ItemDao 保留设计说明书中的七个普通方法：

| 方法 | 行为 |
| --- | --- |
| `findById(int)` | 完整详情及展示投影，查不到返回 Optional.empty |
| `insert(Item)` | 返回生成主键；不修改输入对象，未设置日期时使用数据库默认时间 |
| `update(Item)` | 仅更新分类、标题、描述、价格、交易方式和标签 |
| `updateStatus(int,ItemStatus)` | 单独修改状态，权限与业务前置状态由 Service 检查 |
| `increaseViewCount(int)` | 原子增加浏览量 |
| `existsOnSale(int)` | 判断物品是否存在且在架 |
| `search(ItemQuery)` | 按组合条件查询并分页 |

上述方法均提供 Connection 首参数重载。普通更新不会修改发布人、成交状态、浏览量或创建时间，避免内容编辑覆盖其他操作。
更新时间由原有数据库触发器维护。时间读取与保存沿用 `yyyy-MM-dd HH:mm:ss`，精度为秒。
查询使用完整的物品、用户、分类 JOIN，因为原 v_item_list 视图不包含描述和更新时间；原视图与 SQL 脚本保持原样。

## 筛选与分页示例

```java
ItemDao items = DaoFactory.getItemDao();
Item item = new Item();
item.setOwnerId(2);
item.setCategoryId(11);
item.setTitle("软件工程教材");
item.setDescription("内页完整");
item.setPrice(25.0);
item.setTradeType(TradeType.SALE);
int itemId = items.insert(item);

ItemQuery query = ItemQuery.builder()
        .keyword("教材")
        .categoryId(1)
        .minPrice(10.0)
        .maxPrice(30.0)
        .tradeType(TradeType.SALE)
        .sort(new PriceAscStrategy())
        .pageNo(1)
        .pageSize(20)
        .build();
PageResult<Item> page = items.search(query);
```

默认：在架、按时间倒序、第 1 页、每页 20 条。
使用 `.status(null)` 查询全部状态，`.ownerId(userId)` 查询“我的物品”；其他可选条件传 null 表示不限。
分类筛选包括该分类及后代分类，选择一级分类可以查出其二级分类的物品。
描述、标题、标签均参与关键词匹配；关键词两端空白会去除，百分号、下划线和反斜杠按字面量搜索。

页大小须在 10–100，页码从 1 开始。超出总页数时保留总数与请求页码，返回空列表。
offset 使用 long；相同排序值按 item_id 倒序补充排序，确保翻页稳定。
负价格、NaN、Infinity、倒置区间、非正筛选编号和非法分页参数在构建 ItemQuery 时被拒绝。

## 排序策略与安全

内置 `TimeDescStrategy`、`PriceAscStrategy`、`CreditDescStrategy`，分别按发布时间、价格和发布人信用排序。
沿用 `SortStrategy.toOrderBy()` 接口，但 ItemQuery 仅允许 `i.price`、`i.created_at`、`i.item_id`、`u.credit_score` 的 ASC/DESC 组合。
构建时读取、验证并冻结排序文本，不会在执行查询时再次调用外部策略，防止可变策略绕过验证。
其他筛选值全部绑定到 PreparedStatement；SQL 条件构造只组合固定片段。

## 事务与异常

分类、物品、用户 DAO 可共用同一个来自 DBUtil 的连接。
事务中的全部调用应使用 Connection 重载；由 Service 开启、提交或回滚，DAO 只关闭自己的 Statement/ResultSet。
外键、同级分类重名和 SQL 故障包装为 DataAccessException；非法实体与查询输入抛 IllegalArgumentException。
日志与界面提示由调用边界统一处理。

## 本轮接口补充

- 新增 CategoryDao 的五个方法及连接重载；迭代一只列出了接口和方法数量，未给出详细签名。
- ItemDao 七个普通方法保持原签名，补充同连接重载。
- ItemQuery 增加可选 ownerId，支持既有“我的物品”界面；字段、分页与排序通过不可变 Builder 设置。
- 工厂新增 `DaoFactory.getCategoryDao()` 与 `DaoFactory.getItemDao()`。
- 未新增数据表或修改初始化脚本；交接时将以上补充同步给成员 2、3。

## 验证记录

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

新增实现前先运行相应测试，确认缺失实现导致失败，再实现并执行全套。
分类 DAO 8 项、物品实体 3 项、检索条件 4 项、物品 DAO 20 项，加上既有 49 项，共 84 项。
覆盖分类重名、外键行为、状态机、字段保护、组合检索、排序安全、分页边界、参数化查询与跨 DAO 回滚。
独立只读代码审查未发现阻塞问题；补充了超出 32 位范围的浏览量、约束失败后的外部事务控制及损坏分类环的检索测试。

后续建议开发收藏与流转数据层；当前不包含物品图片存储等选做功能。
