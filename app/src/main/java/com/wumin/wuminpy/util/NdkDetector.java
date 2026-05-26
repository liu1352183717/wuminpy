package com.wumin.wuminpy.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NdkDetector {
    private static final String TAG = "NdkDetector";

    public static class NdkArchiveInfo {
        public final boolean isValid;
        public final String topLevelDir; // 压缩包内统一顶级目录名，可能为 null
        public NdkArchiveInfo(boolean isValid, String topLevelDir) {
            this.isValid = isValid;
            this.topLevelDir = topLevelDir;
        }
    }

    /**
     * 检测压缩包是否为 NDK 并提取顶级目录名
     * @param context  上下文
     * @param file     压缩包文件
     * @param toolDir  工具目录（存放 unzip/tar）
     * @return NdkArchiveInfo 对象
     */
    public static NdkArchiveInfo inspectArchive(Context context, File file, String toolDir) {
        String name = file.getName().toLowerCase();
        String[] command = null;

        if (name.endsWith(".zip")) {
            command = new String[]{toolDir + "/unzip", "-Z1", file.getAbsolutePath()}; // 仅列出文件，无额外信息
        } else if (name.endsWith(".tar")) {
            command = new String[]{toolDir + "/tar", "-tf", file.getAbsolutePath()};
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            command = new String[]{toolDir + "/tar", "-tzf", file.getAbsolutePath()};
        } else if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            command = new String[]{toolDir + "/tar", "-tJf", file.getAbsolutePath()};
        } else {
            return new NdkArchiveInfo(false, null);
        }

        // 设置环境变量（与解压时保持一致）
        Map<String, String> env = new java.util.HashMap<>();
        String home = new File(context.getFilesDir(), "home").getAbsolutePath();
        String libDir = new File(context.getFilesDir(), "usr/lib").getAbsolutePath();
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        String currentPath = System.getenv("PATH");
        env.put("PATH", toolDir + ":" + libDir + ":" + nativeLibDir + ":" + (currentPath != null ? currentPath : ""));
        env.put("HOME", home);

        CommandExecutor.Result result = CommandExecutor.executeSync(command, null, env, 10, true);
        if (!result.isSuccess()) {
            Log.e(TAG, "命令执行失败: " + result.exception);
            return new NdkArchiveInfo(false, null);
        }

        boolean foundNdk = false;
        Set<String> topDirs = new HashSet<>();

        for (String line : result.outputLines) {
            if (line.trim().isEmpty()) continue;

            // NDK 特征检测
            if (line.contains("toolchains/") || line.contains("/toolchains/") ||
                    line.endsWith("ndk-build") || line.contains("/ndk-build") ||
                    line.contains("meta/") || line.contains("/meta/")) {
                foundNdk = true;
            }

            // 提取顶级目录
            String path = line.trim();
            if (path.startsWith("./")) path = path.substring(2);
            int slash = path.indexOf('/');
            if (slash != -1) {
                String top = path.substring(0, slash);
                if (!top.isEmpty() && !top.equals(".") && !top.equals("..")) {
                    topDirs.add(top);
                }
            } else {
                // 无斜杠表示文件在根目录（压缩包无统一目录）
                topDirs.add(null);
            }
        }

        String topLevelDir = (topDirs.size() == 1) ? topDirs.iterator().next() : null;
        return new NdkArchiveInfo(foundNdk, topLevelDir);
    }

    // 保留原方法（可选）
    public static boolean isNdkArchive(Context context, File file, String toolDir) {
        return inspectArchive(context, file, toolDir).isValid;
    }
}