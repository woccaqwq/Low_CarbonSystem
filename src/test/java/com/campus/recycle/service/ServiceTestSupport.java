package com.campus.recycle.service;

import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.Statement;

/** 测试辅助：清空迭代一初始化脚本带入的业务数据，仅保留表结构与系统配置。 */
final class ServiceTestSupport {
    private ServiceTestSupport() { }

    static void clearBusinessData(DBUtil database) {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            statement.executeUpdate("DELETE FROM review");
            statement.executeUpdate("DELETE FROM flow_record");
            statement.executeUpdate("DELETE FROM favorite");
            statement.executeUpdate("DELETE FROM item");
            statement.executeUpdate("DELETE FROM category");
            statement.executeUpdate("DELETE FROM operation_log");
            statement.executeUpdate("DELETE FROM sys_user");
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (Exception failure) {
            throw new AssertionError("清理测试数据失败", failure);
        }
    }
}
