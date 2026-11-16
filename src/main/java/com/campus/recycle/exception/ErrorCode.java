package com.campus.recycle.exception;

/** 业务边界使用的稳定错误标识与固定提示；不得向提示拼接原始输入或技术异常。 */
public enum ErrorCode {
    USER_NOT_FOUND("用户名或密码错误"),
    PASSWORD_ERROR("用户名或密码错误"),
    USER_DISABLED("账号已被禁用，请联系管理员"),
    USERNAME_DUPLICATED("该用户名已被占用"),
    ITEM_NOT_ON_SALE("该物品已下架或已流转"),
    ITEM_OWN_SELF("不能对自己发布的物品发起意向"),
    FLOW_DUPLICATED("你已对该物品发起过意向"),
    FLOW_STATUS_ILLEGAL("当前状态不支持该操作"),
    PERMISSION_DENIED("没有操作权限"),
    REVIEW_NOT_ALLOWED("当前不可评价：未成交或已评价"),
    FLOW_NOT_FOUND("流转记录不存在"),
    DB_ERROR("数据操作失败，请联系管理员"),
    VALIDATION_ERROR("输入参数不合法，请检查后重试");

    private final String message;

    ErrorCode(String message) { this.message = message; }

    /** @return 固定用户提示，不含输入值或内部异常原因 */
    public String getMessage() { return message; }
}
