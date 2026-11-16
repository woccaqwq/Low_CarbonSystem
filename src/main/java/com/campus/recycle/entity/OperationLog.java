package com.campus.recycle.entity;

import java.time.LocalDateTime;

/** 业务操作日志；调用方须使用安全摘要，禁止传入口令、哈希、盐或原始表单。 */
public final class OperationLog {
    private int logId;
    private Integer userId;
    private String action;
    private String detail = "";
    private LocalDateTime createdAt;
    private String username;
    private String nickname;

    /** 创建待填充日志，保存前必须设置动作。 */
    public OperationLog() { }
    /** @param userId 操作人，null 表示系统或匿名 @param action 非空白动作 @param detail 非 null 安全摘要 */
    public OperationLog(Integer userId, String action, String detail) {
        setUserId(userId);
        setAction(action);
        setDetail(detail);
    }
    /** @return 主键，未保存时为 0 */
    public int getLogId() { return logId; }
    /** @param logId 非负主键 */
    public void setLogId(int logId) {
        if (logId < 0) { throw new IllegalArgumentException("日志编号不合法"); }
        this.logId = logId;
    }
    /** @return 操作人编号，系统、匿名或已删除用户为 null */
    public Integer getUserId() { return userId; }
    /** @param userId 正整数或 null */
    public void setUserId(Integer userId) {
        if (userId != null && userId <= 0) { throw new IllegalArgumentException("操作人编号不合法"); }
        this.userId = userId;
    }
    /** @return 动作代码 */
    public String getAction() { return action; }
    /** @param action 非空白动作，建议使用固定常量 */
    public void setAction(String action) {
        if (action == null || action.isBlank()) { throw new IllegalArgumentException("日志动作不能为空"); }
        this.action = action;
    }
    /** @return 安全摘要 */
    public String getDetail() { return detail; }
    /** @param detail 非 null，可为空串；DAO 不自动识别或清除敏感信息 */
    public void setDetail(String detail) {
        if (detail == null) { throw new IllegalArgumentException("日志摘要不能为 null"); }
        this.detail = detail;
    }
    /** @return 创建时间，保存前可为 null */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt null 使用数据库当前时间；持久化精确到秒 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** @return 操作人当前用户名的查询投影，不是历史快照 */
    public String getUsername() { return username; }
    /** @param username 仅供展示，不参与插入 */
    public void setUsername(String username) { this.username = username; }
    /** @return 操作人当前昵称的查询投影，不是历史快照 */
    public String getNickname() { return nickname; }
    /** @param nickname 仅供展示，不参与插入 */
    public void setNickname(String nickname) { this.nickname = nickname; }

    /** 调试描述省略摘要，调用方仍须保证 action 为安全动作代码。 */
    @Override
    public String toString() {
        return "OperationLog{logId=" + logId + ", userId=" + userId + ", action='" + action + "'}";
    }
}
