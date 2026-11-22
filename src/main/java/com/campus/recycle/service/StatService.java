package com.campus.recycle.service;

import com.campus.recycle.entity.StatReport;
import com.campus.recycle.exception.BusinessException;
import java.nio.file.Path;
import java.time.LocalDate;

/** 统计业务：同快照报表组装、CSV 导出与减碳估算。范围由会话身份决定，不信任界面参数。 */
public interface StatService {
    /**
     * 在单一读事务中组装报表；日期闭区间，跨年按月逐年合并。
     * @param from 起始日期或 null @param to 结束日期或 null
     * @return 同一快照的不可变报表
     */
    StatReport buildReport(LocalDate from, LocalDate to);

    /**
     * 导出当前报表快照；不重新查询数据库、不扩大数据范围。
     * @param report 已获准的报表 @param target 不存在的新文件路径
     * @return 导出文件的绝对路径 @throws BusinessException 参数不合法或导出失败
     */
    Path exportCsv(StatReport report, Path target) throws BusinessException;

    /**
     * 按配置的每件减排系数估算；系数为有限非负数。
     * @param flowedItemCount 已流转物品数 @return 估算减排千克数
     * @throws BusinessException 数量越界或配置缺失/损坏
     */
    double estimateCo2(int flowedItemCount) throws BusinessException;
}
