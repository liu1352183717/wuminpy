package com.wumin.wuminpy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;
import com.hjq.permissions.permission.base.IPermission;
import com.wumin.auto.PythonAccessibilityService;
import com.wumin.core.AppConfig;
import com.wumin.core.ServiceRegistry;
import com.wumin.core.util.BackgroundPermissionHelper;
import com.wumin.core.util.NetUtil;
import com.wumin.debug.VSCodeDebugServer;
import com.wumin.service.ForegroundService;
import com.wumin.wuminpy.drawer.DrawerAdapter;
import com.wumin.wuminpy.drawer.DrawerMenuConfig;
import com.wumin.wuminpy.drawer.DrawerMenuItem;
import com.wumin.wuminpy.util.ThemeHelper;

import java.util.Arrays;
import java.util.List;

/**
 * 处理抽屉菜单中所有开关和权限相关的逻辑。
 * 从 MainActivity 抽离以保持其精简。
 */
public class DrawerSwitchHandler {

    private final MainActivity activity;
    private final DrawerAdapter adapter;
    private final List<DrawerMenuItem> menuItems;

    public DrawerSwitchHandler(MainActivity activity, DrawerAdapter adapter, List<DrawerMenuItem> menuItems) {
        this.activity = activity;
        this.adapter = adapter;
        this.menuItems = menuItems;
    }

    // ═══════════════════════════════════════════════════════
    // 抽屉开关分发
    // ═══════════════════════════════════════════════════════

