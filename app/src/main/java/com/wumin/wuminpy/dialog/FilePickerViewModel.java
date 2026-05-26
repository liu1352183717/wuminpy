package com.wumin.wuminpy.dialog;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.wumin.wuminpy.R;
import com.wumin.wuminpy.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FilePickerViewModel extends ViewModel {

    private final MutableLiveData<List<FileItem>> files = new MutableLiveData<>();
    private final MutableLiveData<File> currentPath = new MutableLiveData<>();
    private File rootDir;

    // 定义常见压缩文件扩展名（小写，包含双扩展名）
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            ".zip", ".tar", ".tar.gz", ".tgz", ".tar.xz", ".txz",
            ".7z", ".rar", ".gz", ".bz2", ".xz"
    );

    public LiveData<List<FileItem>> getFiles() {
        return files;
    }

    public LiveData<File> getCurrentPath() {
        return currentPath;
    }

    public void setRootDir(File root) {
        this.rootDir = root;
        loadFiles(root);
    }
    public void loadFiles(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            files.setValue(Collections.emptyList());
            return;
        }

        new Thread(() -> {
            File[] children = directory.listFiles();
            List<FileItem> itemList = new ArrayList<>();
            if (children != null) {
                for (File file : children) {
                    if (file.isDirectory()) {
                        // 文件夹始终添加，用于导航
                        itemList.add(createFileItem(file));
                    } else {
                        // 文件：只添加常见压缩格式
                        String name = file.getName().toLowerCase();
                        for (String ext : ALLOWED_EXTENSIONS) {
                            if (name.endsWith(ext)) {
                                itemList.add(createFileItem(file));
                                break;
                            }
                        }
                    }
                }

                // 排序：文件夹在前，然后按名称升序
                Collections.sort(itemList, (o1, o2) -> {
                    if (o1.isDirectory() && !o2.isDirectory()) return -1;
                    if (!o1.isDirectory() && o2.isDirectory()) return 1;
                    return o1.getName().compareToIgnoreCase(o2.getName());
                });
            }
            files.postValue(itemList);
            currentPath.postValue(directory);
        }).start();
    }

    private FileItem createFileItem(File file) {
        boolean isDir = file.isDirectory();
        int iconRes = isDir ? R.drawable.ic_folder : R.drawable.ic_file;
        long size = isDir ? 0 : file.length();
        long lastModified = file.lastModified();
        return new FileItem(
                file.getName(),
                file.getAbsolutePath(),
                isDir,
                iconRes,
                size,
                lastModified
        );
    }

    public void navigateTo(FileItem item) {
        if (item.isDirectory()) {
            loadFiles(new File(item.getPath()));
        }
    }

    public void navigateUp() {
        File cur = currentPath.getValue();
        Log.d("FilePickerViewModel",cur.getAbsolutePath());
        if (cur != null && cur.getParentFile() != null && cur.getParentFile().exists()) {
            loadFiles(cur.getParentFile());
        }

    }
}
