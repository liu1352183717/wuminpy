package com.wumin.wuminpy.ui.example;

import android.app.Application;

import androidx.annotation.NonNull;

import com.wumin.wuminpy.ui.BaseFileBrowserViewModel;

import java.io.File;

public class SlideshowViewModel extends BaseFileBrowserViewModel {

    public SlideshowViewModel(@NonNull Application application) {
        super(application, "slideshow_prefs");
    }

    @Override
    protected File getRootDirectory(Application application) {
        return new File(application.getFilesDir(), "usr/example");
    }


}
