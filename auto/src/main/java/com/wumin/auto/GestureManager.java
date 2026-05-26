package com.wumin.auto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.PrintWriter;

public class GestureManager {

    // 0. 全局返回键
    public static void performBack() {
        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service == null) return;
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }

    // 1. 坐标点击 (极短的滑动)
    public static void performClick(int x, int y, PrintWriter writer) {
        // 模拟手指按下抬起，50毫秒
        performSwipe(x, y, x, y, 50, writer);
    }

    // 2. 长按 (坐标不动，按住的时间变长)
    public static void performLongPress(int x, int y, int durationMs, PrintWriter writer) {
        // 长按时间强制保障至少 500ms
        int duration = Math.max(durationMs, 500);
        performSwipe(x, y, x, y, duration, writer);
    }

    // 3. 两点滑动
    public static void performSwipe(int startX, int startY, int endX, int endY, int durationMs, PrintWriter writer) {
        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service == null) {
            sendResponse(writer, "error");
            return;
        }

        Path path = new Path();
        path.moveTo(startX, startY);
        if (startX != endX || startY != endY) {
            path.lineTo(endX, endY);
        }

        dispatchPathGesture(service, path, durationMs, writer);
    }

    // 4. 多点连续手势 (例如画图形、手势密码)
    public static void performCustomGesture(JSONArray pointsArray, int durationMs, PrintWriter writer) {
        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service == null || pointsArray == null || pointsArray.length() == 0) {
            sendResponse(writer, "error");
            return;
        }

        Path path = new Path();
        try {
            // 移动到起点
            JSONObject firstPoint = pointsArray.getJSONObject(0);
            path.moveTo(firstPoint.getInt("x"), firstPoint.getInt("y"));
            // 依次连线
            for (int i = 1; i < pointsArray.length(); i++) {
                JSONObject pt = pointsArray.getJSONObject(i);
                path.lineTo(pt.getInt("x"), pt.getInt("y"));
            }
        } catch (Exception e) {
            sendResponse(writer, "error_parsing_points");
            return;
        }

        dispatchPathGesture(service, path, durationMs, writer);
    }

    // --- 核心分发逻辑 ---
    private static void dispatchPathGesture(AccessibilityService service, Path path, int durationMs, PrintWriter writer) {
        // 参数: 路径, 延迟多久开始(0), 整个手势的耗时(durationMs)
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, durationMs);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        // 必须在主线程派发手势
        new Handler(Looper.getMainLooper()).post(() -> {
            boolean dispatched = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    sendResponse(writer, "ok"); // 执行完毕，唤醒 Python
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    sendResponse(writer, "cancelled");
                }
            }, null);

            if (!dispatched) {
                sendResponse(writer, "fail");
            }
        });
    }

    // 终极高级手势：支持多指并发、每根手指独立时间线
    public static void performAdvancedGesture(JSONArray strokesArray, PrintWriter writer) {
        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service == null || strokesArray == null || strokesArray.length() == 0) {
            sendResponse(writer, "error");
            return;
        }

        GestureDescription.Builder builder = new GestureDescription.Builder();

        try {
            // 遍历每一根手指（或者每一段独立的笔画）
            for (int i = 0; i < strokesArray.length(); i++) {
                JSONObject strokeObj = strokesArray.getJSONObject(i);

                int startTime = strokeObj.optInt("start_time", 0); // 延迟多久按下
                int duration = strokeObj.getInt("duration");       // 这根手指滑动耗时
                JSONArray points = strokeObj.getJSONArray("path"); // 这根手指的路径

                if (points.length() == 0) continue;

                Path path = new Path();
                JSONObject firstPoint = points.getJSONObject(0);
                path.moveTo(firstPoint.getInt("x"), firstPoint.getInt("y"));

                for (int j = 1; j < points.length(); j++) {
                    JSONObject pt = points.getJSONObject(j);
                    path.lineTo(pt.getInt("x"), pt.getInt("y"));
                }

                // 创建这根手指的 Stroke，并加入 Builder
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(path, startTime, duration);
                builder.addStroke(stroke);
            }
        } catch (Exception e) {
            sendResponse(writer, "error_parsing_advanced_gesture");
            return;
        }

        GestureDescription gesture = builder.build();

        // 依然是在主线程派发
        new Handler(Looper.getMainLooper()).post(() -> {
            boolean dispatched = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    sendResponse(writer, "ok");
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    sendResponse(writer, "cancelled");
                }
            }, null);

            if (!dispatched) sendResponse(writer, "fail");
        });
    }

    private static void sendResponse(PrintWriter writer, String status) {
        if (writer == null) return;
        try {
            writer.println("{\"status\": \"" + status + "\"}");
            writer.flush();
        } catch (Exception ignored) {}
    }
}