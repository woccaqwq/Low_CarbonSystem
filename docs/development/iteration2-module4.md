# 迭代二模块四 收藏数据层

本模块提供添加收藏、取消收藏、判断是否收藏及分页查询能力，供成员 2 的 ItemService 接入。
复用既有 SQLite 表结构、连接工厂、公共 DAO 和分页结果，不修改初始化脚本。

## 实体与接口

`Favorite` 对应 favorite 表：favoriteId、userId、itemId、createdAt。
查询结果额外包含 Item 投影，用于显示物品当前标题、描述、状态、分类、发布人昵称和信用分。
投影只是本次查询的快照，刷新列表需重新查询；修改投影不会自动修改数据库物品。

| DAO 方法 | 返回值与语义 |
| --- | --- |
| `insert(Favorite)` | 返回新主键；同用户同物品重复添加返回原主键，保留原收藏时间 |
| `deleteByUserAndItem(int userId,int itemId)` | 只删除指定用户与物品组合，第一次取消返回 1，再取消返回 0 |
| `exists(int userId,int itemId)` | 返回是否已收藏，不存在的正整数用户或物品返回 false |
| `findByUser(int userId,int pageNo,int pageSize)` | 返回 PageResult<Favorite>，每条记录包含当前 Item 投影 |

四个方法均有 Connection 首参数重载。编号须为正整数；页码从 1 开始，每页 10–100。
没有收藏或用户不存在时，分页查询返回空列表和总数 0。
列表按收藏时间倒序、收藏编号倒序排列；offset 使用 long，超出总页数返回空列表但保留总数。

## 调用示例

```java
import com.campus.recycle.dao.FavoriteDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.Favorite;
import com.campus.recycle.entity.PageResult;

FavoriteDao favorites = DaoFactory.getFavoriteDao();
int userId = 3;
int itemId = 1;
int favoriteId = favorites.insert(new Favorite(userId, itemId));
boolean collected = favorites.exists(userId, itemId);
PageResult<Favorite> page = favorites.findByUser(userId, 1, 20);
int deleted = favorites.deleteByUserAndItem(userId, itemId);
```

DAO 参数顺序为 **userId、itemId**；设计中 Service 的 `favorite(itemId,userId)` 顺序不同，接入时请明确传参。
插入不修改传入 Favorite 的 favoriteId 或 createdAt；需要完整记录时重新查询。
createdAt 为 null 时首次保存使用数据库默认时间，显式时间保存到秒。

## 幂等添加与错误处理

实现使用一条定向 UPSERT：仅对 UNIQUE(user_id,item_id) 冲突复用原记录，通过 RETURNING 返回该记录主键。
重复添加不会覆盖原收藏时间、重新排序或生成第二条收藏记录。
用户/物品不存在等外键错误仍包装为 DataAccessException，不使用会忽略其他约束错误的宽泛插入方式。
非法实体、非正编号和非法分页参数抛 IllegalArgumentException。

物品下架或已流转后收藏仍保留，列表不筛除非在架物品。
删除分类使物品分类变为 null 时，收藏继续可见；删除物品或用户时由既有 ON DELETE CASCADE 清理对应收藏。
用户删除是否因其他业务引用被禁止，仍由原有外键及业务规则决定。

## 事务与映射复用

普通方法管理独立连接；事务内传入同一连接：

```java
try (Connection connection = DBUtil.getInstance().getConnection()) {
    connection.setAutoCommit(false);
    try {
        favorites.insert(connection, new Favorite(userId, itemId));
        // 可继续调用其他 DAO 的 Connection 重载。
        connection.commit();
    } catch (RuntimeException | SQLException failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        throw failure;
    }
}
```

DAO 关闭自身的 PreparedStatement/ResultSet，不提交、回滚或关闭外部连接。
失败后已有未提交修改仍由调用方决定回滚。
收藏时间使用独立别名 `favorite_created_at`，避免与物品的 created_at 混淆。
物品与收藏 DAO 共用包内 `ItemRowMapper`，该映射器不执行 SQL，也不形成 DAO 间调用。

## 接口补充与交接

- 迭代一未给出 FavoriteDao 的详细签名，本模块补齐四个普通方法及连接重载。
- 新增 `DaoFactory.getFavoriteDao()`。
- 新增 Favorite.item 查询投影，不新增数据库字段。
- ItemService 应使用登录用户身份，负责收藏资格与用户提示；DAO 不判断登录状态或界面行为。
- 本模块尚未实现 FlowRecordDao；下一个模块建议开发流转记录数据层。

## 验证

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

收藏新增 15 项真实 SQLite JUnit 测试，加上既有 84 项，共 99 项。
测试覆盖初始数据、持久化、幂等添加、用户隔离、物品状态变化、空分类、时间精度与稳定分页、
外键错误、外部事务提交/回滚、用户与物品级联删除、非法参数和已关闭连接。
独立只读审查未发现阻塞问题；已加强时间映射断言，分别验证收藏时间与物品创建/更新时间不串值。
