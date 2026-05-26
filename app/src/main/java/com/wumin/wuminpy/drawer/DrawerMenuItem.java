package com.wumin.wuminpy.drawer;

import java.util.List;

public class DrawerMenuItem {
    public static final int TYPE_HEADER = 2;
    public static final int TYPE_PARENT = 0;
    public static final int TYPE_CHILD_NORMAL = 1;    // 普通子项（点击导航）
    public static final int TYPE_CHILD_SWITCH = 3;    // 开关子项

    public int type;
    public int id;
    public String title;
    public int iconRes;
    public List<DrawerMenuItem> children;
    public boolean isExpanded;      // 父项是否展开
    public boolean isChecked;       // 开关子项的当前状态

    public int parentId; // 子项记录父项ID，父项或头部为0
    // 头部专用构造（无参，需手动设置 type）
    public DrawerMenuItem() {
        this.type = TYPE_HEADER;
    }

    // 父项构造
    public DrawerMenuItem(int id, String title, int iconRes, List<DrawerMenuItem> children) {
        this.type = TYPE_PARENT;
        this.id = id;
        this.title = title;
        this.iconRes = iconRes;
        this.children = children;
        this.isExpanded = false;
    }

    // 子项构造（通过 isSwitch 区分类型）
    public DrawerMenuItem(int id, String title, int iconRes, boolean isSwitch) {
        this.type = isSwitch ? TYPE_CHILD_SWITCH : TYPE_CHILD_NORMAL;
        this.id = id;
        this.title = title;
        this.iconRes = iconRes;
        this.isChecked = false; // 默认关闭，可从 SharedPreferences 恢复
    }

    // 新增 setter，用于链式调用
    public DrawerMenuItem setParentId(int parentId) {
        this.parentId = parentId;
        return this;
    }
}