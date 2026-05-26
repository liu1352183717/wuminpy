package com.wumin.wuminpy.drawer;

import com.wumin.wuminpy.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 抽屉菜单的数据工厂。
 * 从 MainActivity 抽离，纯静态数据构建。
 */
public class DrawerMenuConfig {

    // 父项 ID
    public static final int PARENT_PERMISSION = R.id.nav_permission_parent;
    public static final int PARENT_THEME      = R.id.nav_theme_parent;
    public static final int PARENT_DEVELOPER  = R.id.nav_developer_parent;

    // 权限子项
    public static final int ID_PERM_OVERLAY              = R.id.nav_permission_overlay;
    public static final int ID_PERM_STORAGE              = R.id.nav_permission_storage;
    public static final int ID_PERM_FOREGROUND           = R.id.nav_permission_foreground;
    public static final int ID_PERM_ACCESSIBILITY        = R.id.nav_permission_accessi;
    public static final int ID_PERM_BATTERY              = R.id.nav_battery_optimizations;
    public static final int ID_PERM_BACKGROUND_POPUP     = R.id.nav_backfore_permiss;

    // 主题子项
    public static final int ID_THEME_FOLLOW_SYSTEM       = R.id.nav_theme_follow_system;
    public static final int ID_THEME_NIGHT               = R.id.nav_theme_night;

    // 开发者子项
    public static final int ID_DEV_REMOTE                = R.id.nav_dev_remote;
    public static final int ID_DEV_USB                   = R.id.nav_dev_usb;
    public static final int ID_DEV_WAKELOCK              = R.id.nav_dev_wakelock;
    public static final int ID_DEV_TUTORIAL              = R.id.nav_dev_tutorial;

    // 独立项
    public static final int ID_FLOATING                  = R.id.nav_floting;
    public static final int ID_NOTIFICATION              = R.id.nav_notifi;
    public static final int ID_NDK_INSTALL               = R.id.nav_ndk_install;
    public static final int ID_GIT_CONFIG                = R.id.nav_git_config;
    public static final int ID_AI_CONFIG                 = R.id.nav_ai_config;
    public static final int ID_LSP_CONFIG                = R.id.nav_lsp_config;
    public static final int ID_MORE_SETTINGS             = R.id.nav_more_settings;
    public static final int ID_CHECK_UPDATE              = R.id.nav_check_update;
    public static final int ID_OPENSOURCE                = R.id.nav_opensource;
    public static final int ID_PRIVACY                   = R.id.nav_privacy;
    public static final int ID_SPONSOR                   = R.id.nav_sponsor;
    public static final int ID_EXIT                      = R.id.nav_exit;

