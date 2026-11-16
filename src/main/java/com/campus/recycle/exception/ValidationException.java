package com.campus.recycle.exception;

/** 表单或参数校验失败；field 用固定字段标识，禁止传入用户填写的值。 */
public final class ValidationException extends BusinessException {
    private static final long serialVersionUID = 1L;
    private final String field;

    /** @param code 非 null 错误码；错误针对整个表单 */
    public ValidationException(ErrorCode code) { this(null, code, null); }

    /** @param code 非 null 错误码 @param cause 可空原因；错误针对整个表单 */
    public ValidationException(ErrorCode code, Throwable cause) { this(null, code, cause); }

    /** @param field 固定字段标识，null 表示整个表单 @param code 非 null 错误码 */
    public ValidationException(String field, ErrorCode code) { this(field, code, null); }

    /**
     * @param field 非空白固定字段标识，或 null 表示整个表单
     * @param code 非 null 错误码
     * @param cause 可空原始原因，不拼入提示
     * @throws IllegalArgumentException field 非 null 且为空白
     * @throws NullPointerException code 为 null
     */
    public ValidationException(String field, ErrorCode code, Throwable cause) {
        super(code, cause);
        if (field != null && field.isBlank()) { throw new IllegalArgumentException("校验字段标识不能是空白"); }
        this.field = field;
    }

    /** @return 需定位的字段标识，null 表示表单级错误 */
    public String getField() { return field; }
}
