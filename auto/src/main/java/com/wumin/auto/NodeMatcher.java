package com.wumin.auto;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;

public class NodeMatcher {

    // 递归遍历 UI 树，寻找符合 query 的节点
    public static void findNodes(AccessibilityNodeInfo node, JSONObject query, int limit, JSONArray results) {
        findNodesInternal(node, query, limit, results, 0);
    }

    private static void findNodesInternal(AccessibilityNodeInfo node, JSONObject query, int limit, JSONArray results, int depth) {
        if (node == null) return;
        if (limit > 0 && results.length() >= limit) return;

        if (isMatch(node, query, depth)) {
            try {
                JSONObject jsonNode = new JSONObject();
                jsonNode.put("id", NodeCacheManager.cacheNode(node));
                if (node.getClassName() != null) jsonNode.put("class", node.getClassName().toString());
                if (node.getText() != null) jsonNode.put("text", node.getText().toString());
                if (node.getContentDescription() != null) jsonNode.put("desc", node.getContentDescription().toString());
                if (node.getPackageName() != null) jsonNode.put("package", node.getPackageName().toString());
                if (node.getViewIdResourceName() != null) jsonNode.put("view_id", node.getViewIdResourceName().toString());

                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                JSONObject boundsObj = new JSONObject();
                boundsObj.put("left", bounds.left);
                boundsObj.put("top", bounds.top);
                boundsObj.put("right", bounds.right);
                boundsObj.put("bottom", bounds.bottom);
                jsonNode.put("bounds", boundsObj);

                jsonNode.put("clickable", node.isClickable());
                jsonNode.put("long_clickable", node.isLongClickable());
                jsonNode.put("scrollable", node.isScrollable());
                jsonNode.put("checkable", node.isCheckable());
                jsonNode.put("checked", node.isChecked());
                jsonNode.put("focusable", node.isFocusable());
                jsonNode.put("focused", node.isFocused());
                jsonNode.put("editable", node.isEditable());
                jsonNode.put("enabled", node.isEnabled());
                jsonNode.put("selected", node.isSelected());
                jsonNode.put("depth", depth);
                jsonNode.put("child_count", node.getChildCount());

                results.put(jsonNode);
            } catch (Exception ignored) {}
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            findNodesInternal(node.getChild(i), query, limit, results, depth + 1);
        }
    }

    private static boolean isMatch(AccessibilityNodeInfo node, JSONObject query, int depth) {
        if (query == null || query.length() == 0) return false;
        try {
            if (query.has("text")) {
                if (node.getText() == null || !node.getText().toString().equals(query.getString("text"))) return false;
            }
            if (query.has("text_contains")) {
                if (node.getText() == null || !node.getText().toString().contains(query.getString("text_contains"))) return false;
            }
            if (query.has("text_matches")) {
                if (node.getText() == null || !node.getText().toString().matches(query.getString("text_matches"))) return false;
            }
            if (query.has("desc")) {
                if (node.getContentDescription() == null || !node.getContentDescription().toString().equals(query.getString("desc"))) return false;
            }
            if (query.has("desc_contains")) {
                if (node.getContentDescription() == null || !node.getContentDescription().toString().contains(query.getString("desc_contains"))) return false;
            }
            if (query.has("desc_matches")) {
                if (node.getContentDescription() == null || !node.getContentDescription().toString().matches(query.getString("desc_matches"))) return false;
            }
            if (query.has("class_name")) {
                if (node.getClassName() == null || !node.getClassName().toString().equals(query.getString("class_name"))) return false;
            }
            if (query.has("class_contains")) {
                if (node.getClassName() == null || !node.getClassName().toString().contains(query.getString("class_contains"))) return false;
            }
            if (query.has("id")) {
                if (node.getViewIdResourceName() == null || !node.getViewIdResourceName().equals(query.getString("id"))) return false;
            }
            if (query.has("package_name")) {
                if (node.getPackageName() == null || !node.getPackageName().toString().equals(query.getString("package_name"))) return false;
            }
            if (query.has("clickable")) {
                if (node.isClickable() != query.getBoolean("clickable")) return false;
            }
            if (query.has("long_clickable")) {
                if (node.isLongClickable() != query.getBoolean("long_clickable")) return false;
            }
            if (query.has("scrollable")) {
                if (node.isScrollable() != query.getBoolean("scrollable")) return false;
            }
            if (query.has("checkable")) {
                if (node.isCheckable() != query.getBoolean("checkable")) return false;
            }
            if (query.has("checked")) {
                if (node.isChecked() != query.getBoolean("checked")) return false;
            }
            if (query.has("focusable")) {
                if (node.isFocusable() != query.getBoolean("focusable")) return false;
            }
            if (query.has("focused")) {
                if (node.isFocused() != query.getBoolean("focused")) return false;
            }
            if (query.has("selected")) {
                if (node.isSelected() != query.getBoolean("selected")) return false;
            }
            if (query.has("editable")) {
                if (node.isEditable() != query.getBoolean("editable")) return false;
            }
            if (query.has("enabled")) {
                if (node.isEnabled() != query.getBoolean("enabled")) return false;
            }
            if (query.has("depth")) {
                if (depth != query.getInt("depth")) return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}