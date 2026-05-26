package com.wumin.wuminpy.crash;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static final String CRASH_DIR = "crashes"; // 崩溃日志存放目录名
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        // 保存系统默认的异常处理器
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // 1. 记录崩溃日志（写入文件）
        saveCrashLog(ex);

        // 2. 可选：上传到服务器（这里仅示例，需自行实现网络请求）
        // uploadCrashReport(ex);

        // 3. 调用系统默认处理器（会弹出系统崩溃对话框并终止进程）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            // 如果没有默认处理器，则自行结束进程（避免应用挂起）
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    /**
     * 将崩溃信息保存到文件
     */
    private void saveCrashLog(Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String stackTrace = sw.toString();

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String time = sdf.format(new Date());

        // 收集设备信息
        sb.append("时间: ").append(time).append("\n");
        sb.append("设备: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Android版本: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("应用版本: ").append(getAppVersion()).append("\n");
        sb.append("异常信息: ").append(ex.toString()).append("\n");
        sb.append("堆栈: \n").append(stackTrace);

        String crashContent = sb.toString();

        // 写入文件
        File crashDir = new File(context.getFilesDir(), CRASH_DIR);
        if (!crashDir.exists()) {
            crashDir.mkdirs();
        }
        String fileName = "crash_" + System.currentTimeMillis() + ".log";
        File crashFile = new File(crashDir, fileName);

        try (FileWriter writer = new FileWriter(crashFile)) {
            writer.write(crashContent);
            Log.e(TAG, "崩溃日志已保存: " + crashFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "保存崩溃日志失败", e);
        }
    }

    /**
     * 获取应用版本号
     */
    private String getAppVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName + " (" + packageInfo.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "未知";
        }
    }
}