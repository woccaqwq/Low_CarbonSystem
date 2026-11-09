-- =============================================================================
--  低碳校园资源循环流转桌面系统 —— SQLite 数据库建表脚本
--  文件：schema.sql        版本：v1.0        迭代：迭代 1（第 1–2 周）
--  说明：
--    1. 本脚本按“先删后建”方式组织，可重复执行；生产部署时请勿在有数据的库上执行。
--    2. 所有时间字段统一用 TEXT 存 ISO 格式字符串（yyyy-MM-dd HH:mm:ss，本地时间）。
--    3. 所有布尔语义字段用 INTEGER 存 0/1（SQLite 无原生 BOOLEAN）。
--    4. 外键约束必须由连接层执行 PRAGMA foreign_keys = ON 才会生效。
--    5. 事务边界由 Service 层控制，本脚本仅负责结构定义。
-- =============================================================================

PRAGMA foreign_keys = OFF;
PRAGMA journal_mode = WAL;

-- -----------------------------------------------------------------------------
-- 1. sys_user  用户表（普通学生 / 管理员共用一张表，以 role 区分）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS flow_record;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS item;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS sys_config;
DROP TABLE IF EXISTS sys_user;
DROP VIEW  IF EXISTS v_item_list;

CREATE TABLE sys_user (
    user_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    username       TEXT    NOT NULL UNIQUE,                 -- 登录名，唯一
    password_hash  TEXT    NOT NULL,                         -- PBKDF2-HMAC-SHA256 结果，Base64
    salt           TEXT    NOT NULL,                         -- 16 字节随机盐，Base64
    nickname       TEXT    NOT NULL DEFAULT '',
    role           TEXT    NOT NULL DEFAULT 'STUDENT'
                   CHECK (role IN ('STUDENT', 'ADMIN')),     -- 角色
    credit_score   INTEGER NOT NULL DEFAULT 100
                   CHECK (credit_score BETWEEN 0 AND 150),   -- 信用分，初始 100
    status         INTEGER NOT NULL DEFAULT 0
                   CHECK (status IN (0, 1)),                 -- 0 正常 / 1 禁用
    created_at     TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    last_login_at  TEXT,                                     -- 首次登录前为 NULL
    CHECK (length(username) BETWEEN 3 AND 20),
    CHECK (length(password_hash) > 0)
);

-- -----------------------------------------------------------------------------
-- 2. category  物品分类表（支持二级分类，parent_id 为 NULL 表示一级分类）
-- -----------------------------------------------------------------------------
CREATE TABLE category (
    category_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    parent_id    INTEGER REFERENCES category (category_id) ON DELETE RESTRICT,
    sort_no      INTEGER NOT NULL DEFAULT 0,
    UNIQUE (parent_id, name),                                -- 同一父分类下名称不重复
    CHECK (parent_id IS NULL OR parent_id <> category_id)     -- 不允许自引用
);

-- -----------------------------------------------------------------------------
-- 3. item  闲置物品表
-- -----------------------------------------------------------------------------
CREATE TABLE item (
    item_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id     INTEGER NOT NULL REFERENCES sys_user (user_id) ON DELETE RESTRICT,
    category_id  INTEGER          REFERENCES category (category_id) ON DELETE SET NULL,
    title        TEXT    NOT NULL,
    description  TEXT    NOT NULL DEFAULT '',
    price        REAL    NOT NULL DEFAULT 0 CHECK (price >= 0),   -- 交换类物品可为 0
    trade_type   TEXT    NOT NULL DEFAULT 'EXCHANGE'
                 CHECK (trade_type IN ('EXCHANGE', 'SALE')),      -- 交换 / 售卖
    status       TEXT    NOT NULL DEFAULT 'ON_SALE'
                 CHECK (status IN ('ON_SALE', 'OFF_SHELF', 'FLOWED')), -- 在架/下架/已流转
    tags         TEXT    NOT NULL DEFAULT '',                -- 逗号分隔，便于 LIKE 检索
    view_count   INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CHECK (length(title) BETWEEN 1 AND 60)
);

-- -----------------------------------------------------------------------------
-- 4. favorite  收藏表（用户 × 物品 唯一）
-- -----------------------------------------------------------------------------
CREATE TABLE favorite (
    favorite_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL REFERENCES sys_user (user_id) ON DELETE CASCADE,
    item_id      INTEGER NOT NULL REFERENCES item (item_id) ON DELETE CASCADE,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    UNIQUE (user_id, item_id)
);

