package com.wumin.wuminpy.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wumin.wuminpy.R;
import com.wumin.wuminpy.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilePickerDialogFragment extends DialogFragment {

    private static final String ARG_START_PATH = "start_path";

    private FilePickerViewModel viewModel;
    private TextView tvPath;
    private RecyclerView recyclerView;
    private Button btnCancel, btnSelect;
    private File selectedFile;

    public interface OnFileSelectedListener {
        void onFileSelected(File file);
    }

    private OnFileSelectedListener listener;

    public static FilePickerDialogFragment newInstance(String startPath) {
        Bundle args = new Bundle();
        args.putString(ARG_START_PATH, startPath);
        FilePickerDialogFragment fragment = new FilePickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnFileSelectedListener(OnFileSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_file_picker, container, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // 使用 ComponentDialog 或普通的 Dialog
        return new Dialog(requireContext(), getTheme()) {
            @Override
            public void onBackPressed() {
                // 1. 获取当前路径和根路径
                File cur = viewModel.getCurrentPath().getValue();
                String startPath = getArguments() != null ? getArguments().getString(ARG_START_PATH) : "/storage/emulated/0";
                File root = new File(startPath);

                // 2. 逻辑判断
                if (cur != null && !cur.equals(root)) {
                    // 如果不是根目录，执行返回上一级
                    viewModel.navigateUp();
                    if (btnSelect != null) btnSelect.setEnabled(false);
                    selectedFile = null;
                    Log.d("FilePicker", "Navigate Up to: " + viewModel.getCurrentPath().getValue());
                } else {
                    // 如果是根目录，调用 super 执行默认的 dismiss()
                    super.onBackPressed();
                }
            }
        };
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvPath = view.findViewById(R.id.tvPath);
        recyclerView = view.findViewById(R.id.recyclerView);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnSelect = view.findViewById(R.id.btnSelect);

        btnSelect.setEnabled(false);

        viewModel = new ViewModelProvider(this).get(FilePickerViewModel.class);

        String startPath = getArguments().getString(ARG_START_PATH);
        if (startPath == null) {
            startPath = "/storage/emulated/0";
        }
        File root = new File(startPath);
        viewModel.setRootDir(root);

        FilePickerAdapter adapter = new FilePickerAdapter(new ArrayList<>(), item -> {
            if (item.isDirectory()) {
                viewModel.navigateTo(item);
                btnSelect.setEnabled(false); // 进入文件夹后禁用选择按钮
                selectedFile = null;
            } else {
                selectedFile = new File(item.getPath());
                btnSelect.setEnabled(true);
                // 可以高亮选中的项
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel.getFiles().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getCurrentPath().observe(getViewLifecycleOwner(), file -> tvPath.setText(file.getAbsolutePath()));

        btnCancel.setOnClickListener(v -> dismiss());
        btnSelect.setOnClickListener(v -> {
            if (selectedFile != null && listener != null) {
                listener.onFileSelected(selectedFile);
            }
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            // 获取屏幕宽度
            DisplayMetrics displayMetrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;
            // 设置宽度为屏幕宽度的90%，高度自适应
            int width = (int) (screenWidth * 0.8);
            int height = (int) (screenHeight * 0.8);

            dialog.getWindow().setLayout(width, height);
        }
    }

    // ---------- 适配器 ----------
    private static class FilePickerAdapter extends RecyclerView.Adapter<FilePickerAdapter.ViewHolder> {

        private List<FileItem> list = new ArrayList<>();
        private final OnItemClickListener listener;
        private int selectedPosition = -1; // 当前选中的位置，-1表示无选中

        interface OnItemClickListener {
            void onItemClick(FileItem item);
        }

        FilePickerAdapter(List<FileItem> list, OnItemClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void submitList(List<FileItem> newList) {
            list = newList;
            selectedPosition = -1; // 数据更新时重置选中
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_picker, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileItem item = list.get(position);
            holder.icon.setImageResource(item.getIconRes());
            holder.name.setText(item.getName());

            // 设置选中效果：如果当前项是选中的，添加背景色，否则恢复默认
            if (position == selectedPosition) {
                holder.itemView.setBackgroundColor(holder.itemView.getContext().getColor(R.color.selected_item_bg)); // 需要定义颜色
            } else {
                holder.itemView.setBackgroundColor(holder.itemView.getContext().getColor(android.R.color.transparent));
            }

            holder.itemView.setOnClickListener(v -> {
                // 先通知外部点击事件（更新 selectedFile 和按钮状态）
                listener.onItemClick(item);

                if (!item.isDirectory()) {
                    // 点击的是文件：更新选中位置
                    int previousSelected = selectedPosition;
                    selectedPosition = holder.getAdapterPosition();
                    // 刷新之前选中的项和当前项
                    if (previousSelected != -1 && previousSelected != selectedPosition) {
                        notifyItemChanged(previousSelected);
                    }
                    notifyItemChanged(selectedPosition);
                } else {
                    // 点击文件夹：如果有选中的文件，清除选中状态
                    if (selectedPosition != -1) {
                        int oldPos = selectedPosition;
                        selectedPosition = -1;
                        notifyItemChanged(oldPos);
                    }
                }
            });


        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;

            ViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.icon);
                name = v.findViewById(R.id.name);
            }
        }
    }
}
