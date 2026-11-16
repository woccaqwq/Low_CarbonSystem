BEGIN TRANSACTION;
INSERT INTO sys_user (username, password_hash, salt) VALUES ('rollback_user', 'hash', 'salt');
INSERT INTO item (owner_id, title) VALUES (99999, 'invalid owner');
COMMIT;