    public void onSwitchChanged(DrawerMenuItem item, boolean isChecked) {
        int id = item.id;

        if (id == DrawerMenuConfig.ID_PERM_OVERLAY) {
            requestPermission(item, isChecked, Arrays.asList(PermissionLists.getSystemAlertWindowPermission()));
        } else if (id == DrawerMenuConfig.ID_PERM_STORAGE) {
            requestPermission(item, isChecked, Arrays.asList(
                    PermissionLists.getWriteExternalStoragePermission(),
                    PermissionLists.getReadExternalStoragePermission()));
        } else if (id == DrawerMenuConfig.ID_PERM_ACCESSIBILITY) {
            List<IPermission> accPerms = Arrays.asList(
                    PermissionLists.getBindAccessibilityServicePermission(PythonAccessibilityService.class));
            if (isChecked) {
                XXPermissions.with(activity).permissions(accPerms).request((grantedList, deniedList) -> {
                    if (deniedList.isEmpty()) {
                        Toast.makeText(activity, "无障碍服务已开启", Toast.LENGTH_SHORT).show();
                        saveSwitchState(item, true);
                    } else {
                        rollbackSwitch(item);
                    }
                });
            } else {
                if (XXPermissions.isGrantedPermissions(activity, accPerms)) {
                    Toast.makeText(activity, "请在无障碍设置中关闭服务", Toast.LENGTH_LONG).show();
                    activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    rollbackSwitch(item);
                } else {
                    saveSwitchState(item, false);
                }
            }
        } else if (id == DrawerMenuConfig.ID_PERM_FOREGROUND) {
            requestPermission(item, isChecked, Arrays.asList(PermissionLists.getNotificationServicePermission()));
        } else if (id == DrawerMenuConfig.ID_PERM_BATTERY) {
            requestPermission(item, isChecked, Arrays.asList(PermissionLists.getRequestIgnoreBatteryOptimizationsPermission()));
        } else if (id == DrawerMenuConfig.ID_FLOATING) {
            handleFloatingSwitch(item, isChecked);
        } else if (id == DrawerMenuConfig.ID_NOTIFICATION) {
            handleNotificationSwitch(item, isChecked);
        } else if (id == DrawerMenuConfig.ID_THEME_FOLLOW_SYSTEM) {
            applyTheme(isChecked ? ThemeHelper.MODE_FOLLOW_SYSTEM : ThemeHelper.MODE_LIGHT);
        } else if (id == DrawerMenuConfig.ID_THEME_NIGHT) {
            applyTheme(isChecked ? ThemeHelper.MODE_DARK : ThemeHelper.MODE_LIGHT);
        } else if (id == DrawerMenuConfig.ID_DEV_REMOTE) {
            handleDevRemote(isChecked);
        } else if (id == DrawerMenuConfig.ID_DEV_USB) {
            handleDevUsb(isChecked);
        } else if (id == DrawerMenuConfig.ID_PERM_BACKGROUND_POPUP) {
            XXPermissions.startPermissionActivity(activity);
            saveSwitchState(item, isChecked);
        } else if (id == DrawerMenuConfig.ID_DEV_WAKELOCK) {
            activity.setKeepScreenOn(isChecked);
            saveSwitchState(item, isChecked);
            Toast.makeText(activity, isChecked ? "屏幕将保持常亮" : "已取消屏幕常亮", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════
    // 开关状态持久化
    // ═══════════════════════════════════════════════════════

    public void saveSwitchState(DrawerMenuItem item, boolean isChecked) {
        int id = item.id;
        if (id == DrawerMenuConfig.ID_PERM_STORAGE)            AppConfig.INSTANCE.setPermissionStorage(isChecked);
        else if (id == DrawerMenuConfig.ID_PERM_OVERLAY)       AppConfig.INSTANCE.setPermissionOverlay(isChecked);
        else if (id == DrawerMenuConfig.ID_PERM_ACCESSIBILITY) AppConfig.INSTANCE.setPermissionAccessi(isChecked);
        else if (id == DrawerMenuConfig.ID_PERM_FOREGROUND)    AppConfig.INSTANCE.setPermissionForeground(isChecked);
        else if (id == DrawerMenuConfig.ID_FLOATING)           AppConfig.INSTANCE.setFlotEnabled(isChecked);
        else if (id == DrawerMenuConfig.ID_NOTIFICATION)       AppConfig.INSTANCE.setNotifiEnabled(isChecked);
        else if (id == DrawerMenuConfig.ID_THEME_FOLLOW_SYSTEM)
            AppConfig.INSTANCE.setThemeMode(isChecked ? ThemeHelper.MODE_FOLLOW_SYSTEM : ThemeHelper.MODE_LIGHT);
        else if (id == DrawerMenuConfig.ID_THEME_NIGHT)
            AppConfig.INSTANCE.setThemeMode(isChecked ? ThemeHelper.MODE_DARK : ThemeHelper.MODE_LIGHT);
        else if (id == DrawerMenuConfig.ID_PERM_BATTERY)       AppConfig.INSTANCE.setBatteryOptimizations(isChecked);
        else if (id == DrawerMenuConfig.ID_PERM_BACKGROUND_POPUP)
            AppConfig.INSTANCE.setBackgroundPopup(isChecked);
    }

    public void restoreSwitchState(List<DrawerMenuItem> items, boolean isColdStart) {
        for (DrawerMenuItem item : items) {
            if (item.type != DrawerMenuItem.TYPE_CHILD_SWITCH) {
                if (item.children != null && !item.children.isEmpty()) {
                    restoreSwitchState(item.children, isColdStart);
                }
                continue;
            }
            boolean saved = loadSwitchConfig(item.id);
            item.isChecked = saved;

            if (isColdStart) {
                if (item.id == DrawerMenuConfig.ID_FLOATING && saved) activity.showFloat();
                if (item.id == DrawerMenuConfig.ID_NOTIFICATION && saved) {
                    Intent intent = new Intent(activity, ForegroundService.class);
                    intent.setAction(ForegroundService.ACTION_START);
                    activity.startService(intent);
                }
            }
        }
    }

    public void refreshSwitchStates() {
        refreshRecursive(menuItems);
    }

    // ═══════════════════════════════════════════════════════
    // UI 辅助
    // ═══════════════════════════════════════════════════════

    /** 互斥开关弹回——修改另一个开关的 UI 状态 */
    public void setSwitchUIState(int targetId, boolean isChecked) {
        for (DrawerMenuItem parent : menuItems) {
            if (parent.children == null) continue;
            for (int i = 0; i < parent.children.size(); i++) {
                DrawerMenuItem child = parent.children.get(i);
                if (child.id == targetId && child.isChecked != isChecked) {
                    child.isChecked = isChecked;
                    adapter.notifyDataSetChanged();
                    return;
                }
            }
        }
    }

    public void rollbackSwitch(DrawerMenuItem item) {
        item.isChecked = false;
        int index = menuItems.indexOf(item);
        if (index != -1) adapter.notifyItemChanged(index);
        boolean actual = checkPermissionState(item.id);
        saveSwitchState(item, actual);
    }

    // ═══════════════════════════════════════════════════════
    // 内部实现
    // ═══════════════════════════════════════════════════════

    private void requestPermission(DrawerMenuItem item, boolean isChecked, List<IPermission> list) {
        if (isChecked) {
            XXPermissions.with(activity).permissions(list).request((grantedList, deniedList) -> {
                boolean allGranted = deniedList.isEmpty();
                if (allGranted) {
                    Toast.makeText(activity, "权限获取成功", Toast.LENGTH_SHORT).show();
                    saveSwitchState(item, true);
                } else {
                    XXPermissions.startPermissionActivity(activity);
                    rollbackSwitch(item);
                }
            });
        } else {
            if (XXPermissions.isGrantedPermissions(activity, list)) {
                Toast.makeText(activity, "请在系统设置中手动关闭权限", Toast.LENGTH_LONG).show();
                XXPermissions.startPermissionActivity(activity);
                rollbackSwitch(item);
            } else {
                saveSwitchState(item, false);
            }
        }
    }

    private boolean loadSwitchConfig(int itemId) {
        if (itemId == DrawerMenuConfig.ID_PERM_STORAGE)              return AppConfig.INSTANCE.getPermissionStorage();
        if (itemId == DrawerMenuConfig.ID_PERM_OVERLAY)              return AppConfig.INSTANCE.getPermissionOverlay();
        if (itemId == DrawerMenuConfig.ID_PERM_ACCESSIBILITY)        return AppConfig.INSTANCE.getPermissionAccessi();
        if (itemId == DrawerMenuConfig.ID_PERM_FOREGROUND)           return AppConfig.INSTANCE.getPermissionForeground();
        if (itemId == DrawerMenuConfig.ID_FLOATING)                  return AppConfig.INSTANCE.getFlotEnabled();
        if (itemId == DrawerMenuConfig.ID_NOTIFICATION)              return AppConfig.INSTANCE.getNotifiEnabled();
        if (itemId == DrawerMenuConfig.ID_THEME_FOLLOW_SYSTEM)       return AppConfig.INSTANCE.getThemeMode() == ThemeHelper.MODE_FOLLOW_SYSTEM;
        if (itemId == DrawerMenuConfig.ID_THEME_NIGHT)               return AppConfig.INSTANCE.getThemeMode() == ThemeHelper.MODE_DARK;
        if (itemId == DrawerMenuConfig.ID_PERM_BATTERY)              return AppConfig.INSTANCE.getBatteryOptimizations();
        if (itemId == DrawerMenuConfig.ID_PERM_BACKGROUND_POPUP)     return AppConfig.INSTANCE.getBackgroundPopup();
        return false;
    }

    private boolean checkPermissionState(int itemId) {
        if (itemId == DrawerMenuConfig.ID_PERM_STORAGE)
            return XXPermissions.isGrantedPermission(activity, PermissionLists.getWriteExternalStoragePermission());
        if (itemId == DrawerMenuConfig.ID_PERM_OVERLAY)
            return XXPermissions.isGrantedPermission(activity, PermissionLists.getSystemAlertWindowPermission());
        if (itemId == DrawerMenuConfig.ID_PERM_FOREGROUND)
            return XXPermissions.isGrantedPermission(activity, PermissionLists.getNotificationServicePermission());
        if (itemId == DrawerMenuConfig.ID_PERM_BATTERY)
            return XXPermissions.isGrantedPermission(activity, PermissionLists.getRequestIgnoreBatteryOptimizationsPermission());
        if (itemId == DrawerMenuConfig.ID_PERM_BACKGROUND_POPUP)
            return BackgroundPermissionHelper.isAllowed(activity);
        if (itemId == DrawerMenuConfig.ID_PERM_ACCESSIBILITY)
            return XXPermissions.isGrantedPermission(activity, PermissionLists.getBindAccessibilityServicePermission(PythonAccessibilityService.class));
        return false;
    }

    private void refreshRecursive(List<DrawerMenuItem> items) {
        for (DrawerMenuItem item : items) {
            if (item.type == DrawerMenuItem.TYPE_CHILD_SWITCH && isPermissionItem(item.id)) {
                boolean actual = checkPermissionState(item.id);
                if (item.isChecked != actual) {
                    item.isChecked = actual;
                    saveSwitchState(item, actual);
                    int index = menuItems.indexOf(item);
                    if (index != -1) adapter.notifyItemChanged(index);
                }
            }
            if (item.children != null && !item.children.isEmpty()) {
                refreshRecursive(item.children);
            }
        }
    }

    private static boolean isPermissionItem(int id) {
        return id == DrawerMenuConfig.ID_PERM_STORAGE
                || id == DrawerMenuConfig.ID_PERM_OVERLAY
                || id == DrawerMenuConfig.ID_PERM_FOREGROUND
                || id == DrawerMenuConfig.ID_PERM_BATTERY
                || id == DrawerMenuConfig.ID_PERM_BACKGROUND_POPUP
                || id == DrawerMenuConfig.ID_PERM_ACCESSIBILITY;
    }

    // ═══════════════════════════════════════════════════════
    // 各开关的具体处理
    // ═══════════════════════════════════════════════════════

    private void handleFloatingSwitch(DrawerMenuItem item, boolean isChecked) {
        if (isChecked) {
            if (Settings.canDrawOverlays(activity)) {
                activity.showFloat();
                saveSwitchState(item, true);
            } else {
                rollbackSwitch(item);
                Toast.makeText(activity, "请打开悬浮窗权限", Toast.LENGTH_LONG).show();
            }
        } else {
            activity.hideFloat();
            saveSwitchState(item, false);
        }
    }

    private void handleNotificationSwitch(DrawerMenuItem item, boolean isChecked) {
        if (isChecked) {
            if (XXPermissions.isGrantedPermission(activity, PermissionLists.getNotificationServicePermission())) {
                Intent intent = new Intent(activity, ForegroundService.class);
                intent.setAction(ForegroundService.ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.startForegroundService(intent);
                } else {
                    activity.startService(intent);
                }
                saveSwitchState(item, true);
            } else {
                rollbackSwitch(item);
                Toast.makeText(activity, "请打开通知权限", Toast.LENGTH_SHORT).show();
            }
        } else {
            Intent intent = new Intent(activity, ForegroundService.class);
            intent.setAction(ForegroundService.ACTION_STOP);
            activity.startService(intent);
            saveSwitchState(item, false);
        }
    }

    private void applyTheme(int mode) {
        AppConfig.INSTANCE.setThemeMode(mode);
        ThemeHelper.setThemeMode(mode);
        activity.recreate();
    }

    private void handleDevRemote(boolean isChecked) {
        if (isChecked) {
            showLanConfigDialog();
        } else {
            VSCodeDebugServer server = VSCodeDebugServer.getInstance();
            if (server.isRunning() && server.getCurrentMode() == VSCodeDebugServer.DebugMode.LAN) {
                server.stop();
                Toast.makeText(activity, "无线调试已关闭", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleDevUsb(boolean isChecked) {
        if (isChecked) {
            VSCodeDebugServer.getInstance().start(VSCodeDebugServer.DebugMode.ADB, 8080);
            Toast.makeText(activity, "USB 调试模式已开启 (8080)", Toast.LENGTH_SHORT).show();
            setSwitchUIState(DrawerMenuConfig.ID_DEV_REMOTE, false);
        } else {
            VSCodeDebugServer server = VSCodeDebugServer.getInstance();
            if (server.isRunning() && server.getCurrentMode() == VSCodeDebugServer.DebugMode.ADB) {
                server.stop();
                Toast.makeText(activity, "USB 调试已关闭", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLanConfigDialog() {
        String ip = NetUtil.getLocalIpAddress(activity);
        if (ip == null) {
            Toast.makeText(activity, "请先连接 Wi-Fi！", Toast.LENGTH_SHORT).show();
            setSwitchUIState(DrawerMenuConfig.ID_DEV_REMOTE, false);
            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences("WuminPyConfig", android.content.Context.MODE_PRIVATE);
        int savedPort = prefs.getInt("lan_port", 8080);

        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(savedPort));

        new AlertDialog.Builder(activity)
                .setTitle("配置无线调试")
                .setMessage("本机 IP: " + ip + "\n请输入监听端口：")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("启动", (d, w) -> {
                    String portStr = input.getText().toString();
                    if (portStr.isEmpty()) portStr = "8080";
                    int port = Integer.parseInt(portStr);
                    prefs.edit().putInt("lan_port", port).apply();
                    VSCodeDebugServer.getInstance().start(VSCodeDebugServer.DebugMode.LAN, port);
                    Toast.makeText(activity, "无线调试已启动\n" + ip + ":" + port, Toast.LENGTH_LONG).show();
                    setSwitchUIState(DrawerMenuConfig.ID_DEV_USB, false);
                })
                .setNegativeButton("取消", (d, w) -> setSwitchUIState(DrawerMenuConfig.ID_DEV_REMOTE, false))
                .show();
    }
}
