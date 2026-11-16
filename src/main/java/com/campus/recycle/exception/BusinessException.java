package com.campus.recycle.exception;

import java.util.Objects;

/** 可预期业务拒绝；界面显示固定 message，技术 cause 不向用户展示。 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode code;

    /** @param code 非 null 错误码 */
    public BusinessException(ErrorCode code) { this(code, null); }

    /**
     * @param code 非 null 错误码
     * @param cause 可空原始原因，不拼入用户提示
     * @throws NullPointerException code 为 null
     */
    public BusinessException(ErrorCode code, Throwable cause) {
        super(Objects.requireNonNull(code, "code").getMessage(), cause);
        this.code = code;
    }

    /** @return 稳定错误码 */
    public ErrorCode getCode() { return code; }
}
