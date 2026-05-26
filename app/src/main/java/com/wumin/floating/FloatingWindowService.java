package com.wumin.floating;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.graphics.drawable.GradientDrawable;
import android.view.accessibility.AccessibilityWindowInfo;
import android.app.Dialog;
import android.util.TypedValue;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleService;

import androidx.appcompat.widget.AppCompatImageView;
import com.wumin.ai.AIFloatingService;
import com.wumin.auto.InspectorManager;
import com.wumin.auto.PythonAccessibilityService;
import com.wumin.wuminpy.MainActivity;
import com.wumin.wuminpy.R;

import java.util.ArrayList;
import java.util.List;

public class FloatingWindowService extends LifecycleService {
    private static final String TAG = "FloatingWindowService";

    // ==================== 常量定义 ====================
    /**
     * 小悬浮窗尺寸（px）
     */
    private static final int SMALL_SIZE = 120;
    /**
     * 大悬浮窗宽度（px）
     */
    private static final int BIG_WIDTH = 600;          // 300+300
    /**
     * 大悬浮窗高度（px）
     */
    private static final int BIG_HEIGHT = 520;         // 400+100+25
    /**
     * 扇形半径（px）
     */
    private static final int RADIUS = 200;
    /**
     * 小窗展开为大窗时的水平偏移量（px）
     */
    private static final int EXPAND_OFFSET_X = 240;
    /**
     * 小窗展开为大窗时的垂直偏移量（px）
     */
    private static final int EXPAND_OFFSET_Y = 200;
    /**
     * 动画时长（ms）
     */
    private static final int ANIM_DURATION = 350;
    /**
     * 移动判定阈值（px）
     */
    private static final int MOVE_THRESHOLD = 3;
    /**
     * 点击判定阈值（px）
     */
    private static final int CLICK_THRESHOLD = 10;

    // ==================== 成员变量 ====================
    private int screenWidth;
    private int screenHeight;

    private View floatViewSmall;
    private View floatViewBig;
    private List<AppCompatImageView> menuItems = new ArrayList<>();
    private AppCompatImageView floatMore;

    private WindowManager windowManager;
    private WindowManager.LayoutParams smallParams;      // 小窗布局参数
    private WindowManager.LayoutParams bigParams;        // 大窗布局参数
    private Dialog toolboxDialog;

    /**
     * 当前显示的窗口类型：true=小窗，false=大窗
     */
    private boolean isSmallShowing = true;
    /**
     * 当前贴边位置
     */
    private FloatPos position = FloatPos.LEFT;

    // Binder 用于外部控制
    private final MsgBinder binder = new MsgBinder();

    // ==================== 生命周期 ====================
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        // 初始化视图
        initViews();

        // 获取窗口管理器
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 获取屏幕尺寸
        updateScreenSize();

        // 初始化布局参数（小窗）
        smallParams = createLayoutParams(SMALL_SIZE, SMALL_SIZE);
        smallParams.x = 0;
        smallParams.y = screenHeight / 3;
    }

    private void initViews() {
        floatViewSmall = View.inflate(this, R.layout.float_small_view, null);
        floatViewSmall.setOnTouchListener(new FloatingOnTouchListener());

        // 【核心修改 1】：用一个自定义的 FrameLayout 包裹大悬浮窗，用于拦截返回键！
        floatViewBig = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                // 如果按下了返回键，并且是抬起动作
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    floatMore.setClickable(false);
                    closeSectorMenu(); // 执行收起菜单的动画
                    return true; // 消费掉这个按键，不再传给底层的 App
                }
                return super.dispatchKeyEvent(event);
            }
        };
        // 将原有的布局 inflate 到这个 FrameLayout 中
        View.inflate(this, R.layout.float_big_view, (ViewGroup) floatViewBig);

