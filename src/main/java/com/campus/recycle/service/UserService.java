package com.campus.recycle.service;

import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.entity.User;
import com.campus.recycle.exception.BusinessException;

/** 账号业务：注册、登录、改密与管理员分页查询。认证与授权规则集中在此层。 */
public interface UserService {
    /**
     * 校验凭据并返回不含口令的安全快照；不存在、口令错误与禁用按固定提示拒绝。
     * @param username 登录名 @param rawPassword 明文口令
     * @return 登录成功的安全快照
     * @throws BusinessException 凭据错误、账号禁用或连续失败被锁定
     */
    SessionUser login(String username, String rawPassword) throws BusinessException;

    /**
     * 注册学生账号。
     * @param username 3–20 位字母数字下划线 @param rawPassword 6–20 位且含字母与数字 @param nickname 昵称
     * @throws BusinessException 用户名重复或参数不合法
     */
    void register(String username, String rawPassword, String nickname) throws BusinessException;

    /**
     * 修改口令，需验证旧口令，改密后旧口令立即失效。
     * @param userId 用户编号 @param oldPwd 旧口令 @param newPwd 新口令
     * @throws BusinessException 用户不存在、旧口令错误或新口令不合法
     */
    void changePassword(int userId, String oldPwd, String newPwd) throws BusinessException;

    /**
     * 分页查询用户；返回对象含认证字段，界面与 CSV 不得展示哈希和盐。
     * @param keyword 用户名/昵称关键词 @param pageNo 页码 @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<User> pageUsers(String keyword, int pageNo, int pageSize);
}
