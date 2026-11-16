package com.campus.recycle.dao;

import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.exception.DataAccessException;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** 只追加的业务日志存取；权限由 Service 检查，外部连接的事务由调用方管理。 */
public interface OperationLogDao {
    /** @param log 日志 @return 新主键，不修改输入对象；重复调用仍追加新记录 */
    int insert(OperationLog log) throws DataAccessException;
    /** @param limit 1–1000 @return 最近日志，按创建时间、编号倒序 */
    List<OperationLog> findRecent(int limit) throws DataAccessException;
    /** @param logId 正整数主键 @return 日志或空 */
    Optional<OperationLog> findById(int logId) throws DataAccessException;
    /** @param userId 正整数或 null（不限） @param action 精确动作或 null（不限），不可空白
     * @param pageNo 页码 1 起 @param pageSize 10–100 @return 筛选分页，创建时间、编号倒序 */
    PageResult<OperationLog> findByPage(Integer userId, String action, int pageNo, int pageSize)
            throws DataAccessException;
    /** @param connection 外部连接，不关闭、不提交、不回滚 @param log 日志 @return 新主键 */
    int insert(Connection connection, OperationLog log) throws DataAccessException;
    /** @param connection 外部连接 @param limit 1–1000 @return 最近日志 */
    List<OperationLog> findRecent(Connection connection, int limit) throws DataAccessException;
    /** @param connection 外部连接 @param logId 正整数主键 @return 日志或空 */
    Optional<OperationLog> findById(Connection connection, int logId) throws DataAccessException;
    /** @param connection 外部连接 @param userId 正整数或 null @param action 精确动作或 null
     * @param pageNo 页码 1 起 @param pageSize 10–100 @return 筛选分页 */
    PageResult<OperationLog> findByPage(Connection connection, Integer userId, String action,
                                      int pageNo, int pageSize) throws DataAccessException;
}
