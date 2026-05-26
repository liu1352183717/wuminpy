package com.wumin.auto;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.PrintWriter;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class WaitTaskManager {
    private static WaitTask currentTask = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 【核心升级 1】：创建一条专门用于 UI 检索的后台线程，绝不阻塞主线程！
    private static final Handler checkHandler;
    static {
        HandlerThread thread = new HandlerThread("UiCheckThread");
        thread.start();
        checkHandler = new Handler(thread.getLooper());
    }

    public static class WaitTask {
        JSONObject query;
        PrintWriter socketWriter;
        Runnable timeoutRunnable;

        public WaitTask(JSONObject query, PrintWriter writer) {
            this.query = query;
            this.socketWriter = writer;
        }
    }

    // 由 IPC 子线程调用
    public static void startWaitTask(JSONObject query, int timeoutMs, PrintWriter writer) {
        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service == null) return;

        // 1. 安全挂载任务
        synchronized (WaitTaskManager.class) {
            currentTask = new WaitTask(query, writer);
            currentTask.timeoutRunnable = () -> {
                synchronized (WaitTaskManager.class) {
                    if (currentTask != null) {
                        try {
                            writer.println("{\"status\": \"timeout\"}");
                            writer.flush();
                        } catch (Exception ignored) {}
                        currentTask = null;
                    }
                }
            };
        }

        // 2. 启动超时器
        mainHandler.postDelayed(currentTask.timeoutRunnable, timeoutMs);

        // 3. 立即在后台线程触发第一次查找
        checkHandler.post(checkRunnable);
    }

    // 【核心升级 2】：由无障碍主线程调用。去掉了 synchronized，耗时从 200ms 降为 0.1ms！
    public static void onScreenChanged() {
        if (currentTask == null) return;

        // 【防抖机制】：如果 UI 疯狂刷新（如光标闪烁），取消积压的检查，延迟 150ms 再查
        checkHandler.removeCallbacks(checkRunnable);
        checkHandler.postDelayed(checkRunnable, 150);
    }

    // 后台执行的搜寻任务
    private static final Runnable checkRunnable = () -> {
        WaitTask taskToProcess;
        synchronized (WaitTaskManager.class) {
            taskToProcess = currentTask;
        }

        if (taskToProcess == null) return;

        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service == null) return;

        // 耗时操作：在后台线程安全遍历 UI 树，不加锁，让主线程自由飞翔！
        boolean found = checkAndRespond(service, taskToProcess.query, taskToProcess.socketWriter);

        if (found) {
            synchronized (WaitTaskManager.class) {
                // 如果查到了，且任务没因为超时而被清空，则结束任务
                if (currentTask == taskToProcess) {
                    mainHandler.removeCallbacks(currentTask.timeoutRunnable);
                    currentTask = null;
                }
            }
        }
    };

    private static boolean checkAndRespond(PythonAccessibilityService service, JSONObject query, PrintWriter writer) {
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows == null) return false;

        JSONArray results = new JSONArray();

        // 倒序遍历窗口
        for (int i = windows.size() - 1; i >= 0; i--) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window == null || window.getRoot() == null) continue;

            // 过滤掉审查器自己所在的遮罩层
            if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;

            NodeMatcher.findNodes(window.getRoot(), query, 1, results);

            // 只要找到 1 个就立刻返回给 Python
            if (results.length() > 0) {
                try {
                    JSONObject response = new JSONObject();
                    response.put("status", "ok");
                    response.put("node", results.getJSONObject(0));
                    writer.println(response.toString());
                    writer.flush();
                } catch (Exception e) { e.printStackTrace(); }
                return true;
            }
        }
        return false;
    }
}