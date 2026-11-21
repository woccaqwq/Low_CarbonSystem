# 迭代二 DAO 接口清单

由当前 Java 接口源自动生成，按类型与参数完整保留方法签名，不表示新增 Service 契约。
九个 DAO 共 56 个普通方法和 56 个 Connection 首参数重载，合计 112 个。

| 接口 | 实现类 | BaseDao 映射类型 | 普通 | Connection | 工厂方法 |
| --- | --- | --- | ---: | ---: | --- |
| CategoryDao | CategoryDaoImpl | Category | 5 | 5 | getCategoryDao() |
| FavoriteDao | FavoriteDaoImpl | Favorite | 4 | 4 | getFavoriteDao() |
| FlowRecordDao | FlowRecordDaoImpl | FlowRecord | 10 | 10 | getFlowRecordDao() |
| ItemDao | ItemDaoImpl | Item | 7 | 7 | getItemDao() |
| OperationLogDao | OperationLogDaoImpl | OperationLog | 4 | 4 | getOperationLogDao() |
| ReviewDao | ReviewDaoImpl | Review | 6 | 6 | getReviewDao() |
| StatDao | StatDaoImpl | StatCounts | 10 | 10 | getStatDao() |
| SysConfigDao | SysConfigDaoImpl | SysConfig | 2 | 2 | getSysConfigDao() |
| UserDao | UserDaoImpl | User | 8 | 8 | getUserDao() |

## CategoryDao

[接口源](../../../src/main/java/com/campus/recycle/dao/CategoryDao.java)

```java
Optional<Category> findById(int categoryId) throws DataAccessException;
List<Category> findAll() throws DataAccessException;
int insert(Category category) throws DataAccessException;
int update(Category category) throws DataAccessException;
int deleteById(int categoryId) throws DataAccessException;
Optional<Category> findById(Connection connection, int categoryId) throws DataAccessException;
List<Category> findAll(Connection connection) throws DataAccessException;
int insert(Connection connection, Category category) throws DataAccessException;
int update(Connection connection, Category category) throws DataAccessException;
int deleteById(Connection connection, int categoryId) throws DataAccessException;
```

## FavoriteDao

[接口源](../../../src/main/java/com/campus/recycle/dao/FavoriteDao.java)

```java
int insert(Favorite favorite) throws DataAccessException;
int deleteByUserAndItem(int userId, int itemId) throws DataAccessException;
boolean exists(int userId, int itemId) throws DataAccessException;
PageResult<Favorite> findByUser(int userId, int pageNo, int pageSize) throws DataAccessException;
int insert(Connection connection, Favorite favorite) throws DataAccessException;
int deleteByUserAndItem(Connection connection, int userId, int itemId) throws DataAccessException;
boolean exists(Connection connection, int userId, int itemId) throws DataAccessException;
PageResult<Favorite> findByUser(Connection connection, int userId, int pageNo, int pageSize) throws DataAccessException;
```

## FlowRecordDao

[接口源](../../../src/main/java/com/campus/recycle/dao/FlowRecordDao.java)

```java
int insert(FlowRecord record) throws DataAccessException;
Optional<FlowRecord> findById(int flowId) throws DataAccessException;
int updateStatus(int flowId, FlowStatus status) throws DataAccessException;
boolean existsPending(int itemId, int buyerId) throws DataAccessException;
List<FlowRecord> findByUser(int userId, FlowStatus status) throws DataAccessException;
int cancelOtherPending(int itemId, int exceptFlowId) throws DataAccessException;
int cancel(int flowId, String reason) throws DataAccessException;
PageResult<FlowRecord> findByUser(int userId, FlowStatus status, int pageNo, int pageSize) throws DataAccessException;
PageResult<FlowRecord> findByBuyer(int buyerId, FlowStatus status, int pageNo, int pageSize) throws DataAccessException;
PageResult<FlowRecord> findBySeller(int sellerId, FlowStatus status, int pageNo, int pageSize) throws DataAccessException;
int insert(Connection connection, FlowRecord record) throws DataAccessException;
Optional<FlowRecord> findById(Connection connection, int flowId) throws DataAccessException;
int updateStatus(Connection connection, int flowId, FlowStatus status) throws DataAccessException;
boolean existsPending(Connection connection, int itemId, int buyerId) throws DataAccessException;
List<FlowRecord> findByUser(Connection connection, int userId, FlowStatus status) throws DataAccessException;
int cancelOtherPending(Connection connection, int itemId, int exceptFlowId) throws DataAccessException;
int cancel(Connection connection, int flowId, String reason) throws DataAccessException;
PageResult<FlowRecord> findByUser(Connection connection, int userId, FlowStatus status, int pageNo, int pageSize) throws DataAccessException;
PageResult<FlowRecord> findByBuyer(Connection connection, int buyerId, FlowStatus status, int pageNo, int pageSize) throws DataAccessException;
PageResult<FlowRecord> findBySeller(Connection connection, int sellerId, FlowStatus status, int pageNo, int pageSize) throws DataAccessException;
```

## ItemDao

[接口源](../../../src/main/java/com/campus/recycle/dao/ItemDao.java)

