package com.campus.recycle.service;

import com.campus.recycle.entity.User;
import com.campus.recycle.entity.OperationLog;
import com.campus.recycle.dao.CategoryDao;
import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.SysConfigDao;
import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.impl.CategoryDaoImpl;
import com.campus.recycle.dao.impl.ItemDaoImpl;
import com.campus.recycle.dao.impl.OperationLogDaoImpl;
import com.campus.recycle.dao.impl.SysConfigDaoImpl;
import com.campus.recycle.dao.impl.UserDaoImpl;
import com.campus.recycle.entity.Category;
import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.entity.StatReport;
import com.campus.recycle.entity.enums.ItemStatus;
import com.campus.recycle.entity.enums.Role;
import com.campus.recycle.entity.enums.TradeType;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.service.impl.AdminServiceImpl;
import com.campus.recycle.service.impl.StatServiceImpl;
import com.campus.recycle.util.CsvExporter;
import com.campus.recycle.util.DBUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 统计业务与管理员业务：报表快照、减排估算、CSV 导出与角色复核。 */
class StatAndAdminServiceTest {
    @TempDir Path directory;
    private DBUtil database;
    private UserDao users;
    private ItemDao items;
    private OperationLogDao logs;
    private SessionUser admin;
    private SessionUser student;
    private StatService statService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        database = DBUtil.forPath(directory.resolve("stat-admin-service.db"));
        ServiceTestSupport.clearBusinessData(database);
        users = new UserDaoImpl(database);
        CategoryDao categories = new CategoryDaoImpl(database);
        items = new ItemDaoImpl(database);
        logs = new OperationLogDaoImpl(database);
        SysConfigDao configs = new SysConfigDaoImpl(database);
        statService = new StatServiceImpl(database, DaoFactoryStatDao(), configs, new CsvExporter());
        adminService = new AdminServiceImpl(database, items, users, logs);

        int adminId = users.insert(new User("admin_x", "hash", "salt", "管理员"));
        try (var connection = database.getConnection();
             var statement = connection.prepareStatement("UPDATE sys_user SET role = 'ADMIN' WHERE user_id = ?")) {
            statement.setInt(1, adminId);
            statement.executeUpdate();
        } catch (java.sql.SQLException failure) {
            throw new AssertionError(failure);
        }
        int studentId = users.insert(new User("student_x", "hash", "salt", "学生"));
        admin = new SessionUser(adminId, "admin_x", "管理员", Role.ADMIN, 100);
        student = new SessionUser(studentId, "student_x", "学生", Role.STUDENT, 100);
        int category = categories.insert(new Category("教材", null, 1));

        Item onSale = new Item();
        onSale.setOwnerId(studentId);
        onSale.setCategoryId(category);
        onSale.setTitle("在架教材");
        onSale.setTradeType(TradeType.SALE);
        onSale.setPrice(20);
        int onSaleId = items.insert(onSale);

        Item flowed = new Item();
        flowed.setOwnerId(studentId);
        flowed.setCategoryId(category);
        flowed.setTitle("已流转教材");
        flowed.setTradeType(TradeType.SALE);
        flowed.setPrice(15);
        int flowedId = items.insert(flowed);
        items.updateStatus(flowedId, ItemStatus.FLOWED);
        assertTrue(onSaleId > 0 && flowedId > 0);
    }

    private com.campus.recycle.dao.StatDao DaoFactoryStatDao() {
        return new com.campus.recycle.dao.impl.StatDaoImpl(database);
    }

    @Test
    void buildsReportAndEstimatesCo2() {
        StatReport report = statService.buildReport(LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        assertEquals(2, report.getOverview().getCounts().getItemCount());
        assertEquals(1, report.getOverview().getCounts().getOnSaleItemCount());
        assertEquals(1, report.getOverview().getCounts().getFlowedItemCount());
        assertEquals(2.4, report.getCo2PerItemKg());
        assertEquals(2.4, report.getCo2SavedKg());
        assertFalse(report.getCategories().isEmpty());
        assertFalse(report.getMonths().isEmpty());
        assertEquals(9.6, statService.estimateCo2(4));
        assertThrows(BusinessException.class, () -> statService.estimateCo2(-1));
    }

    @Test
    void exportsUtf8BomCsvWithNumbers() throws Exception {
        StatReport report = statService.buildReport(null, null);
        Path target = directory.resolve("report.csv");
        Path exported = statService.exportCsv(report, target);
        assertEquals(target.toAbsolutePath().normalize(), exported);
        String content = Files.readString(exported, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFF"));
        assertTrue(content.contains("已流转物品数"));
        assertTrue(content.contains("减排估算"));
        assertThrows(BusinessException.class, () -> statService.exportCsv(report, target));
    }

    @Test
    void adminOfflineItemAndToggleUserWithRoleCheck() {
        int itemId = items.search(com.campus.recycle.entity.ItemQuery.builder()
                .status(ItemStatus.ON_SALE).build()).getItems().get(0).getItemId();
        adminService.offlineIllegalItem(itemId, "违规内容", admin);
        assertEquals(ItemStatus.OFF_SHELF, items.findById(itemId).orElseThrow().getStatus());
        assertTrue(logs.findRecent(10).stream()
                .anyMatch(log -> "ADMIN_OFFLINE".equals(log.getAction()) && log.getDetail().contains("违规内容")));

        assertThrows(BusinessException.class, () -> adminService.offlineIllegalItem(itemId, "理由", student));
        assertThrows(BusinessException.class,
                () -> adminService.toggleUserStatus(admin.getUserId(), false, admin));
        adminService.toggleUserStatus(student.getUserId(), false, admin);
        assertEquals(1, users.findById(student.getUserId()).orElseThrow().getStatus());
        List<OperationLog> recent = adminService.recentLogs(10);
        assertFalse(recent.isEmpty());
    }
}
