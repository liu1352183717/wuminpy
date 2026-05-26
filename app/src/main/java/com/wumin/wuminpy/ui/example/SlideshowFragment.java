package com.wumin.wuminpy.ui.example;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.wumin.codeeditor.EditorActivity;
import com.wumin.wuminpy.R;
import com.wumin.wuminpy.model.FileItem;
import com.wumin.wuminpy.ui.BaseFileBrowserFragment;
import com.wumin.wuminpy.ui.BaseFileBrowserViewModel;

public class SlideshowFragment extends BaseFileBrowserFragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_slideshow, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init(view);
    }

    @Override
    protected BaseFileBrowserViewModel createViewModel() {
        return new ViewModelProvider(requireActivity(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication()))
                .get(SlideshowViewModel.class);
    }

    @Override
    protected int getItemLayoutResId() {
        return R.layout.item_slideshow;
    }

    @Override
    protected void onFileClicked(FileItem item) {
        if (item.getName().endsWith(".py")) {
            Intent intent = new Intent(requireContext(), EditorActivity.class);
            intent.putExtra("file_path", item.getPath());
            startActivity(intent);
        } else {
            super.onFileClicked(item);
        }
    }
}
