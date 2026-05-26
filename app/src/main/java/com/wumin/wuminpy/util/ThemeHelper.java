package com.wumin.wuminpy.util;

import androidx.appcompat.app.AppCompatDelegate;

import com.wumin.core.AppConfig;

public class ThemeHelper {
    public static final int MODE_FOLLOW_SYSTEM = -1;  // 跟随系统
    public static final int MODE_LIGHT = 0;           // 日间模式
    public static final int MODE_DARK = 1;            // 夜间模式

    private static final String PREF_THEME_MODE = "theme_mode";

    public static void setThemeMode(int mode) {
//        AppConfig.INSTANCE.setThemeMode(mode); // 保存到 SharedPreferences
        applyThemeMode(mode);                  // 立即应用
    }

    public static int getThemeMode() {
        return AppConfig.INSTANCE.getThemeMode();
    }

    private static void applyThemeMode(int mode) {
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_FOLLOW_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}