package com.wumin.wuminpy;

import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.wumin.auto.PythonAccessibilityService;
import com.wumin.auto.UiTreeDumper;
import com.wumin.debug.VSCodeDebugServer;
import com.wumin.merminal.TerminalManager;
import com.wumin.merminal.TerminalTcpBridge;
import com.wumin.screen.ScreenCaptureService;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;

/**
 * 处理 VS Code 通过网络/ADB 发来的所有指令。
 * 从 MainActivity 抽离以保持其精简。
 */
public class VSCodeBridge {

    private static final String TAG = "VSCodeBridge";
    private final MainActivity activity;

    public VSCodeBridge(MainActivity activity) {
        this.activity = activity;
    }

    public void setup() {
        VSCodeDebugServer.getInstance().setCommandListener(new VSCodeDebugServer.CommandListener() {
            @Override
            public void onRunScript(String scriptName) {
                Intent intent = new Intent("com.wumin.action.RUN_SCRIPT");
                intent.putExtra("script_name", scriptName);
                intent.putExtra("is_debug", false);
                activity.sendBroadcast(intent);
                Log.i(TAG, "网络指令拉起脚本: " + scriptName);
            }

            @Override
            public void onPushFile(String relativePath, String base64Content) {
                try {
                    byte[] fileData = Base64.decode(base64Content, Base64.DEFAULT);
                    File remoteDir = new File("/sdcard/pyscripts/");
                    if (!remoteDir.exists()) remoteDir.mkdirs();

                    File targetFile = new File(remoteDir, relativePath);
                    targetFile.getParentFile().mkdirs();

                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(fileData);
                    }
                    Log.i(TAG, "文件网络同步成功: " + relativePath);
                } catch (Exception e) {
                    Log.e(TAG, "文件网络同步失败", e);
                }
            }

            @Override
            public void onRequestScreen() {
                activity.runOnUiThread(() -> {
                    new androidx.appcompat.app.AlertDialog.Builder(activity)
                            .setTitle("投屏请求")
                            .setMessage("VS Code 请求查看和控制您的屏幕，是否允许？")
                            .setPositiveButton("允许", (dialog, which) -> activity.startScreenCapture())
                            .setNegativeButton("拒绝", (dialog, which) -> {
                                android.widget.Toast.makeText(activity, "已拒绝投屏", android.widget.Toast.LENGTH_SHORT).show();
                                VSCodeDebugServer.getInstance().sendCommandToVSCode("screen_rejected");
                            })
                            .setCancelable(false)
                            .show();
                });
            }

            @Override
            public void onStopScreen() {
                Intent serviceIntent = new Intent(activity, ScreenCaptureService.class);
                activity.stopService(serviceIntent);
                Log.i(TAG, "已接收到关闭指令，彻底停止后台录屏服务");
            }

            @Override
            public void onDebugScript(String scriptName) {
                Intent intent = new Intent("com.wumin.action.RUN_SCRIPT");
                intent.putExtra("script_name", scriptName);
                intent.putExtra("is_debug", true);
                activity.sendBroadcast(intent);
                Log.i(TAG, "网络指令拉起断点调试: " + scriptName);

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    VSCodeDebugServer.getInstance().sendCommandToVSCode("debug_ready");
                }, 1000);
            }

            @Override
            public void onStartTerminal() {
                activity.runOnUiThread(() -> {
                    Log.i(TAG, "收到 VS Code 请求：启动底层终端服务");
                    TerminalManager.getInstance().init(activity, ready -> {
                        if (ready) {
                            Log.i(TAG, "终端服务启动成功，通知 VS Code");
                            VSCodeDebugServer.getInstance().sendCommandToVSCode("terminal_ready");
                        } else {
                            Log.e(TAG, "终端服务启动失败");
                        }
                    });
                });
            }

            @Override
            public void onStopTerminal() {
                activity.runOnUiThread(() -> {
                    Log.i(TAG, "收到 VS Code 请求：关闭终端服务释放资源");
                    try {
                        TerminalTcpBridge.INSTANCE.stopServer();
                        TerminalManager.getInstance().release();
                        Intent serviceIntent = new Intent(activity, com.wumin.merminal.TerminalService.class);
                        activity.stopService(serviceIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "关闭终端服务时发生异常", e);
                    }
                });
            }

            @Override
            public void onGestureClick(int x, int y) {
                Log.i(TAG, "无障碍手势点击: (" + x + ", " + y + ")");
                Executors.newSingleThreadExecutor().execute(() -> {
                    com.wumin.auto.GestureManager.performClick(x, y, null);
                });
            }

            @Override
            public void onGestureSwipe(int startX, int startY, int endX, int endY, int duration) {
                Log.i(TAG, "无障碍手势滑动: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ")");
                Executors.newSingleThreadExecutor().execute(() -> {
                    com.wumin.auto.GestureManager.performSwipe(startX, startY, endX, endY, duration, null);
                });
            }

            @Override
            public void onGestureBack() {
                Log.i(TAG, "无障碍全局返回键");
                com.wumin.auto.GestureManager.performBack();
            }

            @Override
            public void onDumpUITree() {
                Log.i(TAG, "=== 收到 VS Code 请求：导出 UI 节点树 ===");
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
                        JSONObject response = new JSONObject();
                        response.put("action", "ui_tree");

                        if (service == null) {
                            Log.w(TAG, "无障碍服务未运行，无法导出节点树");
                            response.put("error", "无障碍服务未运行，请在手机端开启无障碍权限");
                            VSCodeDebugServer.getInstance().sendCommandToVSCode(response);
                            return;
                        }

                        Log.i(TAG, "开始遍历窗口获取节点树...");
                        JSONObject treeData = UiTreeDumper.dumpFullTreeForVSCode(service.getWindows());
                        response.put("data", treeData);
                        Log.i(TAG, "节点树导出完成，准备发送到 VS Code");
                        VSCodeDebugServer.getInstance().sendCommandToVSCode(response);
                        Log.i(TAG, "节点树已发送到 VS Code");
                    } catch (Exception e) {
                        Log.e(TAG, "导出 UI 节点树失败", e);
                        try {
                            JSONObject errResponse = new JSONObject();
                            errResponse.put("action", "ui_tree");
                            errResponse.put("error", "导出节点树异常: " + e.getMessage());
                            VSCodeDebugServer.getInstance().sendCommandToVSCode(errResponse);
                        } catch (Exception ignored) {}
                    }
                });
            }
        });
    }

    /**
     * 录屏权限被用户拒绝时通知 VS Code
     */
    public void notifyScreenRejected() {
        VSCodeDebugServer.getInstance().sendCommandToVSCode("screen_rejected");
    }

    /**
     * 录屏服务启动后通知 VS Code 并发送屏幕信息
     */
    public void notifyScreenAccepted(int width, int height) {
        VSCodeDebugServer.getInstance().sendCommandToVSCode("screen_accepted");
        try {
            org.json.JSONObject screenInfo = new org.json.JSONObject();
            screenInfo.put("action", "screen_info");
            screenInfo.put("width", width);
            screenInfo.put("height", height);
            VSCodeDebugServer.getInstance().sendCommandToVSCode(screenInfo);
        } catch (Exception e) {
            Log.e(TAG, "构建屏幕信息失败", e);
        }
    }
}
