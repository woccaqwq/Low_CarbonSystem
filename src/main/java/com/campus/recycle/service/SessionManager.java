package com.campus.recycle.service;

import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.exception.BusinessException;
import com.campus.recycle.exception.ErrorCode;
import java.util.Optional;

/**
 * 内存会话持有当前登录快照，供界面导航与展示。快照不是实时凭证：
 * 每次受限业务仍由 Service 在事务内重新读取账号并校验角色与状态。
 */
public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();

    private volatile SessionUser current;

    /** @return 全局会话管理器单例 */
    public static SessionManager getInstance() { return INSTANCE; }

    private SessionManager() { }

    /** @param user 登录成功返回的快照，null 表示退出登录 */
    public void setCurrent(SessionUser user) { current = user; }

    /** 退出登录，清除当前快照。 */
    public void logout() { current = null; }

    /** @return 当前快照；未登录为空 */
    public Optional<SessionUser> current() { return Optional.ofNullable(current); }

    /** @return 是否已登录 */
    public boolean isLoggedIn() { return current != null; }

    /**
     * @return 当前快照
     * @throws BusinessException 未登录
     */
    public SessionUser requireCurrent() throws BusinessException {
        SessionUser user = current;
        if (user == null) { throw new BusinessException(ErrorCode.PERMISSION_DENIED); }
        return user;
    }

    /**
     * 仅用于界面导航判断；实际权限仍由 Service 复核。
     * @return 当前用户是否为管理员，未登录为 false
     */
    public boolean isAdmin() {
        SessionUser user = current;
        return user != null && user.isAdmin();
    }
}
