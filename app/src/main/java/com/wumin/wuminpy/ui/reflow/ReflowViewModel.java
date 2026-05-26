package com.wumin.wuminpy.ui.reflow;

import android.app.Application;

import androidx.annotation.NonNull;

import com.wumin.wuminpy.ui.BaseFileBrowserViewModel;

import java.io.File;

public class ReflowViewModel extends BaseFileBrowserViewModel {

    public ReflowViewModel(@NonNull Application application) {
        super(application, "reflow_prefs");
    }

    @Override
    protected File getRootDirectory(Application application) {
        return application.getFilesDir().getParentFile();
    }
}
