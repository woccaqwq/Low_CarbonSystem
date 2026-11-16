package com.campus.recycle.dao;

import com.campus.recycle.entity.Category;
import com.campus.recycle.exception.DataAccessException;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** 分类数据契约；Connection 重载不关闭、提交或回滚外部连接。 */
public interface CategoryDao {
    /** @param categoryId 编号 @return 分类或空 */
    Optional<Category> findById(int categoryId) throws DataAccessException;
    /** @return 全部分类型，按 sort_no、category_id 升序 */
    List<Category> findAll() throws DataAccessException;
    /** @param category 分类数据 @return 新主键，不修改输入对象 */
    int insert(Category category) throws DataAccessException;
    /** @param category 含编号的数据 @return 受影响行数 */
    int update(Category category) throws DataAccessException;
    /** @param categoryId 编号 @return 删除行数，有子分类时外键拒绝删除 */
    int deleteById(int categoryId) throws DataAccessException;
    /** @param connection 外部连接 @param categoryId 编号 @return 分类或空 */
    Optional<Category> findById(Connection connection, int categoryId) throws DataAccessException;
    /** @param connection 外部连接 @return 全部分类 */
    List<Category> findAll(Connection connection) throws DataAccessException;
    /** @param connection 外部连接 @param category 分类数据 @return 新主键 */
    int insert(Connection connection, Category category) throws DataAccessException;
    /** @param connection 外部连接 @param category 分类数据 @return 更新行数 */
    int update(Connection connection, Category category) throws DataAccessException;
    /** @param connection 外部连接 @param categoryId 编号 @return 删除行数 */
    int deleteById(Connection connection, int categoryId) throws DataAccessException;
}
