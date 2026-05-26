package com.wumin.service; // 请替换为您的包名

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.wumin.wuminpy.MainActivity; // 根据实际主界面修改
import com.wumin.wuminpy.R;

public class ForegroundService extends Service {
    private static final String TAG = "ForegroundService";
    private static final String CHANNEL_ID = "wuminpy_service";
    private static final int NOTIFICATION_ID = 3001;

    // 动作常量（用于控制服务）
    public static final String ACTION_START = "com.wumin.action.START_FOREGROUND";
    public static final String ACTION_STOP = "com.wumin.action.STOP_FOREGROUND";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createNotificationChannel();
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WuminPy 服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WuminPy 后台服务通知");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        Log.d(TAG, "收到命令: " + action);

        if (ACTION_START.equals(action)) {
            startForegroundService();
        } else if (ACTION_STOP.equals(action)) {
            stopForegroundService();
        }

        // 如果服务被系统杀死，我们希望它不要自动重启，所以返回 START_NOT_STICKY
        return START_NOT_STICKY;
    }

    /**
     * 启动前台服务
     */
    private void startForegroundService() {
        // 构建通知
        Notification notification = buildNotification();
        // 将服务转为前台服务
//        startForeground(NOTIFICATION_ID, notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        Log.d(TAG, "前台服务已启动");
    }

    /**
     * 停止前台服务
     */
    private void stopForegroundService() {
        // 停止前台并移除通知
        stopForeground(true);
        // 停止服务自身
        stopSelf();
        Log.d(TAG, "前台服务已停止");
    }

    /**
     * 构建通知
     */
    private Notification buildNotification() {
        // 点击通知打开主界面
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WuminPy")
                .setContentText("服务正在后台运行")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // 此服务不需要绑定
    }
}