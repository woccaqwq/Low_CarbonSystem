package com.campus.recycle.entity;

import com.campus.recycle.entity.enums.Role;
import java.util.Objects;

/** 不含凭据的不可变用户快照；不是身份凭证，认证与实时授权由 Service 负责。 */
public final class SessionUser {
    private final int userId;
    private final String username;
    private final String nickname;
    private final Role role;
    private final int creditScore;

    /**
     * @param userId 正整数持久化编号
     * @param username 非空白登录名
     * @param nickname 非 null 昵称，可为空串
     * @param role 非 null 角色
     * @param creditScore 0–150 的信用分
     * @throws IllegalArgumentException 字段不合法
     */
    public SessionUser(int userId, String username, String nickname, Role role, int creditScore) {
        if (userId <= 0) { throw new IllegalArgumentException("会话用户编号须为正整数"); }
        if (username == null || username.isBlank()) { throw new IllegalArgumentException("会话用户名不能为空"); }
        if (nickname == null) { throw new IllegalArgumentException("会话昵称不能为 null"); }
        if (role == null) { throw new IllegalArgumentException("会话角色不能为空"); }
        if (creditScore < 0 || creditScore > 150) { throw new IllegalArgumentException("会话信用分超出范围"); }
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.creditScore = creditScore;
    }

    /**
     * 复制安全展示字段，不保留 User 引用，不检查口令或账号状态。
     * @param user 非 null 的已持久化用户；登录校验应由 Service 先完成
     * @return 独立快照，源对象或数据库后续变化不会自动更新它
     * @throws NullPointerException user 为 null
     * @throws IllegalArgumentException 用户尚未持久化或展示字段不合法
     */
    public static SessionUser from(User user) {
        Objects.requireNonNull(user, "user");
        return new SessionUser(user.getUserId(), user.getUsername(), user.getNickname(), user.getRole(),
                user.getCreditScore());
    }

    /** @return 用户编号 */
    public int getUserId() { return userId; }
    /** @return 登录名 */
    public String getUsername() { return username; }
    /** @return 昵称 */
    public String getNickname() { return nickname; }
    /** @return 创建快照时的角色，不能替代实时授权检查 */
    public Role getRole() { return role; }
    /** @return 创建快照时的信用分；业务更新后应重新查询并替换快照 */
    public int getCreditScore() { return creditScore; }
    /** @return 快照角色是否为管理员，仅供展示和界面导航 */
    public boolean isAdmin() { return role == Role.ADMIN; }

    /** @return 仅含编号、角色和信用分的调试描述 */
    @Override
    public String toString() {
        return "SessionUser{userId=" + userId + ", role=" + role + ", creditScore=" + creditScore + "}";
    }
}
