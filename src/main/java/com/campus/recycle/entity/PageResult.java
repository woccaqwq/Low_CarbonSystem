package com.campus.recycle.entity;

import java.util.List;

/** 不可变的分页容器；不对其中的可变实体执行深拷贝。 */
public final class PageResult<T> {
    private final List<T> items;
    private final int total;
    private final int pageNo;
    private final int pageSize;

    /**
     * 创建分页快照。
     * @param items 本页记录，防御性复制
     * @param total 总条数，非负
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页 10–100 条
     * @throws IllegalArgumentException 分页元数据不合法
     */
    public PageResult(List<T> items, int total, int pageNo, int pageSize) {
        if (total < 0 || pageNo < 1 || pageSize < 10 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        this.items = List.copyOf(items);
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    /** @return 本页不可修改的列表 */
    public List<T> getItems() { return items; }
    /** @return 总记录数 */
    public int getTotal() { return total; }
    /** @return 请求页码 */
    public int getPageNo() { return pageNo; }
    /** @return 每页大小 */
    public int getPageSize() { return pageSize; }
    /** @return 总页数，空结果为 0，计算不会发生加法溢出 */
    public int getTotalPages() { return total == 0 ? 0 : 1 + (total - 1) / pageSize; }
    /** @return 本页是否无记录 */
    public boolean isEmpty() { return items.isEmpty(); }
}
