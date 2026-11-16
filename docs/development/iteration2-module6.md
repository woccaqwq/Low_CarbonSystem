# 迭代二模块六 评价数据层

本模块实现评价实体、存取、重复检查、收到的评价列表与平均分，为成员 2 的 ReviewService 提供数据接口。
评价与信用分可以通过同一连接原子提交，DAO 本身不会自动加减信用分。

## 实体与接口

`Review` 对应 review_id、flow_id、from_user_id、to_user_id、score、content、created_at。
`fromNickname`、`toNickname`、`itemTitle` 是每次查询得到的当前展示投影，不写入原评价记录。
评分须为 1–5，内容非 null、最多 200 个 Unicode 码点，可为空串；不能评价自己。
无参对象的默认评分为 0，须填入有效评分后才能保存；`validateScore()` 可显式校验。

保留四个冻结方法：

| 方法 | 语义 |
| --- | --- |
| `insert(Review)` | 返回新主键，不修改输入对象；重复评价抛 DataAccessException |
| `existsByFlowAndFrom(int flowId,int fromUserId)` | 检查该评价人是否已评价该次流转 |
| `findByUser(int userId)` | 查询该用户收到的评价，即 to_user_id 匹配 |
| `avgScoreOf(int userId)` | 该用户收到评价的平均星级，保留小数，无评价时为 0.0 |

新增 `findById(int)` 返回 Optional，以及 `findByUser(int,int pageNo,int pageSize)` 分页重载。
六个方法都有 Connection 首参数重载，不提交、回滚或关闭调用方连接。
工厂入口为 `DaoFactory.getReviewDao()`。

## 调用示例

```java
ReviewDao reviews = DaoFactory.getReviewDao();
Review review = new Review(flowId, fromUserId, toUserId, 5, "物品与描述一致");
int reviewId = reviews.insert(review);
List<Review> received = reviews.findByUser(toUserId);
PageResult<Review> page = reviews.findByUser(toUserId, 1, 20);
double average = reviews.avgScoreOf(toUserId);
```

实际提交入口应调用 ReviewService，由业务层先核对成交状态、身份和时间窗口，再存储评价。
DAO 的普通 insert 不代表用户已获评价资格；GUI 不应直接调用 DAO。

评价列表按 created_at DESC、review_id DESC，页码从 1 开始，每页 10–100，offset 使用 long。
用户不存在或没有收到评价时返回空列表、总数 0、平均分 0.0。
平均分不是信用分；用户信用分仍存储于 sys_user.credit_score。
超过 7 天的旧评价继续展示，7 天仅限制新评价的提交资格。

## 重复与数据保护

原 UNIQUE(flow_id,from_user_id) 保证同一流转同一评价人最多一次。
双方互评允许保存两条记录；重复提交不覆盖旧内容，也不作为成功重试处理。
创建时间可指定，保存到秒；null 使用数据库当前时间。
评价内容中的引号、中文等作为参数绑定，不参与 SQL 拼接。
外键及重复错误包装为 DataAccessException，原 SQLException 保留在 cause。

本模块不提供修改或删除评价的业务接口。
删除流转记录时，原有外键会级联删除评价；是否允许删除流转记录应由业务层决定。
展示投影随昵称/物品标题变化重新查询，但评价分数、内容与关联编号保持原记录。

## 信用规则与业务层责任

按照需求 FR-14，收到 4–5 星评价的用户信用 +2，收到 1–2 星 -3，3 星不变。
评价人不会仅因“发送评价”额外获得信用。每次成交双方 +1 是另一条规则，由 FlowService 处理。
UserDao 已提供原子信用更新，限制结果在 0–150。

ReviewService 应校验：已成交、评价人是交易双方之一、被评价人是另一方、成交后 7 天内、未重复评价。
接收人应从流转记录推导，不能直接信任界面传入的 toUserId。
上述业务判断与窗口政策不放进通用 ReviewDao；原始 DAO 仅负责数据存取、自身字段约束和数据库异常。

## 同连接事务示例

下面示例帮助成员 2 接入，使用通用异常作占位；正式 Service 应替换为约定的业务错误码。
`fromUserId` 应来自已验证的登录身份。

```java
try (Connection connection = DBUtil.getInstance().getConnection()) {
    connection.setAutoCommit(false);
    try {
        FlowRecord flow = flows.findById(connection, flowId)
                .orElseThrow(() -> new IllegalStateException("流转不存在"));
        if (flow.getStatus() != FlowStatus.DEAL || flow.getFinishedAt() == null
                || LocalDateTime.now().isAfter(flow.getFinishedAt().plusDays(7))) {
            throw new IllegalStateException("当前不允许评价");
        }
        int toUserId;
        if (fromUserId == flow.getSellerId()) {
            toUserId = flow.getBuyerId();
        } else if (fromUserId == flow.getBuyerId()) {
            toUserId = flow.getSellerId();
        } else {
            throw new IllegalStateException("不是交易双方");
        }
        Review review = new Review(flowId, fromUserId, toUserId, score, content);
        reviews.insert(connection, review);
        int delta = score >= 4 ? 2 : score <= 2 ? -3 : 0;
        if (delta != 0 && users.updateCreditScore(connection, toUserId, delta) != 1) {
            throw new IllegalStateException("信用更新失败");
        }
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

不能先调用普通 insert 再使用另一连接修改信用，否则不能共同回滚。
重复评价触发数据库唯一约束后应回滚整个事务，不能继续执行加分。
数据库异常为非受检 DataAccessException，必须包含在回滚捕获范围内。

## 接口补充与验证

- 四个普通方法保持冻结签名，新增编号查询、收到评价的分页与连接重载。
- 新增 `DaoFactory.getReviewDao()` 和三个展示投影；未修改原表与初始化 SQL。
- 本模块未实现 ReviewService 或 GUI；统计与操作日志数据层尚待开发。

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

新增实体测试 4 项、DAO 测试 16 项，加上既有 122 项，共 142 项。
覆盖评分与字数边界、初始数据、参数化内容、互评和重复、收评过滤与小数平均、稳定分页、外键及级联。
事务测试验证评价与信用共同提交，以及信用写入失败或后续重复评价失败后的整体回滚。
级联删除测试核对实际数据结果，不把驱动可能包含级联子行的 Statement 返回行数当作业务结果。
独立只读审查无阻塞问题；按建议补充了 200 个非 BMP 字符通过 JDBC 保存后完整读回的测试。
