package com.campus.recycle.service;

import com.campus.recycle.entity.Item;
import com.campus.recycle.entity.ItemForm;
import com.campus.recycle.entity.ItemQuery;
import com.campus.recycle.entity.PageResult;
import com.campus.recycle.entity.SessionUser;
import com.campus.recycle.exception.BusinessException;
import java.util.Optional;

/** 物品业务：发布、维护、检索与收藏，所有权与状态规则集中在此层。 */
public interface ItemService {
    /** @param form 已校验表单 @param owner 发布人快照 @return 新物品编号 @throws BusinessException 参数或分类不合法 */
    int publish(ItemForm form, SessionUser owner) throws BusinessException;

    /** @param itemId 物品编号 @param form 修改内容 @param operator 操作人 @throws BusinessException 非本人或状态不允许 */
    void update(int itemId, ItemForm form, SessionUser operator) throws BusinessException;

    /** @param itemId 物品编号 @param operator 操作人 @throws BusinessException 非本人或状态不允许 */
    void offline(int itemId, SessionUser operator) throws BusinessException;

    /** @param itemId 物品编号 @return 物品或空 */
    Optional<Item> detail(int itemId);

    /** @param query 检索条件 @return 分页结果 */
    PageResult<Item> search(ItemQuery query);

    /** @param itemId 物品编号 @param userId 收藏人 @throws BusinessException 物品不存在或参数不合法 */
    void favorite(int itemId, int userId) throws BusinessException;

    /** @param itemId 物品编号 @param userId 收藏人 @throws BusinessException 参数不合法 */
    void unfavorite(int itemId, int userId) throws BusinessException;
}
