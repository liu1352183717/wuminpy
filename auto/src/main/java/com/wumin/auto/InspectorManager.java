package com.wumin.auto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InspectorManager {
    private static WindowManager windowManager;
    private static InspectorOverlayLayout overlayLayout;
    private static boolean isInspecting = false;

    public interface InspectorListener {
        void onStateChanged(boolean isInspecting);
    }
    private static InspectorListener statusListener;

    public static void start(InspectorListener listener) {
        if (isInspecting) return;
        statusListener = listener;
        PythonAccessibilityService service = PythonAccessibilityService.getInstance();
        if (service == null) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
            overlayLayout = new InspectorOverlayLayout(service);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );

            windowManager.addView(overlayLayout, params);
            isInspecting = true;
            if (statusListener != null) statusListener.onStateChanged(true);

            overlayLayout.captureScreenSnapshot();
        });
    }

    public static void stop() {
        if (!isInspecting || overlayLayout == null || windowManager == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                windowManager.removeView(overlayLayout);
            } catch (Exception ignored) {}
            overlayLayout = null;
            isInspecting = false;
            if (statusListener != null) statusListener.onStateChanged(false);
        });
    }

    public static boolean isInspecting() {
        return isInspecting;
    }

    // ── 颜色方案 ──────────────────────────────────────────────
    private static final int COLOR_CLICKABLE   = 0xFF2196F3; // 蓝色
    private static final int COLOR_SCROLLABLE  = 0xFFFF9800; // 橙色
    private static final int COLOR_EDITABLE    = 0xFF4CAF50; // 绿色
    private static final int COLOR_CHECKABLE   = 0xFF9C27B0; // 紫色
    private static final int COLOR_DEFAULT     = 0xFF9E9E9E; // 灰色
    private static final int COLOR_HIGHLIGHT   = 0xFFFF1744; // 红色（选中）

    private static int getNodeColor(TargetNodeData node) {
        if (node.clickable) return COLOR_CLICKABLE;
        if (node.scrollable) return COLOR_SCROLLABLE;
        if (node.editable) return COLOR_EDITABLE;
        if (node.checkable) return COLOR_CHECKABLE;
        return COLOR_DEFAULT;
    }

    // ── 悬浮窗布局 ──────────────────────────────────────────────

    private static class InspectorOverlayLayout extends FrameLayout {
        private final BoxDrawView boxDrawView;
        private LinearLayout topBar;
        private TextView windowInfoText;
        private LinearLayout infoPanel;
        private TextView infoText;
        private TextView selectorCodeText;
        private LinearLayout treePanel;
        private LinearLayout treeContent;
        private EditText searchInput;
        private LinearLayout actionBar;

        private final List<WindowSnapshot> windowSnapshots = new ArrayList<>();
        private int currentWindowIndex = 0;
        private TargetNodeData currentTarget;
        private boolean isCapturing = false;
        private boolean isTreeMode = false;
        private String searchFilter = "";
        private int lastTouchX, lastTouchY;
        private long lastMoveTime = 0;
        private static final long MOVE_THROTTLE_MS = 80;

        public InspectorOverlayLayout(Context context) {
            super(context);
            setBackgroundColor(Color.parseColor("#08000000"));

            // 1. 画板层
            boxDrawView = new BoxDrawView(context);
            addView(boxDrawView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 2. 顶部工具栏
            topBar = buildTopBar(context);
            LayoutParams topBarParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            topBarParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            topBarParams.topMargin = 80;
            addView(topBar, topBarParams);

            // 3. 底部操作栏
            actionBar = buildActionBar(context);
            LayoutParams actionParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            actionParams.bottomMargin = 40;
            actionBar.setVisibility(GONE);
            addView(actionBar, actionParams);

            // 4. 底部详情面板
            infoPanel = buildInfoPanel(context);
            LayoutParams panelParams = new LayoutParams(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.38)
            );
            panelParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            panelParams.bottomMargin = 100;
            addView(infoPanel, panelParams);

            // 5. 树形视图面板
            treePanel = buildTreePanel(context);
            LayoutParams treeParams = new LayoutParams(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.55)
            );
            treeParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            treeParams.bottomMargin = 100;
            treePanel.setVisibility(GONE);
            addView(treePanel, treeParams);

            // 拖拽
            setupDraggable(topBar);
            setupDraggable(infoPanel);
            setupDraggable(treePanel);
            setupDraggable(actionBar);

            setFocusableInTouchMode(true);
            requestFocus();
        }

        // ═══════════════════════════════════════════════════════
        // 顶部工具栏
        // ═══════════════════════════════════════════════════════
        private LinearLayout buildTopBar(Context context) {
            LinearLayout bar = new LinearLayout(context);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(30, 14, 30, 14);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#E8212121"));
            bg.setCornerRadius(50f);
            bar.setBackground(bg);

            TextView dragHandle = new TextView(context);
            dragHandle.setText("≡");
            dragHandle.setTextColor(Color.parseColor("#999999"));
            dragHandle.setTextSize(14f);
            bar.addView(dragHandle);

            // 上一个窗口
            bar.addView(makeTinyBtn(context, "◀", v -> switchWindow(-1)));

            // 窗口/节点信息
            windowInfoText = new TextView(context);
            windowInfoText.setTextColor(Color.WHITE);
            windowInfoText.setTextSize(10f);
            windowInfoText.setGravity(Gravity.CENTER);
            windowInfoText.setPadding(6, 0, 6, 0);
            windowInfoText.setText("抓取中...");
            bar.addView(windowInfoText);

            // 下一个窗口
            bar.addView(makeTinyBtn(context, "▶", v -> switchWindow(1)));

            // 搜索按钮
            Button searchBtn = makeTinyBtn(context, "🔍", v -> toggleSearch());
            bar.addView(searchBtn);

            // 树形/平铺切换
            Button treeBtn = makeTinyBtn(context, "🌲", v -> toggleTreeMode());
            bar.addView(treeBtn);

            // 刷新
            Button refreshBtn = makeTinyBtn(context, "↻", v -> captureScreenSnapshot());
            refreshBtn.setTextColor(Color.parseColor("#4CAF50"));
            bar.addView(refreshBtn);

            // 关闭
            Button exitBtn = makeTinyBtn(context, "✕", v -> InspectorManager.stop());
            exitBtn.setTextColor(Color.parseColor("#F44336"));
            bar.addView(exitBtn);

            return bar;
        }

        // ═══════════════════════════════════════════════════════
        // 底部操作栏
        // ═══════════════════════════════════════════════════════
        private LinearLayout buildActionBar(Context context) {
            LinearLayout bar = new LinearLayout(context);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER);
            bar.setPadding(12, 8, 12, 8);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#E8212121"));
            bg.setCornerRadius(40f);
            bar.setBackground(bg);

            bar.addView(makePillBtn(context, "点击", Color.parseColor("#2196F3"), v -> {
                if (currentTarget != null && currentTarget.nodeRef != null) {
                    currentTarget.nodeRef.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }));

            bar.addView(makePillBtn(context, "长按", Color.parseColor("#FF9800"), v -> {
                if (currentTarget != null && currentTarget.nodeRef != null) {
                    currentTarget.nodeRef.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
                }
            }));

            bar.addView(makePillBtn(context, "滚动▼", Color.parseColor("#4CAF50"), v -> {
                if (currentTarget != null && currentTarget.nodeRef != null) {
                    currentTarget.nodeRef.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                }
            }));

            bar.addView(makePillBtn(context, "复制信息", Color.parseColor("#9C27B0"), v -> copyNodeInfo()));

            bar.addView(makePillBtn(context, "复制选择器", Color.parseColor("#607D8B"), v -> copySelectorCode()));

            return bar;
        }

        // ═══════════════════════════════════════════════════════
        // 详情面板
        // ═══════════════════════════════════════════════════════
        private LinearLayout buildInfoPanel(Context context) {
            LinearLayout panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(24, 16, 24, 16);
            panel.setVisibility(GONE);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F2212121"));
            bg.setCornerRadius(28f);
            panel.setBackground(bg);

            // 拖拽把手
            TextView handle = new TextView(context);
            handle.setText("— 拖拽移动 —");
            handle.setTextColor(Color.parseColor("#66ffffff"));
            handle.setTextSize(10f);
            handle.setGravity(Gravity.CENTER);
            handle.setPadding(0, 0, 0, 8);
            panel.addView(handle);

            ScrollView scroll = new ScrollView(context);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);

            infoText = new TextView(context);
            infoText.setTextColor(Color.WHITE);
            infoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            infoText.setLineSpacing(2f, 1.1f);
            content.addView(infoText);

            // 分隔线
            View divider = new View(context);
            divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
            LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 2);
            divParams.setMargins(0, 10, 0, 6);
            content.addView(divider, divParams);

            // 选择器代码
            TextView codeLabel = new TextView(context);
            codeLabel.setText("▸ 生成的选择器代码:");
            codeLabel.setTextColor(Color.parseColor("#FFAB40"));
            codeLabel.setTextSize(10f);
            codeLabel.setPadding(0, 0, 0, 4);
            content.addView(codeLabel);

            selectorCodeText = new TextView(context);
            selectorCodeText.setTextColor(Color.parseColor("#B2FF59"));
            selectorCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            selectorCodeText.setLineSpacing(2f, 1.1f);
            selectorCodeText.setBackgroundColor(Color.parseColor("#1A000000"));
            selectorCodeText.setPadding(12, 8, 12, 8);
            content.addView(selectorCodeText);

            scroll.addView(content);
            panel.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            return panel;
        }

        // ═══════════════════════════════════════════════════════
        // 树形视图面板
        // ═══════════════════════════════════════════════════════
        private LinearLayout buildTreePanel(Context context) {
            LinearLayout panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(16, 12, 16, 12);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F2212121"));
            bg.setCornerRadius(28f);
            panel.setBackground(bg);

            // 头部
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(4, 0, 4, 8);

            TextView title = new TextView(context);
            title.setText("布局层级树");
            title.setTextColor(Color.WHITE);
            title.setTextSize(12f);
            header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            // 搜索框
            searchInput = new EditText(context);
            searchInput.setHint("搜索节点...");
            searchInput.setHintTextColor(Color.parseColor("#66FFFFFF"));
            searchInput.setTextColor(Color.WHITE);
            searchInput.setTextSize(11f);
            searchInput.setSingleLine(true);
            searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            searchInput.setBackgroundColor(Color.parseColor("#33FFFFFF"));
            searchInput.setPadding(16, 8, 16, 8);
            GradientDrawable searchBg = new GradientDrawable();
            searchBg.setColor(Color.parseColor("#33FFFFFF"));
            searchBg.setCornerRadius(20f);
            searchInput.setBackground(searchBg);
            searchInput.setVisibility(GONE);
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    searchFilter = s.toString().toLowerCase();
                    refreshTreeView();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            header.addView(searchInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            panel.addView(header);

            // 树内容
            ScrollView treeScroll = new ScrollView(context);
            treeContent = new LinearLayout(context);
            treeContent.setOrientation(LinearLayout.VERTICAL);
            treeScroll.addView(treeContent);
            panel.addView(treeScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            return panel;
        }

        // ═══════════════════════════════════════════════════════
        // 按钮工厂
        // ═══════════════════════════════════════════════════════
        private Button makeTinyBtn(Context ctx, String text, OnClickListener l) {
            Button btn = new Button(ctx);
            btn.setText(text);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(12f);
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setPadding(10, 0, 10, 0);
            btn.setMinHeight(0);
            btn.setMinWidth(0);
            btn.setMinimumHeight(0);
            btn.setMinimumWidth(0);
            btn.setOnClickListener(l);
            return btn;
        }

        private Button makePillBtn(Context ctx, String text, int color, OnClickListener l) {
            Button btn = new Button(ctx);
            btn.setText(text);
            btn.setTextColor(color);
            btn.setTextSize(10f);
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setPadding(12, 6, 12, 6);
            btn.setMinHeight(0);
            btn.setMinWidth(0);
            btn.setMinimumHeight(0);
            btn.setMinimumWidth(0);
            btn.setOnClickListener(l);
            return btn;
        }

        // ═══════════════════════════════════════════════════════
        // 拖拽
        // ═══════════════════════════════════════════════════════
        private void setupDraggable(View view) {
            view.setOnTouchListener(new OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    LayoutParams params = (LayoutParams) v.getLayoutParams();
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            params.leftMargin = v.getLeft();
                            params.topMargin = v.getTop();
                            params.gravity = Gravity.TOP | Gravity.LEFT;
                            v.setLayoutParams(params);
                            initialX = params.leftMargin;
                            initialY = params.topMargin;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.leftMargin = initialX + (int)(event.getRawX() - initialTouchX);
                            params.topMargin = initialY + (int)(event.getRawY() - initialTouchY);
                            v.setLayoutParams(params);
                            return true;
                    }
                    return false;
                }
            });
        }

        // ═══════════════════════════════════════════════════════
        // 返回键
        // ═══════════════════════════════════════════════════════
        @Override
        public boolean dispatchKeyEvent(android.view.KeyEvent event) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                if (searchInput.getVisibility() == VISIBLE) {
                    searchInput.setVisibility(GONE);
                    searchInput.setText("");
                    refreshTreeView();
                    return true;
                }
                if (isTreeMode) {
                    toggleTreeMode();
                    return true;
                }
                if (currentTarget != null) {
                    currentTarget = null;
                    infoPanel.setVisibility(GONE);
                    actionBar.setVisibility(GONE);
                    treePanel.setVisibility(GONE);
                    boxDrawView.invalidate();
                    return true;
                }
                InspectorManager.stop();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // ═══════════════════════════════════════════════════════
        // 模式切换
        // ═══════════════════════════════════════════════════════
        private void toggleSearch() {
            if (searchInput.getVisibility() == VISIBLE) {
                searchInput.setVisibility(GONE);
                searchInput.setText("");
                searchFilter = "";
            } else {
                searchInput.setVisibility(VISIBLE);
                searchInput.requestFocus();
                if (!isTreeMode) toggleTreeMode();
            }
            refreshTreeView();
        }

        private void toggleTreeMode() {
            isTreeMode = !isTreeMode;
            if (isTreeMode) {
                infoPanel.setVisibility(GONE);
                treePanel.setVisibility(VISIBLE);
                refreshTreeView();
            } else {
                treePanel.setVisibility(GONE);
                searchInput.setVisibility(GONE);
                searchFilter = "";
                if (currentTarget != null) {
                    infoPanel.setVisibility(VISIBLE);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        // 复制功能
        // ═══════════════════════════════════════════════════════
        private void copyNodeInfo() {
            if (currentTarget == null) return;
            String info = String.format(Locale.US,
                    "className: %s\nid: %s\ntext: \"%s\"\ndesc: \"%s\"\npackage: %s\n" +
                    "bounds: %s\nclickable=%b scrollable=%b editable=%b enabled=%b\n" +
                    "depth=%d childCount=%d",
                    currentTarget.className, currentTarget.id,
                    currentTarget.text, currentTarget.desc, currentTarget.packageName,
                    currentTarget.bounds.toShortString(),
                    currentTarget.clickable, currentTarget.scrollable,
                    currentTarget.editable, currentTarget.enabled,
                    currentTarget.depth, currentTarget.childCount);
            copyToClipboard(info);
            showBriefToast("节点信息已复制");
        }

        private void copySelectorCode() {
            if (currentTarget == null) return;
            StringBuilder code = new StringBuilder();
            String indent = "";

            // 优先用 text
            if (!currentTarget.text.isEmpty()) {
                code.append(indent).append("auto.text(\"").append(currentTarget.text).append("\").findOne()\n");
            }
            // 其次用 desc
            if (!currentTarget.desc.isEmpty()) {
                code.append(indent).append("auto.desc(\"").append(currentTarget.desc).append("\").findOne()\n");
            }
            // 用 id
            if (!currentTarget.id.isEmpty()) {
                code.append(indent).append("auto.id(\"").append(currentTarget.id).append("\").findOne()\n");
            }
            // 用 className + 条件组合
            String shortClass = currentTarget.className;
            if (shortClass.contains(".")) {
                shortClass = shortClass.substring(shortClass.lastIndexOf('.') + 1);
            }
            code.append(indent).append("auto.className(\"").append(currentTarget.className).append("\")");
            if (currentTarget.clickable) code.append(".clickable()");
            if (currentTarget.editable) code.append(".editable()");
            if (currentTarget.scrollable) code.append(".scrollable()");
            code.append(".findOne()\n");

            // 链式组合（最优）
            String bestSelector = "";
            if (!currentTarget.text.isEmpty()) {
                bestSelector = "auto.text(\"" + currentTarget.text + "\").findOne()";
            } else if (!currentTarget.id.isEmpty()) {
                bestSelector = "auto.id(\"" + currentTarget.id + "\").findOne()";
            } else if (!currentTarget.desc.isEmpty()) {
                bestSelector = "auto.desc(\"" + currentTarget.desc + "\").findOne()";
            } else {
                bestSelector = "auto.className(\"" + currentTarget.className + "\")" +
                        (currentTarget.clickable ? ".clickable()" : "") + ".findOne()";
            }
            code.append("\n# 推荐: node = ").append(bestSelector);
            code.append("\n# if node: node.click()");

            copyToClipboard(code.toString());
            showBriefToast("选择器代码已复制");
        }

        private void copyToClipboard(String text) {
            Context ctx = getContext();
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("inspector", text));
            }
        }

        private void showBriefToast(String msg) {
            new Handler(Looper.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        // ═══════════════════════════════════════════════════════
        // 窗口快照
        // ═══════════════════════════════════════════════════════
        public void captureScreenSnapshot() {
            if (isCapturing) return;
            isCapturing = true;
            windowInfoText.setText("抓取中...");
            windowSnapshots.clear();
            currentWindowIndex = 0;
            currentTarget = null;
            infoPanel.setVisibility(GONE);
            actionBar.setVisibility(GONE);
            treePanel.setVisibility(GONE);
            isTreeMode = false;
            searchFilter = "";
            boxDrawView.invalidate();

            new Thread(() -> {
                PythonAccessibilityService service = PythonAccessibilityService.getInstance();
                if (service != null) {
                    List<AccessibilityWindowInfo> windows = service.getWindows();
                    if (windows != null) {
                        for (int i = 0; i < windows.size(); i++) {
                            AccessibilityWindowInfo window = windows.get(i);
                            if (window == null || window.getRoot() == null) continue;
                            if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;

                            WindowSnapshot snapshot = new WindowSnapshot(i, window.getType());
                            flattenNodes(window.getRoot(), snapshot.nodes, 0);

                            if (!snapshot.nodes.isEmpty()) {
                                windowSnapshots.add(snapshot);
                            }
                        }
                    }
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    isCapturing = false;
                    if (windowSnapshots.isEmpty()) {
                        windowInfoText.setText("0 节点");
                    } else {
                        currentWindowIndex = findMainWindowIndex();
                        updateWindowInfo();
                    }
                    boxDrawView.invalidate();
                });
            }).start();
        }

        private void switchWindow(int direction) {
            if (windowSnapshots.isEmpty()) return;
            currentWindowIndex = (currentWindowIndex + direction + windowSnapshots.size()) % windowSnapshots.size();
            currentTarget = null;
            infoPanel.setVisibility(GONE);
            actionBar.setVisibility(GONE);
            isTreeMode = false;
            treePanel.setVisibility(GONE);
            updateWindowInfo();
            boxDrawView.invalidate();
        }

        private void updateWindowInfo() {
            if (windowSnapshots.isEmpty()) {
                windowInfoText.setText("0 节点");
                return;
            }
            WindowSnapshot ws = windowSnapshots.get(currentWindowIndex);
            windowInfoText.setText(String.format("%d/%d (%d)",
                    currentWindowIndex + 1, windowSnapshots.size(), ws.nodes.size()));
        }

        private int findMainWindowIndex() {
            int maxNodes = 0, best = 0;
            for (int i = 0; i < windowSnapshots.size(); i++) {
                if (windowSnapshots.get(i).nodes.size() > maxNodes) {
                    maxNodes = windowSnapshots.get(i).nodes.size();
                    best = i;
                }
            }
            return best;
        }

        private void flattenNodes(AccessibilityNodeInfo node, List<TargetNodeData> list, int depth) {
            if (node == null) return;
            // 收集所有节点（不仅限于 isVisibleToUser），与 AutoJS 行为一致
            list.add(new TargetNodeData(node, depth));
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    flattenNodes(child, list, depth + 1);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        // 树形视图刷新
        // ═══════════════════════════════════════════════════════
        private void refreshTreeView() {
            treeContent.removeAllViews();
            if (windowSnapshots.isEmpty()) return;

            List<TargetNodeData> nodes = windowSnapshots.get(currentWindowIndex).nodes;
            for (TargetNodeData node : nodes) {
                // 搜索过滤
                if (!searchFilter.isEmpty()) {
                    boolean match = node.text.toLowerCase().contains(searchFilter)
                            || node.desc.toLowerCase().contains(searchFilter)
                            || node.className.toLowerCase().contains(searchFilter)
                            || node.id.toLowerCase().contains(searchFilter);
                    if (!match) continue;
                }

                // 高亮匹配的搜索词
                TextView tv = new TextView(getContext());
                String indent = new String(new char[node.depth]).replace('\0', ' ');
                String label = buildNodeLabel(node);
                tv.setText(indent + label);
                tv.setTextSize(9f);
                tv.setTextColor(getNodeTextColor(node));
                tv.setPadding(4, 2, 4, 2);
                tv.setTag(node);

                // 是否是当前选中节点
                if (currentTarget == node) {
                    tv.setBackgroundColor(Color.parseColor("#44FF1744"));
                }

                tv.setOnClickListener(v -> {
                    currentTarget = (TargetNodeData) v.getTag();
                    boxDrawView.invalidate();
                    refreshTreeView(); // 重新高亮
                    updateInfoPanel();
                    actionBar.setVisibility(VISIBLE);
                });

                treeContent.addView(tv);
            }
        }

        private String buildNodeLabel(TargetNodeData node) {
            String shortClass = node.className;
            if (shortClass.contains(".")) {
                shortClass = shortClass.substring(shortClass.lastIndexOf('.') + 1);
            }
            StringBuilder sb = new StringBuilder();
            if (!node.visible) sb.append("◇ "); // 不可见标记
            sb.append("[").append(shortClass).append("]");
            if (!node.text.isEmpty()) sb.append(" \"").append(truncate(node.text, 40)).append("\"");
            if (!node.desc.isEmpty()) sb.append(" desc=\"").append(truncate(node.desc, 30)).append("\"");
            if (!node.id.isEmpty()) {
                String shortId = node.id;
                if (shortId.contains(":id/")) shortId = "#" + shortId.substring(shortId.lastIndexOf(":id/") + 4);
                sb.append(" ").append(shortId);
            }
            if (node.clickable) sb.append(" [可点]");
            if (node.editable) sb.append(" [编辑]");
            if (node.scrollable) sb.append(" [滚动]");
            return sb.toString();
        }

        private int getNodeTextColor(TargetNodeData node) {
            if (node == currentTarget) return Color.parseColor("#FF5252");
            if (!node.visible) return Color.parseColor("#666666");
            if (node.clickable) return Color.parseColor("#64B5F6");
            if (node.scrollable) return Color.parseColor("#FFB74D");
            if (node.editable) return Color.parseColor("#81C784");
            return Color.parseColor("#BDBDBD");
        }

        private String truncate(String s, int max) {
            return s.length() > max ? s.substring(0, max) + "…" : s;
        }

        // ═══════════════════════════════════════════════════════
        // 画板
        // ═══════════════════════════════════════════════════════
        private class BoxDrawView extends View {
            private final Paint boxPaint = new Paint();
            private final Paint highlightStroke = new Paint();
            private final Paint highlightFill = new Paint();

            public BoxDrawView(Context context) {
                super(context);
                boxPaint.setStyle(Paint.Style.STROKE);
                boxPaint.setStrokeWidth(2f);
                boxPaint.setAntiAlias(true);

                highlightStroke.setColor(COLOR_HIGHLIGHT);
                highlightStroke.setStyle(Paint.Style.STROKE);
                highlightStroke.setStrokeWidth(6f);
                highlightStroke.setAntiAlias(true);

                highlightFill.setColor(Color.parseColor("#30FF1744"));
                highlightFill.setStyle(Paint.Style.FILL);
                highlightFill.setAntiAlias(true);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (isCapturing || windowSnapshots.isEmpty()) return true;

                int x = (int) event.getRawX();
                int y = (int) event.getRawY();
                lastTouchX = x;
                lastTouchY = y;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        actionBar.setVisibility(GONE);
                        currentTarget = findBestMatch(x, y);
                        invalidate();
                        if (isTreeMode) refreshTreeView();
                        if (currentTarget != null) updateInfoPanel();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        long now = System.currentTimeMillis();
                        if (now - lastMoveTime < MOVE_THROTTLE_MS) return true;
                        lastMoveTime = now;
                        currentTarget = findBestMatch(x, y);
                        invalidate();
                        if (isTreeMode) refreshTreeView();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (currentTarget != null) {
                            if (isTreeMode) {
                                treePanel.setVisibility(VISIBLE);
                                refreshTreeView();
                            } else {
                                infoPanel.setVisibility(VISIBLE);
                            }
                            actionBar.setVisibility(VISIBLE);
                            updateInfoPanel();
                        }
                        return true;
                }
                return true;
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (windowSnapshots.isEmpty()) return;

                int[] location = new int[2];
                getLocationOnScreen(location);
                canvas.save();
                canvas.translate(-location[0], -location[1]);

                List<TargetNodeData> currentNodes = windowSnapshots.get(currentWindowIndex).nodes;
                for (TargetNodeData node : currentNodes) {
                    // 跳过无实际面积的不可见节点
                    if (node.bounds.width() <= 0 || node.bounds.height() <= 0) continue;
                    int color = getNodeColor(node);
                    boxPaint.setColor(color);
                    boxPaint.setAlpha(node == currentTarget ? 100 : 70);
                    boxPaint.setStrokeWidth(node == currentTarget ? 0.5f : 1.2f);
                    canvas.drawRect(node.bounds, boxPaint);
                }

                // 所有 clickable 节点加粗描边
                boxPaint.setStrokeWidth(2.5f);
                boxPaint.setAlpha(140);
                for (TargetNodeData node : currentNodes) {
                    if (node.bounds.width() <= 0 || node.bounds.height() <= 0) continue;
                    if (node.clickable && node != currentTarget) {
                        boxPaint.setColor(COLOR_CLICKABLE);
                        canvas.drawRect(node.bounds, boxPaint);
                    }
                }

                // 选中高亮
                if (currentTarget != null && currentTarget.bounds.width() > 0) {
                    canvas.drawRect(currentTarget.bounds, highlightFill);
                    canvas.drawRect(currentTarget.bounds, highlightStroke);
                }
                canvas.restore();
            }

            public TargetNodeData findBestMatch(int x, int y) {
                TargetNodeData best = null;
                int minArea = Integer.MAX_VALUE;
                List<TargetNodeData> currentNodes = windowSnapshots.get(currentWindowIndex).nodes;

                for (TargetNodeData node : currentNodes) {
                    if (node.bounds.width() <= 0 || node.bounds.height() <= 0) continue;
                    if (node.bounds.contains(x, y)) {
                        int area = node.bounds.width() * node.bounds.height();
                        if (area < minArea) {
                            minArea = area;
                            best = node;
                        }
                    }
                }
                return best;
            }
        }

        // ═══════════════════════════════════════════════════════
        // 详情面板更新
        // ═══════════════════════════════════════════════════════
        private void updateInfoPanel() {
            if (currentTarget == null) return;
            infoPanel.setVisibility(VISIBLE);
            actionBar.setVisibility(VISIBLE);

            StringBuilder sb = new StringBuilder();
            sb.append("【基础信息】\n");
            sb.append("文本: ").append(currentTarget.text.isEmpty() ? "(空)" : currentTarget.text).append("\n");
            sb.append("描述: ").append(currentTarget.desc.isEmpty() ? "(空)" : currentTarget.desc).append("\n");
            sb.append("类名:\n  ").append(currentTarget.className).append("\n");
            sb.append("ID: ").append(currentTarget.id.isEmpty() ? "(空)" : currentTarget.id).append("\n");
            sb.append("包名: ").append(currentTarget.packageName).append("\n\n");

            sb.append("【空间信息】\n");
            sb.append(String.format("深度: %d  |  子节点: %d\n", currentTarget.depth, currentTarget.childCount));
            sb.append(String.format("宽×高: %d×%d\n", currentTarget.bounds.width(), currentTarget.bounds.height()));
            sb.append("边界: ").append(currentTarget.bounds.toShortString()).append("\n\n");

            sb.append("【交互状态】\n");
            if (currentTarget.clickable) sb.append("✓ "); else sb.append("✗ ");
            sb.append("可点击  ");
            if (currentTarget.longClickable) sb.append("✓ "); else sb.append("✗ ");
            sb.append("可长按\n");
            if (currentTarget.scrollable) sb.append("✓ "); else sb.append("✗ ");
            sb.append("可滚动  ");
            if (currentTarget.editable) sb.append("✓ "); else sb.append("✗ ");
            sb.append("可编辑\n");
            if (currentTarget.enabled) sb.append("✓ "); else sb.append("✗ ");
            sb.append("已启用  ");
            if (currentTarget.focused) sb.append("✓ "); else sb.append("✗ ");
            sb.append("已聚焦\n");
            if (currentTarget.selected) sb.append("✓ "); else sb.append("✗ ");
            sb.append("已选中  ");
            if (currentTarget.checked) sb.append("✓ "); else sb.append("✗ ");
            sb.append("已勾选\n");
            if (currentTarget.focusable) sb.append("✓ "); else sb.append("✗ ");
            sb.append("可聚焦  ");
            if (currentTarget.checkable) sb.append("✓ "); else sb.append("✗ ");
            sb.append("可勾选\n");
            if (currentTarget.password) sb.append("🔒 密码字段");

            infoText.setText(sb.toString());
            updateSelectorCode();
        }

        private void updateSelectorCode() {
            if (currentTarget == null) return;
            StringBuilder code = new StringBuilder();

            // 构建最优选择器
            if (!currentTarget.text.isEmpty()) {
                code.append("# 按文本\nnode = auto.text(\"")
                        .append(currentTarget.text).append("\").findOne()\n");
            }
            if (!currentTarget.id.isEmpty()) {
                code.append("# 按 ID\nnode = auto.id(\"")
                        .append(currentTarget.id).append("\").findOne()\n");
            }
            if (!currentTarget.desc.isEmpty()) {
                code.append("# 按描述\nnode = auto.desc(\"")
                        .append(currentTarget.desc).append("\").findOne()\n");
            }

            // 组合选择器
            code.append("# 链式组合\nnode = auto.className(\"")
                    .append(currentTarget.className).append("\")");
            if (!currentTarget.text.isEmpty()) {
                code.append("\n    .text(\"").append(currentTarget.text).append("\")");
            }
            if (currentTarget.clickable) code.append(".clickable()");
            if (currentTarget.scrollable) code.append(".scrollable()");
            code.append("\n    .findOne()");

            selectorCodeText.setText(code.toString());
        }
    }

    // ── 数据类 ──────────────────────────────────────────────

    private static class WindowSnapshot {
        int windowId, windowType;
        List<TargetNodeData> nodes = new ArrayList<>();
        WindowSnapshot(int id, int type) { this.windowId = id; this.windowType = type; }
    }

    private static class TargetNodeData {
        Rect bounds = new Rect();
        AccessibilityNodeInfo nodeRef;
        String className = "", text = "", desc = "", id = "", packageName = "";
        boolean clickable, longClickable, scrollable, checked, focusable, focused, selected, enabled, editable, password, checkable;
        boolean visible;
        int depth, childCount;

        TargetNodeData(AccessibilityNodeInfo node, int depth) {
            this.depth = depth;
            this.nodeRef = node;
            this.visible = node.isVisibleToUser();
            node.getBoundsInScreen(this.bounds);
            if (node.getClassName() != null) this.className = node.getClassName().toString();
            if (node.getText() != null) this.text = node.getText().toString();
            if (node.getContentDescription() != null) this.desc = node.getContentDescription().toString();
            if (node.getViewIdResourceName() != null) this.id = node.getViewIdResourceName();
            if (node.getPackageName() != null) this.packageName = node.getPackageName().toString();

            this.childCount = node.getChildCount();
            this.clickable = node.isClickable();
            this.longClickable = node.isLongClickable();
            this.scrollable = node.isScrollable();
            this.checkable = node.isCheckable();
            this.checked = node.isChecked();
            this.focusable = node.isFocusable();
            this.focused = node.isFocused();
            this.selected = node.isSelected();
            this.enabled = node.isEnabled();
            this.editable = node.isEditable();
            this.password = node.isPassword();
        }
    }
}
