package com.wumin.wuminpy.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.FileObserver;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.wumin.wuminpy.R;
import com.wumin.wuminpy.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件浏览器 ViewModel 基类。
 * 子类只需实现 {@link #getRootDirectory(Application)} 提供根目录路径。
 */
public abstract class BaseFileBrowserViewModel extends AndroidViewModel {

    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;
    public static final int SORT_ASCENDING = 0;
    public static final int SORT_DESCENDING = 1;

    private final MutableLiveData<List<FileItem>> files = new MutableLiveData<>();
    private final MutableLiveData<File> currentPathLiveData = new MutableLiveData<>();
    private File currentPath;
    private final File rootPath;
    private FileObserver fileObserver;

    private int currentSortMode = SORT_NAME;
    private int currentSortOrder = SORT_ASCENDING;
    private final SharedPreferences prefs;

    private static final String KEY_SORT_MODE = "sort_mode";
    private static final String KEY_SORT_ORDER = "sort_order";

    protected BaseFileBrowserViewModel(@NonNull Application application, String prefsName) {
        super(application);
        prefs = application.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        currentSortMode = prefs.getInt(KEY_SORT_MODE, SORT_NAME);
        currentSortOrder = prefs.getInt(KEY_SORT_ORDER, SORT_ASCENDING);

        rootPath = getRootDirectory(application);
        loadFiles(rootPath);
    }

    /** 子类提供各自的根目录 */
    protected abstract File getRootDirectory(Application application);

    public LiveData<List<FileItem>> getFiles() { return files; }
    public LiveData<File> getCurrentPath() { return currentPathLiveData; }
    public int getCurrentSortMode() { return currentSortMode; }
    public int getCurrentSortOrder() { return currentSortOrder; }
    public File getRootPath() { return rootPath; }

    public void setSortMode(int mode) {
        if (currentSortMode != mode) {
            currentSortMode = mode;
            savePreferences();
            if (currentPath != null) loadFiles(currentPath);
        }
    }

    public void setSortOrder(int order) {
        if (currentSortOrder != order) {
            currentSortOrder = order;
            savePreferences();
            if (currentPath != null) loadFiles(currentPath);
        }
    }

    public void setSort(int mode, int order) {
        boolean changed = false;
        if (currentSortMode != mode) { currentSortMode = mode; changed = true; }
        if (currentSortOrder != order) { currentSortOrder = order; changed = true; }
        if (changed) {
            savePreferences();
            if (currentPath != null) loadFiles(currentPath);
        }
    }

    private void savePreferences() {
        prefs.edit().putInt(KEY_SORT_MODE, currentSortMode)
                   .putInt(KEY_SORT_ORDER, currentSortOrder).apply();
    }

    public void loadFiles(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            files.postValue(Collections.emptyList());
            return;
        }
        new Thread(() -> {
            File[] children = directory.listFiles();
            List<FileItem> itemList = new ArrayList<>();
            if (children != null) {
                for (File file : children) {
                    boolean isDir = file.isDirectory();
                    int iconRes = isDir ? R.drawable.ic_folder : R.drawable.ic_file;
                    long size = isDir ? 0 : file.length();
                    long lastModified = file.lastModified();
                    itemList.add(new FileItem(file.getName(), file.getAbsolutePath(),
                            isDir, iconRes, size, lastModified));
                }
                Collections.sort(itemList, (o1, o2) -> {
                    if (o1.isDirectory() && !o2.isDirectory()) return -1;
                    if (!o1.isDirectory() && o2.isDirectory()) return 1;
                    int cmp;
                    switch (currentSortMode) {
                        case SORT_SIZE:  cmp = Long.compare(o1.getSize(), o2.getSize()); break;
                        case SORT_DATE:  cmp = Long.compare(o1.getLastModified(), o2.getLastModified()); break;
                        default:         cmp = o1.getName().compareToIgnoreCase(o2.getName()); break;
                    }
                    return currentSortOrder == SORT_DESCENDING ? -cmp : cmp;
                });
            }
            files.postValue(itemList);
            currentPath = directory;
            currentPathLiveData.postValue(directory);
            setupFileObserver(directory);
        }).start();
    }

    private void setupFileObserver(File directory) {
        if (fileObserver != null) fileObserver.stopWatching();
        fileObserver = new FileObserver(directory.getAbsolutePath()) {
            @Override
            public void onEvent(int event, String path) {
                int mask = FileObserver.CREATE | FileObserver.DELETE
                         | FileObserver.MOVED_FROM | FileObserver.MOVED_TO;
                if ((event & mask) != 0) loadFiles(directory);
            }
        };
        fileObserver.startWatching();
    }

    public void navigateTo(FileItem item) {
        if (item.isDirectory()) loadFiles(new File(item.getPath()));
    }

    public boolean navigateUp() {
        if (currentPath != null && !currentPath.equals(rootPath)) {
            File parent = currentPath.getParentFile();
            if (parent != null && parent.exists()) {
                loadFiles(parent);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (fileObserver != null) fileObserver.stopWatching();
    }
}
