package com.campus.recycle.exception;

/** 数据库技术故障。界面应显示通用提示，将 cause 留给日志。 */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * 创建数据访问异常。
     * @param message 不包含口令或 SQL 参数的故障说明
     * @param cause 原始异常，供调用方记录日志
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
