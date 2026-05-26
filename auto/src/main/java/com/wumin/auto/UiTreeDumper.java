package com.wumin.auto;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class UiTreeDumper {

    // 模式 1：给 AI 提取干净的、可交互的节点 (提纯模式)
    public static JSONArray dumpActionableNodes(AccessibilityNodeInfo root) {
        JSONArray nodesArray = new JSONArray();
        traverseForAI(root, nodesArray);
        return nodesArray;
    }

    // 【新增方法】：处理多窗口合并提取
    public static JSONArray dumpAllWindows(List<AccessibilityWindowInfo> windows) {
        JSONArray allNodesArray = new JSONArray();
        if (windows == null) return allNodesArray;

        // 遍历屏幕上的每一个窗口（主界面、状态栏、输入法、弹窗等）
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) {
                // 将该窗口提取的节点追加到统一的数组中
                traverseForAI(root, allNodesArray);
            }
        }
        return allNodesArray;
    }

    // 模式 2：给 VS Code 投屏用的完整节点树（含 bounds、层级结构）
    public static JSONObject dumpFullTreeForVSCode(List<AccessibilityWindowInfo> windows) {
        JSONObject result = new JSONObject();
        JSONArray windowTrees = new JSONArray();
        if (windows == null) {
            try { result.put("windows", windowTrees); } catch (Exception ignored) {}
            return result;
        }

        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) {
                try {
                    JSONObject windowJson = new JSONObject();
                    windowJson.put("windowType", window.getType());
                    windowJson.put("windowLayer", window.getLayer());
                    windowJson.put("root", buildNodeTree(root));
                    windowTrees.put(windowJson);
                } catch (Exception ignored) {} finally {
                    // 🌟 递归回收节点，防止系统无障碍节点池耗尽
                    recycleTree(root);
                }
            }
        }
        try { result.put("windows", windowTrees); } catch (Exception ignored) {}
        return result;
    }

    private static JSONObject buildNodeTree(AccessibilityNodeInfo node) {
        JSONObject json = new JSONObject();
        if (node == null) return json;

        // 提前生成 nodeId 和 children，确保即使属性读取失败，树结构也不丢失
        // 🌟 使用 generateNodeId() 替代 cacheNode()，避免将一次性导出节点缓存在全局 Map 中
        String nodeId = NodeCacheManager.generateNodeId();
        try { json.put("nodeId", nodeId != null ? nodeId : ""); } catch (Exception ignored) {}

        JSONArray children = new JSONArray();
        if (node.getChildCount() > 0) {
            for (int i = 0; i < node.getChildCount(); i++) {
                try {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        children.put(buildNodeTree(child));
                    }
                } catch (Exception e) {
                    // 单个子节点获取失败不影响其他子节点
                }
            }
        }
        try { json.put("children", children); } catch (Exception ignored) {}

        try {
            json.put("className", safeStr(node.getClassName()));
            json.put("text", safeStr(node.getText()));
            json.put("contentDescription", safeStr(node.getContentDescription()));
            json.put("resourceId", safeStr(node.getViewIdResourceName()));
            json.put("packageName", safeStr(node.getPackageName()));
            json.put("clickable", node.isClickable());
            json.put("longClickable", node.isLongClickable());
            json.put("contextClickable", node.isContextClickable());
            json.put("scrollable", node.isScrollable());
            json.put("checkable", node.isCheckable());
            json.put("focusable", node.isFocusable());
            json.put("focused", node.isFocused());
            json.put("accessibilityFocused", node.isAccessibilityFocused());
            json.put("editable", node.isEditable());
            json.put("enabled", node.isEnabled());
            json.put("checked", node.isChecked());
            json.put("selected", node.isSelected());
            json.put("dismissable", node.isDismissable());
            json.put("password", node.isPassword());
            json.put("multiLine", node.isMultiLine());
            json.put("drawingOrder", node.getDrawingOrder());
            json.put("windowId", node.getWindowId());
            json.put("maxTextLength", node.getMaxTextLength());
            json.put("inputType", node.getInputType());
            json.put("liveRegion", node.getLiveRegion());

            // API 28+ 属性
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                json.put("tooltipText", safeStr(node.getTooltipText()));
                json.put("paneTitle", safeStr(node.getPaneTitle()));
            }

            // API 30+ 属性
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                json.put("error", safeStr(node.getError()));
                json.put("stateDescription", safeStr(node.getStateDescription()));
            }

            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            JSONObject boundsJson = new JSONObject();
            boundsJson.put("left", bounds.left);
            boundsJson.put("top", bounds.top);
            boundsJson.put("right", bounds.right);
            boundsJson.put("bottom", bounds.bottom);
            json.put("bounds", boundsJson);
        } catch (Exception ignored) {}

        return json;
    }

    private static String safeStr(CharSequence cs) {
        if (cs == null) return "";
        String s = cs.toString();
        return s != null ? s : "";
    }

    /**
     * 递归回收 AccessibilityNodeInfo 节点及其所有子节点。
     * 不回收则系统节点池会被耗尽（上限约 50-100 个），后续 getWindows() 将返回空。
     */
    private static void recycleTree(AccessibilityNodeInfo node) {
        if (node == null) return;
        try {
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) recycleTree(child);
            }
        } catch (Exception ignored) {}
        try { node.recycle(); } catch (Exception ignored) {}
    }

    private static void traverseForAI(AccessibilityNodeInfo node, JSONArray result) {
        if (node == null) return;

        // 核心过滤逻辑：只有对 AI 有意义的节点才提取
        boolean hasText = node.getText() != null && node.getText().length() > 0;
        boolean hasDesc = node.getContentDescription() != null && node.getContentDescription().length() > 0;
        boolean isActionable = node.isClickable() || node.isScrollable() || node.isCheckable();

        if (hasText || hasDesc || isActionable) {
            try {
                JSONObject jsonNode = new JSONObject();
                jsonNode.put("id", NodeCacheManager.cacheNode(node)); // 分配代号
                jsonNode.put("class", node.getClassName());
                if (hasText) jsonNode.put("text", node.getText().toString());
                if (hasDesc) jsonNode.put("desc", node.getContentDescription().toString());
                if (node.isClickable()) jsonNode.put("clickable", true);

                result.put(jsonNode);
            } catch (Exception ignored) {}
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            traverseForAI(node.getChild(i), result);
        }
    }
}