    /**
     * 构建完整的抽屉菜单项列表。
     * @param expandedMap 从 SharedPreferences 恢复的展开状态，首次启动传 null
     */
    public static List<DrawerMenuItem> build(Map<Integer, Boolean> expandedMap) {
        List<DrawerMenuItem> items = new ArrayList<>();
        items.add(new DrawerMenuItem()); // header

        // ── 权限服务 ──
        List<DrawerMenuItem> permChildren = Arrays.asList(
                switchItem(ID_PERM_OVERLAY,            "悬浮窗权限",   R.drawable.ic_float_permiss_dark, PARENT_PERMISSION),
                switchItem(ID_PERM_STORAGE,            "存储权限",     R.drawable.ic_file_permiss_dark,  PARENT_PERMISSION),
                switchItem(ID_PERM_FOREGROUND,         "前台权限",     R.drawable.ic_noti_dark,          PARENT_PERMISSION),
                switchItem(ID_PERM_ACCESSIBILITY,      "无障碍权限",   R.drawable.ic_accessi_dark,       PARENT_PERMISSION),
                switchItem(ID_PERM_BATTERY,            "忽略电池优化", R.drawable.ic_battery_dark,        PARENT_PERMISSION),
                switchItem(ID_PERM_BACKGROUND_POPUP,   "后台弹出权限", R.drawable.ic_popup_dark,          PARENT_PERMISSION)
        );
        addParent(items, expandedMap, PARENT_PERMISSION, "权限服务", R.drawable.ic_permission_dark, permChildren);

        // ── 主题设置 ──
        List<DrawerMenuItem> themeChildren = Arrays.asList(
                switchItem(ID_THEME_FOLLOW_SYSTEM, "跟随系统", R.drawable.ic_follow_dark, PARENT_THEME),
                switchItem(ID_THEME_NIGHT,         "夜间模式", R.drawable.ic_night_dark,   PARENT_THEME)
        );
        addParent(items, expandedMap, PARENT_THEME, "主题设置", R.drawable.ic_theme_dark, themeChildren);

        // ── 开发者调试 ──
        List<DrawerMenuItem> devChildren = Arrays.asList(
                switchItem(ID_DEV_REMOTE,   "无线连接 (LAN)",  R.drawable.ic_wlan_dark,  PARENT_DEVELOPER),
                switchItem(ID_DEV_USB,      "电脑连接 (ADB)",  R.drawable.ic_usb_dark,   PARENT_DEVELOPER),
                switchItem(ID_DEV_WAKELOCK, "保持常亮",        R.drawable.ic_keep_dark,  PARENT_DEVELOPER),
                normalItem(ID_DEV_TUTORIAL, "连接教程",        R.drawable.ic_settings,   PARENT_DEVELOPER)
        );
        addParent(items, expandedMap, PARENT_DEVELOPER, "开发者调试", R.drawable.ic_debug_dark, devChildren);

        // ── 独立项 ──
        items.add(switchItem(ID_FLOATING,       "悬浮窗",   R.drawable.ic_float_dark,   PARENT_THEME));
        items.add(switchItem(ID_NOTIFICATION,   "前台通知", R.drawable.ic_notif_dark,   PARENT_THEME));
        items.add(normalItem(ID_NDK_INSTALL,    "ndk安装",  R.drawable.ic_ndk_dark,     0));
        items.add(normalItem(ID_GIT_CONFIG,     "git配置",  R.drawable.ic_git_dark,     0));
        items.add(normalItem(ID_AI_CONFIG,      "AI配置",   R.drawable.ic_aiconfig_dark, 0));
        items.add(normalItem(ID_LSP_CONFIG,     "LSP配置",  R.drawable.lsp_auto,         0));
        items.add(normalItem(ID_MORE_SETTINGS,  "更多设置", R.drawable.ic_moreset_dark,  0));
        items.add(normalItem(ID_CHECK_UPDATE,   "检查更新", R.drawable.ic_update_dark,   0));

        // ── 关于 ──
        items.add(normalItem(ID_OPENSOURCE,     "开源声明",   R.drawable.ic_opensource_dark, 0));
        items.add(normalItem(ID_PRIVACY,        "隐私政策",   R.drawable.ic_privacy_dark,    0));
        items.add(normalItem(ID_SPONSOR,        "赞助支持",   R.drawable.ic_sponsor_dark,    0));

        return items;
    }

    /** 获取用于展开状态持久化的 parentId 列表 */
    public static int[] getParentIds() {
        return new int[]{ PARENT_PERMISSION, PARENT_THEME, PARENT_DEVELOPER };
    }

    // ── 工厂方法 ──

    private static DrawerMenuItem switchItem(int id, String title, int icon, int parentId) {
        return new DrawerMenuItem(id, title, icon, true).setParentId(parentId);
    }

    private static DrawerMenuItem normalItem(int id, String title, int icon, int parentId) {
        return new DrawerMenuItem(id, title, icon, false).setParentId(parentId);
    }

    private static void addParent(List<DrawerMenuItem> items, Map<Integer, Boolean> expandedMap,
                                   int id, String title, int icon, List<DrawerMenuItem> children) {
        boolean expanded = expandedMap != null && Boolean.TRUE.equals(expandedMap.getOrDefault(id, false));
        DrawerMenuItem parent = new DrawerMenuItem(id, title, icon, children);
        parent.isExpanded = expanded;
        items.add(parent);
        if (expanded) items.addAll(children);
    }
}
