package com.campus.recycle.dao.impl;

import com.campus.recycle.dao.SysConfigDao;
import com.campus.recycle.entity.SysConfig;
import com.campus.recycle.util.DBUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;

/** 配置 JDBC 读取；原文返回配置值，业务类型解析与写入不属于本 DAO。 */
public final class SysConfigDaoImpl extends BaseDao<SysConfig> implements SysConfigDao {
    private static final String SELECT = "SELECT config_key,config_value,updated_at FROM sys_config";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    /** 使用默认数据库。 */
    public SysConfigDaoImpl() { this(DBUtil.getInstance()); }
    /** @param database 独立数据库工厂 */
    public SysConfigDaoImpl(DBUtil database) { super(database); }

    @Override
    public Optional<SysConfig> findByKey(String key) {
        return withConnection(connection -> findByKey(connection, key));
    }
    @Override
    public List<SysConfig> findAll() {
        return withConnection(this::findAll);
    }
    @Override
    public Optional<SysConfig> findByKey(Connection connection, String key) {
        if (key == null || key.isBlank()) { throw new IllegalArgumentException("配置查询键不能为空"); }
        return onConnection(connection, current -> query(current, SELECT + " WHERE config_key=?", key)
                .stream().findFirst());
    }
    @Override
    public List<SysConfig> findAll(Connection connection) {
        return onConnection(connection, current -> List.copyOf(query(current, SELECT + " ORDER BY config_key ASC")));
    }
    @Override
    protected SysConfig mapRow(ResultSet result) throws SQLException {
        try {
            String timestamp = result.getString("updated_at");
            if (timestamp == null) { throw new SQLException("配置记录缺少更新时间"); }
            return new SysConfig(result.getString("config_key"), result.getString("config_value"),
                    LocalDateTime.parse(timestamp, DATE_FORMAT));
        } catch (IllegalArgumentException | DateTimeException failure) {
            throw new SQLException("配置记录包含非法字段", failure);
        }
    }
}
