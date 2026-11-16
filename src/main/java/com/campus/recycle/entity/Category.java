package com.campus.recycle.entity;

/** 分类实体；完整层级规则与管理员权限由 Service 校验。 */
public final class Category {
    private int categoryId;
    private String name;
    private Integer parentId;
    private int sortNo;

    /** 创建待填充分类。 */
    public Category() { }

    /** @param name 非空名称 @param parentId 父分类，null 为根 @param sortNo 排序号 */
    public Category(String name, Integer parentId, int sortNo) {
        setName(name);
        setParentId(parentId);
        setSortNo(sortNo);
    }

    /** @return 分类编号，未保存为 0 */
    public int getCategoryId() { return categoryId; }
    /** @param categoryId 非负编号，不能与父级相同 */
    public void setCategoryId(int categoryId) {
        if (categoryId < 0 || (parentId != null && parentId == categoryId)) {
            throw new IllegalArgumentException("分类编号不合法");
        }
        this.categoryId = categoryId;
    }
    /** @return 分类名称 */
    public String getName() { return name; }
    /** @param name 非空分类名称，保留原始字符 */
    public void setName(String name) {
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("分类名称不能为空"); }
        this.name = name;
    }
    /** @return 父级编号，根分类为 null */
    public Integer getParentId() { return parentId; }
    /** @param parentId 正整数或 null，不允许自己作为父级 */
    public void setParentId(Integer parentId) {
        if (parentId != null && (parentId <= 0 || parentId == categoryId)) {
            throw new IllegalArgumentException("父分类不合法");
        }
        this.parentId = parentId;
    }
    /** @return 排序号 */
    public int getSortNo() { return sortNo; }
    /** @param sortNo 排序号，同号时按编号升序 */
    public void setSortNo(int sortNo) { this.sortNo = sortNo; }
    /** @return 是否为根分类 */
    public boolean isRoot() { return parentId == null; }
}
