package com.wumin.auto;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NodeCacheManager {
    // 影子节点储物柜：UUID -> 真实的 UI 节点
    private static final ConcurrentHashMap<String, AccessibilityNodeInfo> nodeMap = new ConcurrentHashMap<>();

    // 存入节点并返回唯一代号
    public static String cacheNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String nodeId = UUID.randomUUID().toString();
        nodeMap.put(nodeId, node);
        return nodeId;
    }

    // 🌟 仅生成唯一 ID 但不缓存节点（用于一次性导出，如 VS Code UI 树）
    public static String generateNodeId() {
        return UUID.randomUUID().toString();
    }

    // 根据代号获取节点
    public static AccessibilityNodeInfo getNode(String nodeId) {
        return nodeMap.get(nodeId);
    }

    // 根据代号直接执行点击操作
    public static boolean clickNode(String nodeId) {
        AccessibilityNodeInfo node = nodeMap.get(nodeId);
        if (node != null && node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else if (node != null) {
            // 如果节点本身不可点，尝试点击它的父节点（常见于 TextView 被包裹在 Layout 中）
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }

    // 清理缓存（在 Window 切换时调用），安全回收所有节点
    public static void clearCache() {
        for (AccessibilityNodeInfo node : nodeMap.values()) {
            try { node.recycle(); } catch (Exception ignored) {}
        }
        nodeMap.clear();
    }
}