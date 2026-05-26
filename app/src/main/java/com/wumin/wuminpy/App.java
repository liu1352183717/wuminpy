package com.wumin.wuminpy;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wumin.core.AppConfig;
import com.wumin.core.AppContext;
import com.wumin.logger.LogManager;
import com.wumin.wuminpy.crash.CrashHandler;
import com.wumin.wuminpy.util.ThemeHelper;

public class App extends Application {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // 设置全局崩溃捕获
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        // 注册导航服务
        AppConfig.init(this);
        AppContext.init(this);
        ThemeHelper.setThemeMode(ThemeHelper.getThemeMode());
        // 异步加载历史日志
        LogManager.getInstance().init(this);

        FeatureBootstrap.init(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            int activityCount = 0;

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {

            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {

            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

            }

            @Override
            public void onActivityStarted(android.app.Activity activity) {
                activityCount++;
            }

            @Override
            public void onActivityStopped(android.app.Activity activity) {
                activityCount--;
                if (activityCount == 0) {
                    // 应用进入后台，保存日志
//                    new Thread(() -> LogManager.getInstance().saveToFile(getApplicationContext())).start();
                }
            }
            // 其他回调留空
        });
    }

    public static App getInstance() {
        return instance;
    }

}