//        floatViewBig = View.inflate(this, R.layout.float_big_view, null);
        floatMore = floatViewBig.findViewById(R.id.float_more);
        menuItems.add(floatViewBig.findViewById(R.id.float_iv1));
        menuItems.add(floatViewBig.findViewById(R.id.float_iv2));
        menuItems.add(floatViewBig.findViewById(R.id.float_iv3));
        menuItems.add(floatViewBig.findViewById(R.id.float_iv4));
        menuItems.add(floatViewBig.findViewById(R.id.float_iv5));

        floatMore.setOnClickListener(v -> {
            floatMore.setClickable(false);
            closeSectorMenu();
        });

        menuItems.get(1).setOnClickListener(v -> {
            // 启动 AI 服务
            AIFloatingService.start(FloatingWindowService.this);

            // 彻底隐藏当前的主悬浮窗（连小圆点也不显示）
//            hideFloatingWindow();
            closeSectorMenu();
        });

        menuItems.get(2).setOnClickListener(v -> {
            closeSectorMenu();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                InspectorManager.start(new InspectorManager.InspectorListener() {
                    @Override
                    public void onStateChanged(boolean isInspecting) {}
                });
            }, 300);
        });

        // ── 按钮 2：弹出终端 ──
        menuItems.get(3).setOnClickListener(v -> {
            closeSectorMenu();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(FloatingWindowService.this, com.wumin.merminal.TerminalActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }, 200);
        });

        // ── 按钮 3：弹出运行脚本目录列表 ──
        menuItems.get(4).setOnClickListener(v -> {
            closeSectorMenu();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(FloatingWindowService.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("open_tab", "script"));
            }, 200);
        });

        // ── 按钮 4：工具箱弹窗 ──
        menuItems.get(0).setOnClickListener(v -> {
            closeSectorMenu();
            new Handler(Looper.getMainLooper()).postDelayed(() -> showToolboxDialog(), ANIM_DURATION + 50);
        });
    }

    private WindowManager.LayoutParams createLayoutParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        params.format = PixelFormat.RGBA_8888;
        params.gravity = Gravity.START | Gravity.TOP;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params.width = width;
        params.height = height;
        return params;
    }

    private void updateScreenSize() {
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
//        showFloatingWindow();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return binder;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScreenSize();
        // 如果小窗正在显示，校正其位置（防止移出屏幕）
        if (isSmallShowing && floatViewSmall != null && floatViewSmall.isAttachedToWindow()) {
            adjustPositionAfterRotation(floatViewSmall, smallParams);
        } else if (!isSmallShowing && floatViewBig != null && floatViewBig.isAttachedToWindow()) {
            // 大窗也可校正位置（如果需要）
            // 此处暂不处理，因为大窗一般不会拖动
        }
    }

    private void adjustPositionAfterRotation(View view, WindowManager.LayoutParams params) {
        int maxX = screenWidth - view.getWidth();
        int maxY = screenHeight - view.getHeight();
        params.x = Math.max(0, Math.min(params.x, maxX));
        params.y = Math.max(0, Math.min(params.y, maxY));
        windowManager.updateViewLayout(view, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeWindow();
    }

    // ==================== 外部控制接口 ====================
    public class MsgBinder extends Binder {
        public FloatingWindowService getService() {
            return FloatingWindowService.this;
        }

        public void showFloatingWindow() {
            runOnUiThread(FloatingWindowService.this::showFloatingWindow);
        }

        public void hideFloatingWindow() {
            runOnUiThread(FloatingWindowService.this::hideFloatingWindow);
        }
    }

    private void runOnUiThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            new Handler(Looper.getMainLooper()).post(action);
        }
    }

    // ==================== 显示/隐藏窗口 ====================
    public void showFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show();
            return;
        }

        // 关闭当前所有窗口，避免重复
        closeWindow();

        if (floatViewSmall != null && !floatViewSmall.isAttachedToWindow()) {
            smallParams.width = SMALL_SIZE;
            smallParams.height = SMALL_SIZE;
            // 可以恢复上次位置，这里简单使用默认位置
            smallParams.x = 0;
            smallParams.y = screenHeight / 3;
            windowManager.addView(floatViewSmall, smallParams);
            isSmallShowing = true;
        }
    }

    public void hideFloatingWindow() {
        closeWindow();
    }

    public void closeWindow() {
        try {
            dismissToolboxDialog();
            if (floatViewSmall != null && floatViewSmall.isAttachedToWindow()) {
                windowManager.removeView(floatViewSmall);
            }
            if (floatViewBig != null && floatViewBig.isAttachedToWindow()) {
                windowManager.removeView(floatViewBig);
            }
            position = FloatPos.LEFT;
        } catch (Exception e) {
            Log.e(TAG, "remove view failed", e);
        }
    }

    // ==================== 工具箱弹窗 ====================
    private void showToolboxDialog() {
        if (toolboxDialog != null) return;

        String currentPkg = getForegroundPackage();
        String currentActivity = getCurrentActivityInfo();

        // 从主题解析颜色，适配 DayNight
        TypedValue bgVal = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, bgVal, true);
        int bgColor = bgVal.data;
        // 根据背景亮度自动选深/浅文字色
        boolean isDark = ((bgColor >> 16) & 0xFF) + ((bgColor >> 8) & 0xFF) + (bgColor & 0xFF) < 384;
        int textColor = isDark ? 0xFFE0E0E0 : 0xFF212121;
        int dividerColor = isDark ? 0x33FFFFFF : 0x33000000;

        // 圆角边框背景
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(16));
        gd.setStroke(dp(1), dividerColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(gd);
        int padH = dp(24), padV = dp(12);
        root.setPadding(padH, padV, padH, padV);

        String[] items = {
                "打开无障碍设置",
                "当前包名: " + (currentPkg.isEmpty() ? "(未知)" : currentPkg),
                "当前活动: " + currentActivity,
                "打开主界面",
                "退出悬浮窗"
        };

        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                divider.setBackgroundColor(dividerColor);
                root.addView(divider);
            }
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(items[i]);
            tv.setTextColor(textColor);
            tv.setTextSize(15);
            tv.setPadding(0, dp(12), 0, dp(12));
            tv.setOnClickListener(v -> {
                dismissToolboxDialog();
                handleToolboxAction(idx, currentPkg);
            });
            root.addView(tv);
        }

        Dialog dialog = new Dialog(this);
        dialog.setContentView(root);

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.getWindow().setDimAmount(0f);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        dialog.setOnDismissListener(d -> toolboxDialog = null);
        dialog.show();
        toolboxDialog = dialog;
    }

    private void dismissToolboxDialog() {
        if (toolboxDialog != null) {
            toolboxDialog.dismiss();
            toolboxDialog = null;
        }
    }

    private int dp(int dps) {
        return (int) (dps * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void handleToolboxAction(int which, String currentPkg) {
        switch (which) {
            case 0:
                openAccessibilitySettings();
                break;
            case 1:
                copyToClipboard(currentPkg);
                Toast.makeText(this, "包名已复制: " + currentPkg, Toast.LENGTH_SHORT).show();
                break;
            case 2:
                copyToClipboard(currentPkg);
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                break;
            case 3:
                openMainActivity();
                break;
            case 4:
                hideFloatingWindow();
                break;
        }
    }



    private String getForegroundPackage() {
        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service != null) {
            try {
                List<AccessibilityWindowInfo> windows = service.getWindows();
                if (windows != null) {
                    for (int i = windows.size() - 1; i >= 0; i--) {
                        AccessibilityWindowInfo window = windows.get(i);
                        if (window != null && window.getRoot() != null && window.getRoot().getPackageName() != null) {
                            return window.getRoot().getPackageName().toString();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取前台包名失败", e);
            }
        }
        return "";
    }

    private String getCurrentActivityInfo() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            Runtime.getRuntime().exec("dumpsys activity activities").getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("mResumedActivity") || line.contains("mFocusedActivity")) {
                    reader.close();
                    // 提取包名/Activity
                    int start = line.indexOf("com.");
                    if (start >= 0) {
                        int end = line.indexOf(' ', start);
                        if (end < 0) end = line.length();
                        return line.substring(start, end);
                    }
                    return line.trim();
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "获取当前活动失败", e);
        }
        return "(无法获取)";
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("pkg", text));
        }
    }

    // ==================== 扇形菜单动画 ====================
    private void showSectorMenu() {
        int count = menuItems.size();
        for (int i = 0; i < count; i++) {
            PointF point = new PointF();
            int avgAngle = 180 / (count - 1);
            int angle;
            if (position == FloatPos.LEFT) {
                angle = avgAngle * i + 270;
                point.x = (float) Math.cos(Math.toRadians(angle)) * RADIUS + 30;
            } else { // RIGHT
                angle = avgAngle * i - 270;
                point.x = (float) Math.cos(Math.toRadians(angle)) * RADIUS - 30;
            }
            point.y = (float) -Math.sin(Math.toRadians(angle)) * RADIUS;

            AppCompatImageView item = menuItems.get(i);
            item.setTranslationX(0);
            item.setTranslationY(0);

            ObjectAnimator animX = ObjectAnimator.ofFloat(item, "translationX", point.x);
            ObjectAnimator animY = ObjectAnimator.ofFloat(item, "translationY", point.y);
            AnimatorSet set = new AnimatorSet();
            set.setDuration(ANIM_DURATION).playTogether(animX, animY);
            int finalI = i;
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (finalI == 0) floatMore.setClickable(true);
                }
            });
            set.start();
        }

        ObjectAnimator.ofFloat(floatMore, "alpha", 1f, 0.6f).setDuration(ANIM_DURATION).start();
        ObjectAnimator.ofFloat(floatMore, "rotationY", 0f, 360f).setDuration(ANIM_DURATION).start();
    }

    private void closeSectorMenu() {
        int count = menuItems.size();
        for (int i = 0; i < count; i++) {
            PointF point = new PointF();
            int avgAngle = 180 / (count - 1);
            int angle;
            if (position == FloatPos.LEFT) {
                angle = avgAngle * i - 90;
                point.x = (float) Math.cos(Math.toRadians(angle)) * RADIUS + 30;
            } else { // RIGHT
                angle = avgAngle * i + 90;
                point.x = (float) Math.cos(Math.toRadians(angle)) * RADIUS - 30;
            }
            point.y = (float) -Math.sin(Math.toRadians(angle)) * RADIUS;

            AppCompatImageView item = menuItems.get(i);
            ObjectAnimator animX = ObjectAnimator.ofFloat(item, "translationX", point.x, 0);
            ObjectAnimator animY = ObjectAnimator.ofFloat(item, "translationY", point.y, 0);
            AnimatorSet set = new AnimatorSet();
            set.setDuration(ANIM_DURATION).playTogether(animX, animY);
            int finalI = i;
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (finalI == 0) {
                        // 切换回小悬浮窗
                        smallParams.width = SMALL_SIZE;
                        smallParams.height = SMALL_SIZE;
                        smallParams.x = bigParams.x + EXPAND_OFFSET_X;
                        smallParams.y = bigParams.y + EXPAND_OFFSET_Y;
                        if (floatViewSmall != null && !floatViewSmall.isAttachedToWindow()) {
                            windowManager.addView(floatViewSmall, smallParams);
                        }
                        windowManager.removeView(floatViewBig);
                        floatMore.setClickable(true);
                        isSmallShowing = true;
                    }
                }
            });
            set.start();
        }
    }

    // ==================== 小窗触摸监听（含贴边回弹） ====================
    private class FloatingOnTouchListener implements View.OnTouchListener {
        private float downX, downY;
        private float lastX, lastY;
        private boolean isDragging;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    lastX = downX;
                    lastY = downY;
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    float dx = rawX - lastX;
                    float dy = rawY - lastY;

                    if (!isDragging && (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD)) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        // 更新小窗位置
                        smallParams.x += (int) dx;
                        smallParams.y += (int) dy;
                        // 允许拖出屏幕一部分，但限制不能完全移出（防止无法拖回）
                        smallParams.x = Math.max(-v.getWidth() / 2, Math.min(smallParams.x, screenWidth - v.getWidth() / 2));
                        smallParams.y = Math.max(0, Math.min(smallParams.y, screenHeight - v.getHeight()));
                        windowManager.updateViewLayout(v, smallParams);
                    }

                    lastX = rawX;
                    lastY = rawY;
                    break;

                case MotionEvent.ACTION_UP:
                    float upX = event.getRawX();
                    float upY = event.getRawY();
                    float totalDx = upX - downX;
                    float totalDy = upY - downY;

                    if (!isDragging && Math.abs(totalDx) < CLICK_THRESHOLD && Math.abs(totalDy) < CLICK_THRESHOLD) {
                        // 点击事件：展开大悬浮窗
                        floatMore.setClickable(false);
                        // 保存当前小窗位置，用于计算大窗位置
                        int bigX = smallParams.x - EXPAND_OFFSET_X;
                        int bigY = smallParams.y - EXPAND_OFFSET_Y;
                        // 边界限制
//                        bigX = Math.max(0, Math.min(bigX, screenWidth - BIG_WIDTH));
//                        bigY = Math.max(0, Math.min(bigY, screenHeight - BIG_HEIGHT));

                        // 设置大窗布局参数
//                        bigParams = createLayoutParams(BIG_WIDTH, BIG_HEIGHT);
//                        bigParams.x = bigX;
//                        bigParams.y = bigY;
//
//                        windowManager.addView(floatViewBig, bigParams);
//                        showSectorMenu();

                        // 设置大窗布局参数
                        bigParams = createLayoutParams(BIG_WIDTH, BIG_HEIGHT);
                        bigParams.x = bigX;
                        bigParams.y = bigY;

                        // 【核心修改 2】：删掉 FLAG_NOT_FOCUSABLE！
                        // 换成 FLAG_NOT_TOUCH_MODAL (允许接收按键，同时允许点击悬浮窗外部)
                        bigParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

                        windowManager.addView(floatViewBig, bigParams);

                        // 👇👇👇 加上这两行代码，强制大窗抢夺按键焦点 👇👇👇
                        floatViewBig.setFocusableInTouchMode(true);
                        floatViewBig.requestFocus();

                        showSectorMenu();

                        windowManager.removeView(floatViewSmall);
                        isSmallShowing = false;
                    } else {
                        // 拖动结束：执行贴边回弹动画
                        animateToEdge(v);
                    }
                    isDragging = false;
                    break;
            }
            return true;
        }

        /**
         * 执行贴边回弹动画
         */
        private void animateToEdge(View v) {
            int centerX = smallParams.x + v.getWidth() / 2;
            int targetX;
            if (centerX < screenWidth / 2) {
                targetX = 0;
                position = FloatPos.LEFT;
            } else {
                targetX = screenWidth - v.getWidth();
                position = FloatPos.RIGHT;
            }
            int targetY = Math.max(0, Math.min(smallParams.y, screenHeight - v.getHeight()));

            // 计算动画时长（基于距离，最长ANIM_DURATION）
            int deltaX = targetX - smallParams.x;
            int deltaY = targetY - smallParams.y;
            long duration = (long) (Math.hypot(deltaX, deltaY) / 1000 * ANIM_DURATION);
            duration = Math.min(duration, ANIM_DURATION);

            // 创建属性动画
            ObjectAnimator animX = ObjectAnimator.ofInt(FloatingWindowService.this, "smallWindowX", smallParams.x, targetX);
            ObjectAnimator animY = ObjectAnimator.ofInt(FloatingWindowService.this, "smallWindowY", smallParams.y, targetY);
            AnimatorSet set = new AnimatorSet();
            set.setDuration(duration);
            set.setInterpolator(new OvershootInterpolator(1.2f)); // 弹性系数
            set.playTogether(animX, animY);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // 确保最终位置正确
                    smallParams.x = targetX;
                    smallParams.y = targetY;
                    windowManager.updateViewLayout(v, smallParams);
                }
            });
            set.start();
        }
    }

    // ==================== 属性动画 Setter（供 ObjectAnimator 调用） ====================
    public void setSmallWindowX(int x) {
        smallParams.x = x;
        if (floatViewSmall != null && floatViewSmall.isAttachedToWindow()) {
            windowManager.updateViewLayout(floatViewSmall, smallParams);
        }
    }

    public void setSmallWindowY(int y) {
        smallParams.y = y;
        if (floatViewSmall != null && floatViewSmall.isAttachedToWindow()) {
            windowManager.updateViewLayout(floatViewSmall, smallParams);
        }
    }

    // ==================== 内部枚举 ====================
    private enum FloatPos {
        LEFT, RIGHT
    }
}