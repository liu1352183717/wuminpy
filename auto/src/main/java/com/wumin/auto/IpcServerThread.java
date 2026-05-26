package com.wumin.auto;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;

import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

public class IpcServerThread extends Thread {
    private static final String SOCKET_NAME = "com.wumin.auto.ipc";
    private static IpcServerThread currentThread;

    private LocalServerSocket serverSocket;
    private volatile boolean isRunning = false;

    // === 安全的生命周期管理 ===
    public static synchronized void startServer() {
        if (currentThread != null) {
            currentThread.stopServerInternal();
        }
        currentThread = new IpcServerThread();
        currentThread.start();
    }

    public static synchronized void stopServer() {
        if (currentThread != null) {
            currentThread.stopServerInternal();
            currentThread = null;
        }
    }

    private void stopServerInternal() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
        }
        interrupt();
    }

    @Override
    public void run() {
        isRunning = true;
        int retryCount = 0;

        // 【核心修复】：5次退避重试算法，专治强杀后端口未释放的 Address already in use 异常！
        while (isRunning && retryCount < 5) {
            try {
                serverSocket = new LocalServerSocket(SOCKET_NAME);
                Log.i("IPC", "LocalSocket 服务端启动成功: " + SOCKET_NAME);
                break; // 绑定成功，跳出重试循环
            } catch (Exception e) {
                Log.w("IPC", "LocalSocket 端口可能被残留进程占用，正在重试 (" + (retryCount + 1) + "/5)...");
                retryCount++;
                try {
                    Thread.sleep(400); // 等待 400ms 让 Linux 内核回收死掉的 Socket
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }

        if (serverSocket == null) {
            Log.e("IPC", "LocalSocket 彻底绑定失败，IPC 通讯桥梁断裂！");
            return;
        }

        try {
            while (isRunning) {
                // 阻塞等待 Python 连接
                LocalSocket clientSocket = serverSocket.accept();
                if (!isRunning) break; // 如果刚刚被唤醒但要求停止，立刻退出

                Log.i("IPC", "Python 脚本已连接！");
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (Exception e) {
            if (isRunning) {
                Log.e("IPC", "LocalSocket 监听异常", e);
            }
        } finally {
            stopServerInternal(); // 线程退出前，确保资源被清理
        }
    }

    // ================= 原有业务逻辑完全保留 =================
    private static class ClientHandler implements Runnable {
        private final LocalSocket socket;

        public ClientHandler(LocalSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

                String line;
                while ((line = reader.readLine()) != null) {
                    JSONObject cmd = new JSONObject(line);
                    handleCommand(cmd, writer);
                }
            } catch (Exception e) {
                Log.e("IPC", "与 Python 断开连接", e);
            }
        }

        private void handleCommand(JSONObject cmd, PrintWriter writer) throws Exception {
            String action = cmd.optString("action");
            PythonAccessibilityService service = PythonAccessibilityService.getInstance();
            if (service == null) {
                writer.println("{\"status\": \"error\", \"msg\": \"无障碍服务未运行\"}");
                return;
            }

            if ("find_and_click".equals(action)) {
                String text = cmd.optString("text");
                boolean success = false;
                List<AccessibilityWindowInfo> windows = service.getWindows();
                if (windows != null) {
                    for (int i = windows.size() - 1; i >= 0; i--) {
                        AccessibilityNodeInfo root = windows.get(i).getRoot();
                        if (root != null) {
                            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                            if (!nodes.isEmpty()) {
                                success = nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                break;
                            }
                        }
                    }
                }
                writer.println("{\"status\": \"" + (success ? "ok" : "fail") + "\"}");

            } else if ("find_nodes".equals(action)) {
                JSONObject query = cmd.optJSONObject("query");
                int limit = cmd.optInt("limit", 0);
                JSONArray matchedNodes = new JSONArray();

                List<AccessibilityWindowInfo> windows = service.getWindows();
                if (windows != null) {
                    for (int i = windows.size() - 1; i >= 0; i--) {
                        AccessibilityWindowInfo window = windows.get(i);
                        if (window == null || window.getRoot() == null) continue;
                        if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;

                        NodeMatcher.findNodes(window.getRoot(), query, limit, matchedNodes);
                        if (limit > 0 && matchedNodes.length() >= limit) break;
                    }
                }

                JSONObject response = new JSONObject();
                response.put("status", "ok");
                response.put("nodes", matchedNodes);
                writer.println(response.toString());

            } else if ("wait_for_query".equals(action)) {
                JSONObject query = cmd.optJSONObject("query");
                int timeout = cmd.optInt("timeout", 5000);
                WaitTaskManager.startWaitTask(query, timeout, writer);

            } else if ("click_by_id".equals(action)) {
                String nodeId = cmd.optString("node_id");
                boolean success = NodeCacheManager.clickNode(nodeId);
                writer.println("{\"status\": \"" + (success ? "ok" : "fail") + "\"}");

            } else if ("dump_ai_context".equals(action)) {
                List<AccessibilityWindowInfo> windows = service.getWindows();
                JSONObject response = new JSONObject();
                response.put("status", "ok");
                response.put("actionable_nodes", UiTreeDumper.dumpAllWindows(windows));
                writer.println(response.toString());

            } else if ("gesture_click".equals(action)) {
                int x = cmd.optInt("x");
                int y = cmd.optInt("y");
                GestureManager.performClick(x, y, writer);

            } else if ("gesture_long_press".equals(action)) {
                int x = cmd.optInt("x");
                int y = cmd.optInt("y");
                int duration = cmd.optInt("duration", 1000);
                GestureManager.performLongPress(x, y, duration, writer);

            } else if ("gesture_swipe".equals(action)) {
                int x1 = cmd.optInt("x1");
                int y1 = cmd.optInt("y1");
                int x2 = cmd.optInt("x2");
                int y2 = cmd.optInt("y2");
                int duration = cmd.optInt("duration", 500);
                GestureManager.performSwipe(x1, y1, x2, y2, duration, writer);

            } else if ("gesture_custom".equals(action)) {
                JSONArray points = cmd.optJSONArray("points");
                int duration = cmd.optInt("duration", 1000);
                GestureManager.performCustomGesture(points, duration, writer);

            } else if ("gesture_advanced".equals(action)) {
                JSONArray strokes = cmd.optJSONArray("strokes");
                GestureManager.performAdvancedGesture(strokes, writer);

            } else if ("global_action".equals(action)) {
                int actionCode = cmd.optInt("code");
                boolean success = service.performGlobalAction(actionCode);
                writer.println("{\"status\": \"" + (success ? "ok" : "fail") + "\"}");

            } else if ("get_parent".equals(action)) {
                String nodeId = cmd.optString("node_id");
                AccessibilityNodeInfo node = NodeCacheManager.getNode(nodeId);
                JSONObject response = new JSONObject();
                response.put("status", "ok");

                if (node != null && node.getParent() != null) {
                    AccessibilityNodeInfo parent = node.getParent();
                    JSONObject parentJson = new JSONObject();
                    parentJson.put("id", NodeCacheManager.cacheNode(parent));
                    parentJson.put("text", parent.getText() != null ? parent.getText().toString() : "");
                    parentJson.put("desc", parent.getContentDescription() != null ? parent.getContentDescription().toString() : "");
                    parentJson.put("class", parent.getClassName() != null ? parent.getClassName().toString() : "");
                    response.put("node", parentJson);
                }
                writer.println(response.toString());

            } else if ("get_children".equals(action)) {
                String nodeId = cmd.optString("node_id");
                AccessibilityNodeInfo node = NodeCacheManager.getNode(nodeId);
                JSONObject response = new JSONObject();
                response.put("status", "ok");
                JSONArray childrenArray = new JSONArray();

                if (node != null) {
                    for (int i = 0; i < node.getChildCount(); i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            JSONObject childJson = new JSONObject();
                            childJson.put("id", NodeCacheManager.cacheNode(child));
                            childJson.put("text", child.getText() != null ? child.getText().toString() : "");
                            childJson.put("desc", child.getContentDescription() != null ? child.getContentDescription().toString() : "");
                            childJson.put("class", child.getClassName() != null ? child.getClassName().toString() : "");
                            childrenArray.put(childJson);
                        }
                    }
                }
                response.put("nodes", childrenArray);
                writer.println(response.toString());

            } else if ("get_device_size".equals(action)) {
                android.util.DisplayMetrics metrics = android.content.res.Resources.getSystem().getDisplayMetrics();
                JSONObject response = new JSONObject();
                response.put("status", "ok");
                response.put("width", metrics.widthPixels);
                response.put("height", metrics.heightPixels);
                writer.println(response.toString());

            } else if ("vibrate".equals(action)) {
                int duration = cmd.optInt("duration", 500);
                if (service != null) {
                    android.os.Vibrator vibrator = (android.os.Vibrator) service.getSystemService(android.content.Context.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(duration);
                        }
                    }
                }
                writer.println("{\"status\": \"ok\"}");

            } else if ("launch_app".equals(action)) {
                String packageName = cmd.optString("package_name");
                boolean ok = false;
                if (packageName != null && !packageName.isEmpty()) {
                    try {
                        android.content.Intent launchIntent = service.getApplicationContext().getPackageManager().getLaunchIntentForPackage(packageName);
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            service.getApplicationContext().startActivity(launchIntent);
                            ok = true;
                        }
                    } catch (Exception e) {
                        Log.e("IPC", "launch_app failed", e);
                    }
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("input_text".equals(action)) {
                String text = cmd.optString("text");
                boolean ok = false;
                if (text != null) {
                    List<AccessibilityWindowInfo> windows = service.getWindows();
                    if (windows != null) {
                        for (int i = windows.size() - 1; i >= 0 && !ok; i--) {
                            AccessibilityNodeInfo root = windows.get(i).getRoot();
                            if (root != null) {
                                AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                                if (focused != null) {
                                    android.os.Bundle args = new android.os.Bundle();
                                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                                    ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                                    focused.recycle();
                                }
                            }
                        }
                    }
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("set_text".equals(action)) {
                String nodeId = cmd.optString("node_id");
                String text = cmd.optString("text");
                boolean ok = false;
                if (nodeId != null && text != null) {
                    AccessibilityNodeInfo node = NodeCacheManager.getNode(nodeId);
                    if (node != null) {
                        android.os.Bundle args = new android.os.Bundle();
                        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                        ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    }
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("paste".equals(action)) {
                String nodeId = cmd.optString("node_id", "");
                boolean ok = false;
                AccessibilityNodeInfo target = null;
                if (!nodeId.isEmpty()) {
                    target = NodeCacheManager.getNode(nodeId);
                } else {
                    List<AccessibilityWindowInfo> windows = service.getWindows();
                    if (windows != null) {
                        for (int i = windows.size() - 1; i >= 0; i--) {
                            AccessibilityNodeInfo root = windows.get(i).getRoot();
                            if (root != null) {
                                target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                                if (target != null) break;
                            }
                        }
                    }
                }
                if (target != null) {
                    ok = target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("scroll_forward".equals(action)) {
                String nodeId = cmd.optString("node_id");
                boolean ok = false;
                if (nodeId != null) {
                    AccessibilityNodeInfo node = NodeCacheManager.getNode(nodeId);
                    if (node != null) ok = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("scroll_backward".equals(action)) {
                String nodeId = cmd.optString("node_id");
                boolean ok = false;
                if (nodeId != null) {
                    AccessibilityNodeInfo node = NodeCacheManager.getNode(nodeId);
                    if (node != null) ok = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("long_click_by_id".equals(action)) {
                String nodeId = cmd.optString("node_id");
                boolean ok = false;
                if (nodeId != null) {
                    AccessibilityNodeInfo node = NodeCacheManager.getNode(nodeId);
                    if (node != null) ok = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("current_package".equals(action)) {
                JSONObject response = new JSONObject();
                response.put("status", "ok");
                List<AccessibilityWindowInfo> windows = service.getWindows();
                if (windows != null) {
                    for (int i = windows.size() - 1; i >= 0; i--) {
                        AccessibilityNodeInfo root = windows.get(i).getRoot();
                        if (root != null && root.getPackageName() != null) {
                            response.put("package", root.getPackageName().toString());
                            if (windows.get(i).getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                                break;
                            }
                        }
                    }
                }
                writer.println(response.toString());

            } else if ("key_code".equals(action)) {
                int code = cmd.optInt("code", 0);
                boolean ok = false;
                if (code > 0) {
                    // Try performGlobalAction for supported keys
                    ok = service.performGlobalAction(code);
                    if (!ok) {
                        // Fallback: use input keyevent shell command
                        try {
                            Runtime.getRuntime().exec("input keyevent " + code);
                            ok = true;
                        } catch (Exception ignored) {}
                    }
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("wake_up".equals(action)) {
                try {
                    android.os.PowerManager pm = (android.os.PowerManager) service.getSystemService(android.content.Context.POWER_SERVICE);
                    if (pm != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                                    | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                "wumin_auto:wake_up");
                            wl.acquire(1000);
                            wl.release();
                        }
                    }
                    writer.println("{\"status\": \"ok\"}");
                } catch (Exception e) {
                    writer.println("{\"status\": \"fail\"}");
                }

            } else if ("keep_screen_on".equals(action)) {
                boolean on = cmd.optBoolean("on", true);
                final boolean keepOn = on;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        if (keepOn) {
                            service.getWindows(); // trigger anything to keep alive
                        }
                    } catch (Exception ignored) {}
                });
                writer.println("{\"status\": \"ok\"}");

            } else if ("toast".equals(action)) {
                String msg = cmd.optString("message", "");
                int duration = cmd.optInt("duration", 0); // 0=short, 1=long
                final String toastMsg = msg;
                final int toastDur = duration == 1 ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    android.widget.Toast.makeText(service.getApplicationContext(), toastMsg, toastDur).show();
                });
                writer.println("{\"status\": \"ok\"}");

            } else if ("take_screenshot".equals(action)) {
                String savePath = cmd.optString("save_path", "");
                boolean ok = false;
                if (!savePath.isEmpty()) {
                    try {
                        Process proc = Runtime.getRuntime().exec("screencap -p " + savePath);
                        proc.waitFor();
                        ok = new java.io.File(savePath).exists();
                        if (!ok) {
                            Process proc2 = Runtime.getRuntime().exec("/system/bin/screencap -p " + savePath);
                            proc2.waitFor();
                            ok = new java.io.File(savePath).exists();
                        }
                    } catch (Exception e) {
                        Log.e("IPC", "take_screenshot failed", e);
                    }
                }
                writer.println("{\"status\": \"" + (ok ? "ok" : "fail") + "\"}");

            } else if ("start_inspector".equals(action)) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    InspectorManager.start(null);
                });
                writer.println("{\"status\": \"ok\"}");

            } else if ("stop_inspector".equals(action)) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    InspectorManager.stop();
                });
                writer.println("{\"status\": \"ok\"}");

            } else if ("check_accessibility".equals(action)) {
                JSONObject response = new JSONObject();
                response.put("status", "ok");
                response.put("service_running", service != null);
                boolean enabled = false;
                if (service != null) {
                    try {
                        String enabledServices = android.provider.Settings.Secure.getString(
                                service.getContentResolver(),
                                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                        if (enabledServices != null && enabledServices.toLowerCase()
                                .contains("pythonaccessibilityservice")) {
                            enabled = true;
                        }
                    } catch (Exception e) {
                        response.put("check_error", e.getMessage());
                    }
                }
                response.put("enabled", enabled);
                writer.println(response.toString());

            } else if ("open_accessibility_settings".equals(action)) {
                if (service != null) {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        service.getApplicationContext().startActivity(intent);
                        writer.println("{\"status\": \"ok\"}");
                    } catch (Exception e) {
                        writer.println("{\"status\": \"fail\", \"msg\": \"" + e.getMessage() + "\"}");
                    }
                } else {
                    writer.println("{\"status\": \"fail\", \"msg\": \"无障碍服务未运行\"}");
                }
            }
        }
    }
}