-- -----------------------------------------------------------------------------
-- 5. flow_record  流转记录表（一次流转对应一条记录，贯穿“意向→成交/取消”）
-- -----------------------------------------------------------------------------
CREATE TABLE flow_record (
    flow_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    item_id      INTEGER NOT NULL REFERENCES item (item_id) ON DELETE RESTRICT,
    seller_id    INTEGER NOT NULL REFERENCES sys_user (user_id) ON DELETE RESTRICT,
    buyer_id     INTEGER NOT NULL REFERENCES sys_user (user_id) ON DELETE RESTRICT,
    flow_type    TEXT    NOT NULL DEFAULT 'EXCHANGE'
                 CHECK (flow_type IN ('EXCHANGE', 'SALE')),
    status       TEXT    NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'DEAL', 'CANCELLED')), -- 待确认/已成交/已取消
    remark       TEXT    NOT NULL DEFAULT '',
    created_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    finished_at  TEXT,                                       -- 成交或取消时写入
    CHECK (buyer_id <> seller_id)                            -- 不允许自己和自己流转
);

-- -----------------------------------------------------------------------------
-- 6. review  评价表（一条流转记录下，每个用户只能评价一次）
-- -----------------------------------------------------------------------------
CREATE TABLE review (
    review_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    flow_id       INTEGER NOT NULL REFERENCES flow_record (flow_id) ON DELETE CASCADE,
    from_user_id  INTEGER NOT NULL REFERENCES sys_user (user_id) ON DELETE CASCADE,
    to_user_id    INTEGER NOT NULL REFERENCES sys_user (user_id) ON DELETE CASCADE,
    score         INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
    content       TEXT    NOT NULL DEFAULT '',
    created_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    UNIQUE (flow_id, from_user_id),                          -- 防重复评价
    CHECK (from_user_id <> to_user_id)
);

-- -----------------------------------------------------------------------------
-- 7. operation_log  关键操作日志（加分项：日志记录）
-- -----------------------------------------------------------------------------
CREATE TABLE operation_log (
    log_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER          REFERENCES sys_user (user_id) ON DELETE SET NULL,
    action      TEXT    NOT NULL,                            -- 如 ITEM_PUBLISH / FLOW_CONFIRM
    detail      TEXT    NOT NULL DEFAULT '',
    created_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- -----------------------------------------------------------------------------
-- 8. sys_config  系统配置（加分项：备份策略等）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_config (
    config_key    TEXT PRIMARY KEY,
    config_value  TEXT NOT NULL,
    updated_at    TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- -----------------------------------------------------------------------------
-- 索引：按实际查询路径建立，避免全表扫描
-- -----------------------------------------------------------------------------
CREATE INDEX idx_item_owner      ON item (owner_id);
CREATE INDEX idx_item_category   ON item (category_id);
CREATE INDEX idx_item_status     ON item (status, created_at DESC);   -- 在架列表按时间倒序
CREATE INDEX idx_item_price      ON item (price);                     -- 价格区间筛选
CREATE INDEX idx_fav_user        ON favorite (user_id);
CREATE INDEX idx_flow_item       ON flow_record (item_id);
CREATE INDEX idx_flow_buyer      ON flow_record (buyer_id, status);
CREATE INDEX idx_flow_seller     ON flow_record (seller_id, status);
CREATE INDEX idx_review_flow     ON review (flow_id);
CREATE INDEX idx_review_to       ON review (to_user_id);
CREATE INDEX idx_log_user_time   ON operation_log (user_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 触发器 1：更新物品时自动维护 updated_at
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_item_updated_at
AFTER UPDATE OF title, description, price, trade_type, status, category_id, tags ON item
FOR EACH ROW
WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE item
       SET updated_at = datetime('now', 'localtime')
     WHERE item_id = NEW.item_id;
END;

-- -----------------------------------------------------------------------------
-- 触发器 2：成交后写入 finished_at（防止 Service 层漏写时间戳）
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_flow_finished_at
AFTER UPDATE OF status ON flow_record
FOR EACH ROW
WHEN NEW.status <> 'PENDING' AND NEW.finished_at IS NULL
BEGIN
    UPDATE flow_record
       SET finished_at = datetime('now', 'localtime')
     WHERE flow_id = NEW.flow_id;
END;

-- -----------------------------------------------------------------------------
-- 视图：物品列表（联表查询，供 DAO 直接使用，避免各处重复写 JOIN）
-- -----------------------------------------------------------------------------
CREATE VIEW v_item_list AS
SELECT i.item_id,
       i.title,
       i.price,
       i.trade_type,
       i.status,
       i.tags,
       i.view_count,
       i.created_at,
       i.owner_id,
       u.nickname   AS owner_nickname,
       u.credit_score AS owner_credit,
       i.category_id,
       c.name       AS category_name
  FROM item i
  LEFT JOIN sys_user u ON u.user_id = i.owner_id
  LEFT JOIN category c ON c.category_id = i.category_id;

PRAGMA foreign_keys = ON;