```java
Optional<Item> findById(int itemId) throws DataAccessException;
int insert(Item item) throws DataAccessException;
int update(Item item) throws DataAccessException;
int updateStatus(int itemId, ItemStatus status) throws DataAccessException;
int increaseViewCount(int itemId) throws DataAccessException;
boolean existsOnSale(int itemId) throws DataAccessException;
PageResult<Item> search(ItemQuery query) throws DataAccessException;
Optional<Item> findById(Connection connection, int itemId) throws DataAccessException;
int insert(Connection connection, Item item) throws DataAccessException;
int update(Connection connection, Item item) throws DataAccessException;
int updateStatus(Connection connection, int itemId, ItemStatus status) throws DataAccessException;
int increaseViewCount(Connection connection, int itemId) throws DataAccessException;
boolean existsOnSale(Connection connection, int itemId) throws DataAccessException;
PageResult<Item> search(Connection connection, ItemQuery query) throws DataAccessException;
```

## OperationLogDao

[接口源](../../../src/main/java/com/campus/recycle/dao/OperationLogDao.java)

```java
int insert(OperationLog log) throws DataAccessException;
List<OperationLog> findRecent(int limit) throws DataAccessException;
Optional<OperationLog> findById(int logId) throws DataAccessException;
PageResult<OperationLog> findByPage(Integer userId, String action, int pageNo, int pageSize) throws DataAccessException;
int insert(Connection connection, OperationLog log) throws DataAccessException;
List<OperationLog> findRecent(Connection connection, int limit) throws DataAccessException;
Optional<OperationLog> findById(Connection connection, int logId) throws DataAccessException;
PageResult<OperationLog> findByPage(Connection connection, Integer userId, String action, int pageNo, int pageSize) throws DataAccessException;
```

## ReviewDao

[接口源](../../../src/main/java/com/campus/recycle/dao/ReviewDao.java)

```java
int insert(Review review) throws DataAccessException;
boolean existsByFlowAndFrom(int flowId, int fromUserId) throws DataAccessException;
List<Review> findByUser(int userId) throws DataAccessException;
double avgScoreOf(int userId) throws DataAccessException;
Optional<Review> findById(int reviewId) throws DataAccessException;
PageResult<Review> findByUser(int userId, int pageNo, int pageSize) throws DataAccessException;
int insert(Connection connection, Review review) throws DataAccessException;
boolean existsByFlowAndFrom(Connection connection, int flowId, int fromUserId) throws DataAccessException;
List<Review> findByUser(Connection connection, int userId) throws DataAccessException;
double avgScoreOf(Connection connection, int userId) throws DataAccessException;
Optional<Review> findById(Connection connection, int reviewId) throws DataAccessException;
PageResult<Review> findByUser(Connection connection, int userId, int pageNo, int pageSize) throws DataAccessException;
```

## StatDao

[接口源](../../../src/main/java/com/campus/recycle/dao/StatDao.java)

```java
List<CategoryStat> countByCategory();
List<MonthStat> countByMonth(String year);
int countItems(ItemStatus status);
int countFlows(FlowStatus status);
List<CategoryStat> countByCategory(StatQuery query);
List<MonthStat> countByMonth(String year, StatQuery query);
int countItems(ItemStatus status, StatQuery query);
int countFlows(FlowStatus status, StatQuery query);
StatOverview getOverview();
StatOverview getOverview(StatQuery query);
List<CategoryStat> countByCategory(Connection connection);
List<MonthStat> countByMonth(Connection connection, String year);
int countItems(Connection connection, ItemStatus status);
int countFlows(Connection connection, FlowStatus status);
List<CategoryStat> countByCategory(Connection connection, StatQuery query);
List<MonthStat> countByMonth(Connection connection, String year, StatQuery query);
int countItems(Connection connection, ItemStatus status, StatQuery query);
int countFlows(Connection connection, FlowStatus status, StatQuery query);
StatOverview getOverview(Connection connection);
StatOverview getOverview(Connection connection, StatQuery query);
```

## SysConfigDao

[接口源](../../../src/main/java/com/campus/recycle/dao/SysConfigDao.java)

```java
Optional<SysConfig> findByKey(String key) throws DataAccessException;
List<SysConfig> findAll() throws DataAccessException;
Optional<SysConfig> findByKey(Connection connection, String key) throws DataAccessException;
List<SysConfig> findAll(Connection connection) throws DataAccessException;
```

## UserDao

[接口源](../../../src/main/java/com/campus/recycle/dao/UserDao.java)

```java
Optional<User> findById(int userId) throws DataAccessException;
Optional<User> findByUsername(String username) throws DataAccessException;
int insert(User user) throws DataAccessException;
int updatePassword(int userId, String hash, String salt) throws DataAccessException;
int updateCreditScore(int userId, int delta) throws DataAccessException;
int updateStatus(int userId, int status) throws DataAccessException;
int updateLastLogin(int userId, LocalDateTime time) throws DataAccessException;
PageResult<User> findByPage(String keyword, int pageNo, int pageSize) throws DataAccessException;
Optional<User> findById(Connection connection, int userId) throws DataAccessException;
Optional<User> findByUsername(Connection connection, String username) throws DataAccessException;
int insert(Connection connection, User user) throws DataAccessException;
int updatePassword(Connection connection, int userId, String hash, String salt) throws DataAccessException;
int updateCreditScore(Connection connection, int userId, int delta) throws DataAccessException;
int updateStatus(Connection connection, int userId, int status) throws DataAccessException;
int updateLastLogin(Connection connection, int userId, LocalDateTime time) throws DataAccessException;
PageResult<User> findByPage(Connection connection, String keyword, int pageNo, int pageSize) throws DataAccessException;
```

StatDao 的 DataAccessException 为非受检异常，源签名未显式声明 throws，仍可能抛出。其他签名按源文件原样保留。
