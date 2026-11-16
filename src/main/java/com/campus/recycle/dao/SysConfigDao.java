package com.campus.recycle.dao;

import com.campus.recycle.entity.SysConfig;
import com.campus.recycle.exception.DataAccessException;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** 只读系统配置，无缓存或隐式默认值；Connection 重载不管理调用方事务。 */
public interface SysConfigDao {
    /** @param key 非空白键，区分大小写与空格 @return 配置或空，空串值与不存在不同 */
    Optional<SysConfig> findByKey(String key) throws DataAccessException;
    /** @return 按 config_key 升序的不可修改列表，无数据时为空列表 */
    List<SysConfig> findAll() throws DataAccessException;
    /** @param connection 外部连接，不关闭、不提交、不回滚 @param key 非空白字面键 @return 配置或空 */
    Optional<SysConfig> findByKey(Connection connection, String key) throws DataAccessException;
    /** @param connection 外部连接 @return 按 config_key 升序的不可修改列表 */
    List<SysConfig> findAll(Connection connection) throws DataAccessException;
}
