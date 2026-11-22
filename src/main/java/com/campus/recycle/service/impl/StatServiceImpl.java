package com.campus.recycle.service.impl;

import com.campus.recycle.dao.StatDao;
import com.campus.recycle.dao.SysConfigDao;
import com.campus.recycle.dao.factory.DaoFactory;
import com.campus.recycle.entity.CategoryStat;
import com.campus.recycle.entity.MonthStat;
import com.campus.recycle.entity.StatOverview;
import com.campus.recycle.entity.StatQuery;
import com.campus.recycle.entity.StatReport;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import com.campus.recycle.exception.ValidationException;
import com.campus.recycle.service.StatService;
import com.campus.recycle.util.CsvExporter;
import com.campus.recycle.util.DBUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 统计业务实现：单一读事务组装报表、跨年月份合并、配置系数校验与 CSV 导出。 */
public final class StatServiceImpl implements StatService {
    private static final String CO2_KEY = "stat.co2.per_item_kg";

    private final DBUtil database;
    private final StatDao stats;
    private final SysConfigDao configs;
    private final CsvExporter exporter;
    private final TxSupport tx;

    /** 使用默认连接工厂与 DAO 构造。 */
    public StatServiceImpl() {
        this(DBUtil.getInstance(), DaoFactory.getStatDao(), DaoFactory.getSysConfigDao(), new CsvExporter());
    }

    /**
     * 注入依赖，便于测试使用独立数据库。
     */
    public StatServiceImpl(DBUtil database, StatDao stats, SysConfigDao configs, CsvExporter exporter) {
        this.database = Objects.requireNonNull(database, "database");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.tx = new TxSupport(database);
    }

    @Override
    public StatReport buildReport(LocalDate from, LocalDate to) {
        StatQuery query = buildQuery(from, to);
        return tx.call(connection -> {
            StatOverview overview = stats.getOverview(connection, query);
            List<CategoryStat> categories = stats.countByCategory(connection, query);
            List<MonthStat> months = collectMonths(connection, from, to, query);
            double coefficient = readCoefficient(connection);
            double co2 = Math.round(coefficient * overview.getCounts().getFlowedItemCount() * 100.0) / 100.0;
            return new StatReport(from, to, overview, categories, months, coefficient, co2);
        });
    }

    @Override
    public Path exportCsv(StatReport report, Path target) throws BusinessException {
        if (report == null || target == null) {
            throw new ValidationException("export", ErrorCode.VALIDATION_ERROR);
        }
        List<String> headers = List.of("统计项", "数值", "说明");
        List<List<?>> rows = new ArrayList<>();
        addRow(rows, "统计起始日期", text(report.getFrom()), "闭区间");
        addRow(rows, "统计结束日期", text(report.getTo()), "闭区间");
        StatOverview overview = report.getOverview();
        addRow(rows, "在架物品数", overview.getCounts().getOnSaleItemCount(), "当前状态");
        addRow(rows, "下架物品数", overview.getCounts().getOffShelfItemCount(), "当前状态");
        addRow(rows, "已流转物品数", overview.getCounts().getFlowedItemCount(), "用于减排估算");
        addRow(rows, "待确认意向数", overview.getCounts().getPendingFlowCount(), "意向记录");
        addRow(rows, "成交记录数", overview.getCounts().getDealFlowCount(), "成交记录");
        addRow(rows, "取消记录数", overview.getCounts().getCancelledFlowCount(), "意向记录");
        addRow(rows, "注册用户数", overview.getUserCount(), "当前快照");
        addRow(rows, "平均信用分", Math.round(overview.getAverageCreditScore() * 100.0) / 100.0, "当前快照");
        addRow(rows, "每件减排系数(kg)", report.getCo2PerItemKg(), "系统配置");
        addRow(rows, "减排估算(kg)", report.getCo2SavedKg(), "已流转物品数 × 系数");
        try {
            return exporter.export(target, headers, rows);
        } catch (IOException | IllegalArgumentException failure) {
            throw new BusinessException(ErrorCode.CSV_EXPORT_FAILED, failure);
        }
    }

    @Override
    public double estimateCo2(int flowedItemCount) throws BusinessException {
        if (flowedItemCount < 0) {
            throw new ValidationException("flowedItemCount", ErrorCode.VALIDATION_ERROR);
        }
        double coefficient = configs.findByKey(CO2_KEY)
                .map(config -> parseCoefficient(config.getConfigValue()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_INVALID));
        return Math.round(coefficient * flowedItemCount * 100.0) / 100.0;
    }

    private double readCoefficient(Connection connection) {
        return configs.findByKey(connection, CO2_KEY)
                .map(row -> parseCoefficient(row.getConfigValue()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_INVALID));
    }

    /** 兼容需要原始值的调用点；配置缺失或损坏按业务错误拒绝，不回退默认值。 */
    private static double parseCoefficient(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < 0) {
                throw new BusinessException(ErrorCode.CONFIG_INVALID);
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, failure);
        }
    }

    private List<MonthStat> collectMonths(Connection connection, LocalDate from, LocalDate to, StatQuery query) {
        int firstYear = from != null ? from.getYear() : (to != null ? to.getYear() : Year.now().getValue());
        int lastYear = to != null ? to.getYear() : firstYear;
        List<MonthStat> result = new ArrayList<>();
        for (int year = firstYear; year <= lastYear; year++) {
            result.addAll(stats.countByMonth(connection, String.format("%04d", year), query));
        }
        return result;
    }

    private static StatQuery buildQuery(LocalDate from, LocalDate to) {
        StatQuery.Builder builder = StatQuery.builder();
        if (from != null) { builder.from(from); }
        if (to != null) { builder.to(to); }
        try {
            return builder.build();
        } catch (IllegalArgumentException failure) {
            throw new ValidationException("date", ErrorCode.VALIDATION_ERROR, failure);
        }
    }

    private static void addRow(List<List<?>> rows, String label, Object value, String note) {
        rows.add(List.of(label, value, note));
    }

    private static String text(LocalDate date) {
        return date == null ? "不限" : date.toString();
    }
}
