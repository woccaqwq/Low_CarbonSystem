package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.CategoryDao;
import com.campus.recycle.entity.Category;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** 分类 JDBC 实现，根分类重名校验与写入合并在单条 SQL 中。 */
public final class CategoryDaoImpl extends BaseDao<Category> implements CategoryDao {
    private static final String SELECT = "SELECT category_id,name,parent_id,sort_no FROM category";

    /** 使用默认数据库。 */
    public CategoryDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 注入数据库工厂 */
    public CategoryDaoImpl(DBUtil database) { super(database); }

    @Override
    public Optional<Category> findById(int categoryId) {
        return withConnection(connection -> findById(connection, categoryId));
    }
    @Override
    public List<Category> findAll() {
        return withConnection(this::findAll);
    }
    @Override
    public int insert(Category category) {
        return withConnection(connection -> insert(connection, category));
    }
    @Override
    public int update(Category category) {
        return withConnection(connection -> update(connection, category));
    }
    @Override
    public int deleteById(int categoryId) {
        return withConnection(connection -> deleteById(connection, categoryId));
    }
    @Override
    public Optional<Category> findById(Connection connection, int categoryId) {
        return onConnection(connection, current -> query(current, SELECT + " WHERE category_id=?", categoryId)
                .stream().findFirst());
    }
    @Override
    public List<Category> findAll(Connection connection) {
        return onConnection(connection, current -> query(current, SELECT + " ORDER BY sort_no,category_id"));
    }
    @Override
    public int insert(Connection connection, Category category) {
        validate(category);
        return onConnection(connection, current -> insertAndReturnId(current,
                "INSERT INTO category(name,parent_id,sort_no) SELECT ?,?,? WHERE NOT EXISTS "
                        + "(SELECT 1 FROM category WHERE parent_id IS ? AND name=?)",
                category.getName(), category.getParentId(), category.getSortNo(),
                category.getParentId(), category.getName()));
    }
    @Override
    public int update(Connection connection, Category category) {
        validate(category);
        return onConnection(connection, current -> {
            int changed = update(current, "UPDATE category SET name=?,parent_id=?,sort_no=? WHERE category_id=? "
                            + "AND NOT EXISTS (SELECT 1 FROM category WHERE parent_id IS ? AND name=? AND category_id<>?)",
                    category.getName(), category.getParentId(), category.getSortNo(), category.getCategoryId(),
                    category.getParentId(), category.getName(), category.getCategoryId());
            if (changed == 0 && count(current, "SELECT count(*) FROM category WHERE category_id=?",
                    category.getCategoryId()) != 0) {
                throw new SQLException("同级分类名称重复");
            }
            return changed;
        });
    }
    @Override
    public int deleteById(Connection connection, int categoryId) {
        return onConnection(connection, current -> update(current, "DELETE FROM category WHERE category_id=?",
                categoryId));
    }
    @Override
    protected Category mapRow(ResultSet result) throws SQLException {
        int parent = result.getInt("parent_id");
        Integer parentId = result.wasNull() ? null : parent;
        Category category = new Category(result.getString("name"), parentId, result.getInt("sort_no"));
        category.setCategoryId(result.getInt("category_id"));
        return category;
    }

    private static void validate(Category category) {
        if (category == null || category.getName() == null || category.getName().isBlank()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
    }
}
