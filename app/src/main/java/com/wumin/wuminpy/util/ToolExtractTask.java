package com.wumin.wuminpy.util;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 使用自编译工具（tar/unzip）执行解压任务
 */
public class ToolExtractTask extends AsyncTask<Void, String, Boolean> {

    private final Context context;
    private final File archiveFile;
    private final File targetDir;
    private final String toolDir;        // 工具所在目录
    private final OnProgressListener progressListener;
    private final OnCompleteListener completeListener;
    private String errorMessage;

    public interface OnProgressListener {
        void onProgress(String line);
    }

    public interface OnCompleteListener {
        void onComplete(boolean success, String message, File extractedDir);
    }

    public ToolExtractTask(Context context, File archiveFile, File targetDir, String toolDir,
                           OnProgressListener progressListener, OnCompleteListener completeListener) {
        this.context = context;
        this.archiveFile = archiveFile;
        this.targetDir = targetDir;
        this.toolDir = toolDir;
        this.progressListener = progressListener;
        this.completeListener = completeListener;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        String lower = archiveFile.getName().toLowerCase();
        String[] command;

        if (lower.endsWith(".zip")) {
            command = new String[]{toolDir + "/unzip", "-o", archiveFile.getAbsolutePath(), "-d", targetDir.getAbsolutePath()};
        } else if (lower.endsWith(".tar")) {
            command = new String[]{toolDir + "/tar", "-xvf", archiveFile.getAbsolutePath(), "-C", targetDir.getAbsolutePath()};
        } else if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            command = new String[]{toolDir + "/tar", "-zxvf", archiveFile.getAbsolutePath(), "-C", targetDir.getAbsolutePath()};
        } else if (lower.endsWith(".tar.xz") || lower.endsWith(".txz")) {
            command = new String[]{toolDir + "/tar", "-xJvf", archiveFile.getAbsolutePath(), "-C", targetDir.getAbsolutePath()};
        } else {
            errorMessage = "不支持的压缩格式";
            return false;
        }
        Log.d("ToolExtractTask", "Command: " + String.join(" ", command));

        // 设置环境变量（可根据需要调整）
        Map<String, String> env = new java.util.HashMap<>();
        String home = new File(context.getFilesDir(), "home").getAbsolutePath();
        String libDir = new File(context.getFilesDir(), "usr/lib").getAbsolutePath();
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        String currentPath = System.getenv("PATH");
        env.put("PATH", toolDir + ":" + libDir + ":" + nativeLibDir + ":" + (currentPath != null ? currentPath : ""));
        env.put("HOME", home);

        try {
            CommandExecutor.executeSyncWithCallback(
                    command,
                    null, // 工作目录由命令参数指定
                    env,
                    0,    // 无超时
                    true, // 合并输出流
                    new CommandExecutor.LineCallback() {
                        @Override
                        public void onOutputLine(String line) {
                            publishProgress(line);
                        }
                        @Override
                        public void onErrorLine(String line) {
                            // 合并流，无需处理
                        }
                    }
            );
            return true;
        } catch (IOException | InterruptedException | TimeoutException e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return false;
        }
    }

    @Override
    protected void onProgressUpdate(String... values) {
        if (progressListener != null) progressListener.onProgress(values[0]);
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (completeListener != null) {
            if (success) {
                // 直接返回目标父目录（由调用方根据顶级目录构造实际 NDK 根）
                completeListener.onComplete(true, "解压完成", targetDir);
            } else {
                completeListener.onComplete(false, errorMessage != null ? errorMessage : "未知错误", null);
            }
        }
    }
}