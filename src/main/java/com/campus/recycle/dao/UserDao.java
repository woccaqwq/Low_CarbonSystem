package com.campus.recycle.dao;

import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.User;
import com.campus.recycle.exception.DataAccessException;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用户持久化契约。普通方法管理独立连接；Connection 重载参与调用方事务。
 * 所有数据访问故障均抛 DataAccessException，不包含业务权限或登录规则。
 */
public interface UserDao {
    /** @param userId 用户编号 @return 用户或 Optional.empty() */
    Optional<User> findById(int userId) throws DataAccessException;
    /** @param username 登录名 @return 用户或 Optional.empty() */
    Optional<User> findByUsername(String username) throws DataAccessException;
    /** @param user 待保存数据 @return 新主键，不修改传入对象 */
    int insert(User user) throws DataAccessException;
    /** @param userId 用户编号 @param hash 新哈希 @param salt 新盐 @return 受影响行数 */
    int updatePassword(int userId, String hash, String salt) throws DataAccessException;
    /** @param userId 用户编号 @param delta 增量，结果限制在 0–150 @return 受影响行数 */
    int updateCreditScore(int userId, int delta) throws DataAccessException;
    /** @param userId 用户编号 @param status 0 正常，1 禁用 @return 受影响行数 */
    int updateStatus(int userId, int status) throws DataAccessException;
    /** @param userId 用户编号 @param time 非空登录时间，保存到秒 @return 受影响行数 */
    int updateLastLogin(int userId, LocalDateTime time) throws DataAccessException;
    /**
     * 用户名和昵称的字面量包含搜索，按 user_id 升序。
     * @param keyword null 或空白表示全部
     * @param pageNo 从 1 开始
     * @param pageSize 10–100
     * @return 分页结果，页码超出范围时本页列表为空
     */
    PageResult<User> findByPage(String keyword, int pageNo, int pageSize) throws DataAccessException;

    /** @param connection 外部连接，不由 DAO 关闭 @param userId 用户编号 @return 用户或空 */
    Optional<User> findById(Connection connection, int userId) throws DataAccessException;
    /** @param connection 外部连接 @param username 登录名 @return 用户或空 */
    Optional<User> findByUsername(Connection connection, String username) throws DataAccessException;
    /** @param connection 外部连接 @param user 待保存数据 @return 新主键 */
    int insert(Connection connection, User user) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户编号 @param hash 新哈希 @param salt 新盐 @return 行数 */
    int updatePassword(Connection connection, int userId, String hash, String salt) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户编号 @param delta 信用增量 @return 行数 */
    int updateCreditScore(Connection connection, int userId, int delta) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户编号 @param status 账号状态 @return 行数 */
    int updateStatus(Connection connection, int userId, int status) throws DataAccessException;
    /** @param connection 外部连接 @param userId 用户编号 @param time 登录时间 @return 行数 */
    int updateLastLogin(Connection connection, int userId, LocalDateTime time) throws DataAccessException;
    /** @param connection 外部连接 @param keyword 关键词 @param pageNo 页码 @param pageSize 页大小 @return 分页 */
    PageResult<User> findByPage(Connection connection, String keyword, int pageNo, int pageSize)
            throws DataAccessException;
}
