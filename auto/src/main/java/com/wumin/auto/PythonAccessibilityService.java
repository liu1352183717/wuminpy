package com.wumin.auto;

import android.content.Intent;
import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;

public class PythonAccessibilityService extends AccessibilityService {
    private static final String TAG = "PythonAutoService";
    private static PythonAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "无障碍服务已连接！正在启动本地 IPC 服务器...");

        // 【关键修复】：调用安全启动，这会先清理旧线程，再起新线程
        IpcServerThread.startServer();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            NodeCacheManager.clearCache();
        }

        WaitTaskManager.onScreenChanged();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (InspectorManager.isInspecting()) {
                    InspectorManager.stop();
                    return true;
                }
            }
        }
        return super.onKeyEvent(event);
    }

    // ================== 【新增生命周期管理】 ==================

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "无障碍服务被系统解绑（可能被强杀或手动关闭）");
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "无障碍服务进程被销毁");
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        instance = null;
        NodeCacheManager.clearCache();
        InspectorManager.stop(); // 强制关闭可能残留的审查器
        IpcServerThread.stopServer(); // 强制断开并释放底层的 Socket 端口！
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "无障碍服务被系统中断！");
    }

    public static PythonAccessibilityService getInstance() {
        return instance;
    }
}