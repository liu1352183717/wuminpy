package com.wumin.wuminpy.ui.home;

import android.app.Application;
import android.os.Environment;

import androidx.annotation.NonNull;

import com.wumin.wuminpy.ui.BaseFileBrowserViewModel;

import java.io.File;

public class TransformViewModel extends BaseFileBrowserViewModel {

    public TransformViewModel(@NonNull Application application) {
        super(application, "transform_prefs");
    }

    @Override
    protected File getRootDirectory(Application application) {
        File rootDir = new File(Environment.getExternalStorageDirectory(), "PYScripts");
        if (!rootDir.exists()) rootDir.mkdir();
        if (!rootDir.exists()) rootDir = application.getFilesDir();
        return rootDir;
    }
}
