package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.CategoryDao;
import com.campus.recycle.entity.Category;
import com.campus.recycle.exception.DataAccessException;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryDaoImplTest {
    @TempDir Path directory;
    private DBUtil database;
    private CategoryDao categories;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("categories.db"));
        categories = new CategoryDaoImpl(database);
    }

    @Test
    void readsSeedCategoriesIncludingNullParent() {
        Category root = categories.findById(1).orElseThrow();
        assertEquals("教材书籍", root.getName());
        assertTrue(root.isRoot());
        assertNull(root.getParentId());
        assertEquals(1, categories.findById(11).orElseThrow().getParentId());
        assertEquals(14, categories.findAll().size());
        assertTrue(categories.findById(9999).isEmpty());
    }

    @Test
    void ordersBySortNumberThenIdDeterministically() {
        List<Category> all = categories.findAll();
        assertEquals(List.of(1, 11, 21, 31, 41), all.subList(0, 5).stream()
                .map(Category::getCategoryId).toList());
    }

    @Test
    void insertsUpdatesAndDeletesUnusedCategories() {
        Category input = new Category("新类别", null, 10);
        int id = categories.insert(input);
        assertTrue(id > 42);
        assertEquals(0, input.getCategoryId());
        Category saved = categories.findById(id).orElseThrow();
        saved.setName("修改后");
        saved.setParentId(1);
        saved.setSortNo(20);
        assertEquals(1, categories.update(saved));
        Category changed = new CategoryDaoImpl(database).findById(id).orElseThrow();
        assertEquals("修改后", changed.getName());
        assertEquals(1, changed.getParentId());
        assertEquals(20, changed.getSortNo());
        assertEquals(1, categories.deleteById(id));
        assertTrue(categories.findById(id).isEmpty());
        assertEquals(0, categories.deleteById(id));
        assertEquals(0, categories.update(saved));
    }

    @Test
    void rejectsDuplicateRootAndSiblingNamesButAllowsDifferentParents() {
        assertThrows(DataAccessException.class, () -> categories.insert(new Category("教材书籍", null, 9)));
        assertThrows(DataAccessException.class, () -> categories.insert(new Category("专业课教材", 1, 9)));
        int id = categories.insert(new Category("专业课教材", 2, 9));
        assertTrue(categories.findById(id).isPresent());
        Category root = categories.findById(2).orElseThrow();
        root.setName("教材书籍");
        assertThrows(DataAccessException.class, () -> categories.update(root));
        Category sibling = categories.findById(12).orElseThrow();
        sibling.setName("专业课教材");
        assertThrows(DataAccessException.class, () -> categories.update(sibling));
        assertEquals(15, categories.findAll().size());
    }

    @Test
    void protectsParentsAndNullsItemCategoryWhenDeletingLeaf() throws Exception {
        assertThrows(DataAccessException.class, () -> categories.deleteById(1));
        assertEquals(1, categories.deleteById(11));
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT category_id FROM item WHERE item_id=1")) {
            assertTrue(result.next());
            assertNull(result.getObject(1));
        }
    }

    @Test
    void rejectsMissingParentAndTreatsNamesAsParameters() {
        assertThrows(DataAccessException.class, () -> categories.insert(new Category("无效父级", 9999, 0)));
        int id = categories.insert(new Category("x'); DROP TABLE category;--", null, 0));
        assertEquals("x'); DROP TABLE category;--", categories.findById(id).orElseThrow().getName());
        assertEquals(15, categories.findAll().size());
    }

    @Test
    void participatesInCallerTransactionAndRollsBackAllChanges() throws Exception {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            int id = categories.insert(connection, new Category("临时", null, 0));
            Category inserted = categories.findById(connection, id).orElseThrow();
            inserted.setName("临时修改");
            assertEquals(1, categories.update(connection, inserted));
            assertEquals(1, categories.deleteById(connection, 13));
            assertEquals(14, categories.findAll(connection).size());
            assertFalse(connection.getAutoCommit());
            assertFalse(connection.isClosed());
            connection.rollback();
        }
        assertEquals(14, categories.findAll().size());
        assertTrue(categories.findById(13).isPresent());
        assertTrue(categories.findAll().stream().noneMatch(category -> category.getName().startsWith("临时")));
    }

    @Test
    void validatesNameAndImmediateSelfParent() {
        assertThrows(IllegalArgumentException.class, () -> new Category(" ", null, 0));
        Category category = new Category("有效", 1, 0);
        assertThrows(IllegalArgumentException.class, () -> category.setCategoryId(1));
        Category existing = categories.findById(1).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> existing.setParentId(1));
        assertThrows(IllegalArgumentException.class, () -> categories.insert(new Category()));
    }
}
