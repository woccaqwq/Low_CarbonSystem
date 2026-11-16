package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.Role;
import java.time.LocalDateTime;

/** 用户数据与自身不变量；不持有明文口令，不负责登录或访问数据库。 */
public final class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String salt;
    private String nickname = "";
    private Role role = Role.STUDENT;
    private int creditScore = 100;
    private int status;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    /** 创建待填充的用户，默认学生、正常、信用分 100。 */
    public User() {
    }

    /**
     * 创建注册数据，用户名合法性与密码强度由 Service 校验。
     * @param username 登录名
     * @param passwordHash 派生哈希，不接受明文口令
     * @param salt Base64 盐
     * @param nickname 昵称
     */
    public User(String username, String passwordHash, String salt, String nickname) {
        setUsername(username);
        setPasswordHash(passwordHash);
        setSalt(salt);
        setNickname(nickname);
    }

    /** @return 主键，尚未持久化时为 0 */
    public int getUserId() { return userId; }
    /** @param userId 非负主键 */
    public void setUserId(int userId) {
        if (userId < 0) { throw new IllegalArgumentException("用户编号不能为负数"); }
        this.userId = userId;
    }
    /** @return 登录名 */
    public String getUsername() { return username; }
    /** @param username 非空登录名 */
    public void setUsername(String username) { this.username = requireText(username, "用户名"); }
    /** @return 哈希，仅供内部认证使用，不得显示或记录 */
    public String getPasswordHash() { return passwordHash; }
    /** @param passwordHash 非空派生哈希 */
    public void setPasswordHash(String passwordHash) { this.passwordHash = requireText(passwordHash, "哈希"); }
    /** @return 盐，仅供内部认证使用 */
    public String getSalt() { return salt; }
    /** @param salt 非空盐 */
    public void setSalt(String salt) { this.salt = requireText(salt, "盐"); }
    /** @return 昵称 */
    public String getNickname() { return nickname; }
    /** @param nickname 昵称，不能为 null，可为空串 */
    public void setNickname(String nickname) {
        if (nickname == null) { throw new IllegalArgumentException("昵称不能为 null"); }
        this.nickname = nickname;
    }
    /** @return 用户角色 */
    public Role getRole() { return role; }
    /** @param role 非空角色 */
    public void setRole(Role role) {
        if (role == null) { throw new IllegalArgumentException("角色不能为空"); }
        this.role = role;
    }
    /** @return 信用分 */
    public int getCreditScore() { return creditScore; }
    /** @param creditScore 0–150 之间的信用分 */
    public void setCreditScore(int creditScore) {
        if (creditScore < 0 || creditScore > 150) { throw new IllegalArgumentException("信用分超出范围"); }
        this.creditScore = creditScore;
    }
    /** @return 0 正常，1 禁用 */
    public int getStatus() { return status; }
    /** @param status 0 正常，1 禁用 */
    public void setStatus(int status) {
        if (status != 0 && status != 1) { throw new IllegalArgumentException("账号状态不合法"); }
        this.status = status;
    }
    /** @return 创建时间，持久化前可为 null */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt 创建时间，null 表示使用数据库默认值 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** @return 最后登录时间，从未登录时为 null */
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    /** @param lastLoginAt 最后登录时间，可为 null */
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    /** @return 是否为管理员；具体权限仍由 Service 校验 */
    public boolean isAdmin() { return role == Role.ADMIN; }
    /** @return 账号是否正常 */
    public boolean isEnabled() { return status == 0; }
    /** @param delta 信用分增量，结果限制在 0–150，使用 long 防止溢出 */
    public void adjustCredit(int delta) {
        creditScore = (int) Math.max(0L, Math.min(150L, (long) creditScore + delta));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(field + "不能为空"); }
        return value;
    }

    /** @return 不含哈希和盐的调试描述 */
    @Override
    public String toString() {
        return "User{userId=" + userId + ", username='" + username + "', role=" + role + "}";
    }
}
