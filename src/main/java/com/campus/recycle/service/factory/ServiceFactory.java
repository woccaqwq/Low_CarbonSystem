package com.campus.recycle.service.factory;

import com.campus.recycle.service.AdminService;
import com.campus.recycle.service.FlowService;
import com.campus.recycle.service.ItemService;
import com.campus.recycle.service.ReviewService;
import com.campus.recycle.service.StatService;
import com.campus.recycle.service.UserService;
import com.campus.recycle.service.impl.AdminServiceImpl;
import com.campus.recycle.service.impl.FlowServiceImpl;
import com.campus.recycle.service.impl.ItemServiceImpl;
import com.campus.recycle.service.impl.ReviewServiceImpl;
import com.campus.recycle.service.impl.StatServiceImpl;
import com.campus.recycle.service.impl.UserServiceImpl;

/** 按接口暴露默认 Service 单例，界面只依赖接口，测试可直接构造实现类注入依赖。 */
public final class ServiceFactory {
    private ServiceFactory() { }

    private static final class Services {
        private static final UserService USER = new UserServiceImpl();
        private static final ItemService ITEM = new ItemServiceImpl();
        private static final FlowService FLOW = new FlowServiceImpl();
        private static final ReviewService REVIEW = new ReviewServiceImpl();
        private static final StatService STAT = new StatServiceImpl();
        private static final AdminService ADMIN = new AdminServiceImpl();
    }

    /** @return 默认用户 Service 单例 */
    public static UserService getUserService() { return Services.USER; }
    /** @return 默认物品 Service 单例 */
    public static ItemService getItemService() { return Services.ITEM; }
    /** @return 默认流转 Service 单例 */
    public static FlowService getFlowService() { return Services.FLOW; }
    /** @return 默认评价 Service 单例 */
    public static ReviewService getReviewService() { return Services.REVIEW; }
    /** @return 默认统计 Service 单例 */
    public static StatService getStatService() { return Services.STAT; }
    /** @return 默认管理员 Service 单例 */
    public static AdminService getAdminService() { return Services.ADMIN; }
}
