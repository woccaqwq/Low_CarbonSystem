-- =============================================================================
--  低碳校园资源循环流转桌面系统 —— 初始化数据脚本
--  文件：init_data.sql      版本：v1.0      依赖：schema.sql 已执行
--
--  测试账号（首次登录后建议修改密码）：
--      管理员   admin    / admin123
--      学生甲   student1 / 123456     （物品发布者）
--      学生乙   student2 / 123456     （意向发起者）
--
--  口令算法：PBKDF2WithHmacSHA256，迭代 100000 次，输出 256 位，
--            salt 与 password_hash 均为 Base64 文本。
--            Java 侧对应：
--              PBEKeySpec(pwd.toCharArray(), Base64.getDecoder().decode(salt), 100000, 256)
--              SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
--            本文件中已写入按上述算法计算好的真实哈希值，可直接用上述账号登录。
-- =============================================================================

PRAGMA foreign_keys = ON;

BEGIN TRANSACTION;

-- ---------------------------------------------------------------- 用户
INSERT INTO sys_user (username, password_hash, salt, nickname, role, credit_score, status) VALUES
('admin',    '2lFmtZvCAifuCQjRuFzFh5KojasY/zSk+67mIhff61Q=', 'ytSKAF0hG0IsFXo4ed3mNg==', '系统管理员', 'ADMIN',   100, 0),
('student1', 'VRinVv4gsgJNrsvDJxZJqBWuzVDPKg9qe/ZXFsawLY8=', 'uuGegOWO6poks79ph4t1LA==', '徐同学',     'STUDENT', 112, 0),
('student2', 'y5Ji0dVopjkzyB0OGVMgS9i07brSXPZR930ht3kveBw=', 'sSrNWgAcISm+U2e5Lp5TXA==', '李同学',     'STUDENT', 104, 0);

-- ---------------------------------------------------------------- 分类字典
-- 一级分类
INSERT INTO category (category_id, name, parent_id, sort_no) VALUES
(1, '教材书籍', NULL, 1),
(2, '数码配件', NULL, 2),
(3, '文体用品', NULL, 3),
(4, '生活物资', NULL, 4);

-- 二级分类
INSERT INTO category (category_id, name, parent_id, sort_no) VALUES
(11, '专业课教材', 1, 1),
(12, '公共课教材', 1, 2),
(13, '考研资料',   1, 3),
(21, '数据线/充电器', 2, 1),
(22, '耳机/音响',     2, 2),
(23, '键盘/鼠标',     2, 3),
(31, '球类器材',   3, 1),
(32, '健身器材',   3, 2),
(41, '小家电',     4, 1),
(42, '日用品',     4, 2);

-- ---------------------------------------------------------------- 测试物品
INSERT INTO item (item_id, owner_id, category_id, title, description, price, trade_type, status, tags, view_count, created_at, updated_at) VALUES
(1, 2, 11, '《软件构造》课程教材（第 2 版）', '只用了一学期，内页干净，无笔记划线，可交换等价教材。', 25.0, 'SALE',     'ON_SALE',  '教材,软件工程,九成新', 46, datetime('now', '-6 days', 'localtime'), datetime('now', '-6 days', 'localtime')),
(2, 2, 21, 'Type-C 65W 快充充电器（含线）',    '原装正品，换了新手机后闲置，功能一切正常。',       45.0, 'SALE',     'ON_SALE',  '数码,快充,充电器',   31, datetime('now', '-5 days', 'localtime'), datetime('now', '-5 days', 'localtime')),
(3, 3, 31, '羽毛球拍一副（含球筒）',           '碳素材质，手柄稍旧，适合入门练习，愿交换篮球。',     0.0, 'EXCHANGE', 'ON_SALE',  '运动,羽毛球,二手',   18, datetime('now', '-3 days', 'localtime'), datetime('now', '-3 days', 'localtime')),
(4, 3, 41, '小型电热水壶 0.8L',                '宿舍可用功率，已清洁消毒，换风扇或直接购买。',       30.0, 'EXCHANGE', 'FLOWED',   '生活,小家电,宿舍',   57, datetime('now', '-9 days', 'localtime'), datetime('now', '-2 days', 'localtime')),
(5, 2, 13, '考研数学复习全书（2025 版）',      '配套习题册齐全，仅做过前两章。',                     35.0, 'SALE',     'OFF_SHELF', '考研,数学,资料',      9, datetime('now', '-11 days', 'localtime'), datetime('now', '-4 days', 'localtime'));

-- ---------------------------------------------------------------- 收藏
INSERT INTO favorite (user_id, item_id, created_at) VALUES
(3, 1, datetime('now', '-4 days', 'localtime')),
(3, 2, datetime('now', '-2 days', 'localtime')),
(2, 3, datetime('now', '-1 days', 'localtime'));

-- ---------------------------------------------------------------- 流转记录
-- 一条已成交（物品 4），两条待确认
INSERT INTO flow_record (flow_id, item_id, seller_id, buyer_id, flow_type, status, remark, created_at, finished_at) VALUES
(1, 4, 3, 2, 'EXCHANGE', 'DEAL',    '用水壶换同学的桌面风扇，已当面验货。', datetime('now', '-8 days', 'localtime'), datetime('now', '-2 days', 'localtime')),
(2, 1, 2, 3, 'SALE',     'PENDING', '想要这本教材，可以 20 元成交吗？',     datetime('now', '-2 days', 'localtime'), NULL),
(3, 3, 3, 2, 'EXCHANGE', 'PENDING', '我有篮球，想换羽毛球拍。',             datetime('now', '-1 days', 'localtime'), NULL);

-- ---------------------------------------------------------------- 评价（对应已成交的流转记录 1）
INSERT INTO review (flow_id, from_user_id, to_user_id, score, content, created_at) VALUES
(1, 2, 3, 5, '物品描述一致，交接很爽快，推荐。', datetime('now', '-2 days', 'localtime')),
(1, 3, 2, 4, '沟通顺利，按约定时间到场。',       datetime('now', '-2 days', 'localtime'));

-- ---------------------------------------------------------------- 操作日志
INSERT INTO operation_log (user_id, action, detail, created_at) VALUES
(2, 'ITEM_PUBLISH', '发布物品：Type-C 65W 快充充电器', datetime('now', '-5 days', 'localtime')),
(2, 'FLOW_REQUEST', '对物品 1 发起购买意向',           datetime('now', '-2 days', 'localtime')),
(3, 'FLOW_CONFIRM', '确认流转记录 1 成交',             datetime('now', '-2 days', 'localtime')),
(1, 'ADMIN_LOGIN',  '管理员登录后台',                  datetime('now', '-1 days', 'localtime'));

-- ---------------------------------------------------------------- 系统配置
INSERT INTO sys_config (config_key, config_value) VALUES
('db.backup.enabled', 'true'),
('db.backup.keep_days', '7'),
('stat.co2.per_item_kg', '2.4');

COMMIT;

-- 自增序列对齐（显式插入主键后必须同步 sqlite_sequence，否则后续插入会冲突）
UPDATE sqlite_sequence SET seq = 5 WHERE name = 'item';
UPDATE sqlite_sequence SET seq = 3 WHERE name = 'flow_record';
UPDATE sqlite_sequence SET seq = 4 WHERE name = 'operation_log';
