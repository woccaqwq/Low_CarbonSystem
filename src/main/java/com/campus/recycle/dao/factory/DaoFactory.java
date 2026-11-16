package com.campus.recycle.dao.factory;

import com.campus.recycle.dao.UserDao;
import com.campus.recycle.dao.CategoryDao;
import com.campus.recycle.dao.ItemDao;
import com.campus.recycle.dao.FavoriteDao;
import com.campus.recycle.dao.FlowRecordDao;
import com.campus.recycle.dao.ReviewDao;
import com.campus.recycle.dao.StatDao;
import com.campus.recycle.dao.OperationLogDao;
import com.campus.recycle.dao.SysConfigDao;
import com.campus.recycle.dao.impl.UserDaoImpl;
import com.campus.recycle.dao.impl.CategoryDaoImpl;
import com.campus.recycle.dao.impl.ItemDaoImpl;
import com.campus.recycle.dao.impl.FavoriteDaoImpl;
import com.campus.recycle.dao.impl.FlowRecordDaoImpl;
import com.campus.recycle.dao.impl.ReviewDaoImpl;
import com.campus.recycle.dao.impl.StatDaoImpl;
import com.campus.recycle.dao.impl.OperationLogDaoImpl;
import com.campus.recycle.dao.impl.SysConfigDaoImpl;

/** 按接口暴露默认 DAO，测试可直接向实现类注入独立 DBUtil。 */
public final class DaoFactory {
    private DaoFactory() {
    }

    private static final class Users {
        private static final UserDao INSTANCE = new UserDaoImpl();
    }

    private static final class Categories {
        private static final CategoryDao INSTANCE = new CategoryDaoImpl();
    }

    private static final class Items {
        private static final ItemDao INSTANCE = new ItemDaoImpl();
    }

    private static final class Favorites {
        private static final FavoriteDao INSTANCE = new FavoriteDaoImpl();
    }

    private static final class Flows {
        private static final FlowRecordDao INSTANCE = new FlowRecordDaoImpl();
    }

    private static final class Reviews {
        private static final ReviewDao INSTANCE = new ReviewDaoImpl();
    }

    private static final class Statistics {
        private static final StatDao INSTANCE = new StatDaoImpl();
    }

    private static final class OperationLogs {
        private static final OperationLogDao INSTANCE = new OperationLogDaoImpl();
    }

    private static final class Configurations {
        private static final SysConfigDao INSTANCE = new SysConfigDaoImpl();
    }

    /** @return 默认用户 DAO 单例；工厂和 DAO 均不保存长连接 */
    public static UserDao getUserDao() {
        return Users.INSTANCE;
    }

    /** @return 默认分类 DAO，不持有长连接 */
    public static CategoryDao getCategoryDao() {
        return Categories.INSTANCE;
    }

    /** @return 默认物品 DAO，不持有长连接 */
    public static ItemDao getItemDao() {
        return Items.INSTANCE;
    }

    /** @return 默认收藏 DAO，不持有长连接 */
    public static FavoriteDao getFavoriteDao() {
        return Favorites.INSTANCE;
    }

    /** @return 默认流转 DAO，不持有长连接 */
    public static FlowRecordDao getFlowRecordDao() {
        return Flows.INSTANCE;
    }

    /** @return 默认评价 DAO，不持有长连接 */
    public static ReviewDao getReviewDao() {
        return Reviews.INSTANCE;
    }

    /** @return 默认统计 DAO，不持有长连接 */
    public static StatDao getStatDao() {
        return Statistics.INSTANCE;
    }

    /** @return 默认操作日志 DAO，不持有长连接 */
    public static OperationLogDao getOperationLogDao() {
        return OperationLogs.INSTANCE;
    }

    /** @return 默认只读配置 DAO，不持有长连接或配置缓存 */
    public static SysConfigDao getSysConfigDao() {
        return Configurations.INSTANCE;
    }
}
