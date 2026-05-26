package com.wumin.wuminpy.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wumin.wuminpy.R;
import com.wumin.wuminpy.model.FileItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 文件浏览器 Fragment 基类。
 * 子类在 onViewCreated 中 inflate 各自的布局，再调用 {@link #init(View)}。
 */
public abstract class BaseFileBrowserFragment extends Fragment implements IOnBackPressed {

    protected BaseFileBrowserViewModel viewModel;
    private RecyclerView recyclerView;
    private FloatingActionButton fabMain, fabNewFile, fabNewFolder;
    private View layoutSubFabs;
    private TextView textViewCurrentPath;
    private boolean isFabOpen = false;
    private boolean needScrollToTop = false;

    protected abstract BaseFileBrowserViewModel createViewModel();
    protected abstract int getItemLayoutResId();

    protected void onFileClicked(FileItem item) {
        Toast.makeText(getContext(), "Clicked: " + item.getName(), Toast.LENGTH_SHORT).show();
    }

    protected void init(View rootView) {
        viewModel = createViewModel();

        // ---- 当前路径显示 ----
        textViewCurrentPath = rootView.findViewById(R.id.textViewCurrentPath);
        if (textViewCurrentPath != null) {
            viewModel.getCurrentPath().observe(getViewLifecycleOwner(), path -> {
                if (path != null) textViewCurrentPath.setText(path.getAbsolutePath());
            });
        }

        // ---- RecyclerView ----
        recyclerView = rootView.findViewById(R.id.recyclerview_transform);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        FileItemAdapter adapter = new FileItemAdapter(item -> {
            if (item.isDirectory()) viewModel.navigateTo(item);
            else onFileClicked(item);
        }, this);
        recyclerView.setAdapter(adapter);

        viewModel.getFiles().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            if (needScrollToTop) {
                recyclerView.post(() -> { recyclerView.scrollToPosition(0); needScrollToTop = false; });
            }
        });

        // ---- 下拉刷新 ----
        SwipeRefreshLayout swipeRefresh = rootView.findViewById(R.id.swipeRefreshLayout);
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                File current = viewModel.getCurrentPath().getValue();
                if (current != null) viewModel.loadFiles(current);
                swipeRefresh.setRefreshing(false);
            });
        }

        // ---- 排序按钮 ----
        View buttonSort = rootView.findViewById(R.id.buttonSort);
        if (buttonSort != null) {
            buttonSort.setOnClickListener(v -> showSortMenu(v));
        }

        // ---- FAB ----
        fabMain = rootView.findViewById(R.id.fab_main);
        fabNewFolder = rootView.findViewById(R.id.fab_new_folder);
        fabNewFile = rootView.findViewById(R.id.fab_new_file);
        layoutSubFabs = rootView.findViewById(R.id.layout_sub_fabs);
        if (fabMain != null) setupFAB();
    }

    @Override
    public boolean onBackPressed() {
        File current = viewModel.getCurrentPath().getValue();
        File root = viewModel.getRootPath();
        if (current != null && !current.equals(root)) {
            viewModel.navigateUp();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isFabOpen) closeFABMenu();
    }

    // ======== FAB 菜单 ========

    private void setupFAB() {
        fabMain.setOnClickListener(v -> {
            if (isFabOpen) closeFABMenu();
            else openFABMenu();
        });
        if (fabNewFile != null) fabNewFile.setOnClickListener(v -> {
            closeFABMenu();
            showCreateDialog(false);
        });
        if (fabNewFolder != null) fabNewFolder.setOnClickListener(v -> {
            closeFABMenu();
            showCreateDialog(true);
        });
    }

    private void openFABMenu() {
        isFabOpen = true;
        if (layoutSubFabs != null) layoutSubFabs.setVisibility(View.VISIBLE);
        fabMain.animate().rotation(135).setDuration(200).start();
        if (fabNewFile != null) {
            fabNewFile.setVisibility(View.VISIBLE);
            fabNewFile.animate().alpha(1).setDuration(200).start();
        }
        if (fabNewFolder != null) {
            fabNewFolder.setVisibility(View.VISIBLE);
            fabNewFolder.animate().alpha(1).setDuration(200).start();
        }
    }

    private void closeFABMenu() {
        isFabOpen = false;
        fabMain.animate().rotation(0).setDuration(200).start();
        if (fabNewFile != null) {
            fabNewFile.animate().alpha(0).setDuration(200).start();
        }
        if (fabNewFolder != null) {
            fabNewFolder.animate().alpha(0).setDuration(200).start();
        }
        fabMain.postDelayed(() -> {
            if (layoutSubFabs != null) layoutSubFabs.setVisibility(View.GONE);
            if (fabNewFile != null) fabNewFile.setVisibility(View.GONE);
            if (fabNewFolder != null) fabNewFolder.setVisibility(View.GONE);
        }, 200);
    }

    private void showCreateDialog(boolean isFolder) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_file, null);
        EditText input = dialogView.findViewById(R.id.editFileName);
        String title = isFolder ? "新建文件夹" : "新建文件";
        new AlertDialog.Builder(requireContext()).setTitle(title).setView(dialogView)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File current = viewModel.getCurrentPath().getValue();
                    if (current == null) return;
                    if (isFolder) {
                        if (new File(current, name).mkdir()) {
                            Toast.makeText(requireContext(), "文件夹创建成功", Toast.LENGTH_SHORT).show();
                            viewModel.loadFiles(current);
                        } else {
                            Toast.makeText(requireContext(), "创建失败，可能已存在", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            if (new File(current, name).createNewFile()) {
                                Toast.makeText(requireContext(), "文件创建成功", Toast.LENGTH_SHORT).show();
                                viewModel.loadFiles(current);
                            } else {
                                Toast.makeText(requireContext(), "文件已存在", Toast.LENGTH_SHORT).show();
                            }
                        } catch (IOException e) {
                            Toast.makeText(requireContext(), "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("取消", null).show();
    }

    // ======== 排序菜单（带勾选标识） ========

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();

        String nameLabel = "按名称";
        String sizeLabel = "按大小";
        String dateLabel = "按日期";
        int checked = viewModel.getCurrentSortMode();
        if (checked == BaseFileBrowserViewModel.SORT_NAME) nameLabel = "✓ " + nameLabel;
        else if (checked == BaseFileBrowserViewModel.SORT_SIZE) sizeLabel = "✓ " + sizeLabel;
        else dateLabel = "✓ " + dateLabel;

        menu.add(nameLabel).setOnMenuItemClickListener(i -> {
            viewModel.setSort(BaseFileBrowserViewModel.SORT_NAME, viewModel.getCurrentSortOrder());
            return true;
        });
        menu.add(sizeLabel).setOnMenuItemClickListener(i -> {
            viewModel.setSort(BaseFileBrowserViewModel.SORT_SIZE, viewModel.getCurrentSortOrder());
            return true;
        });
        menu.add(dateLabel).setOnMenuItemClickListener(i -> {
            viewModel.setSort(BaseFileBrowserViewModel.SORT_DATE, viewModel.getCurrentSortOrder());
            return true;
        });

        boolean isAsc = viewModel.getCurrentSortOrder() == BaseFileBrowserViewModel.SORT_ASCENDING;
        menu.add(isAsc ? "切换为降序 ↓" : "切换为升序 ↑").setOnMenuItemClickListener(i -> {
            viewModel.setSortOrder(isAsc ? BaseFileBrowserViewModel.SORT_DESCENDING : BaseFileBrowserViewModel.SORT_ASCENDING);
            return true;
        });
        popup.show();
    }

    // ======== Adapter + ViewHolder ========

    private interface OnItemClickListener { void onItemClick(FileItem item); }

    private class FileItemAdapter extends ListAdapter<FileItem, FileItemAdapter.ViewHolder> {
        private final OnItemClickListener listener;
        private final Fragment fragment;

        FileItemAdapter(OnItemClickListener listener, Fragment fragment) {
            super(new DiffUtil.ItemCallback<FileItem>() {
                @Override public boolean areItemsTheSame(@NonNull FileItem a, @NonNull FileItem b) { return a.getPath().equals(b.getPath()); }
                @Override public boolean areContentsTheSame(@NonNull FileItem a, @NonNull FileItem b) {
                    return a.getPath().equals(b.getPath()) && a.getName().equals(b.getName()) && a.isDirectory() == b.isDirectory();
                }
            });
            this.listener = listener;
            this.fragment = fragment;
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(getItemLayoutResId(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(getItem(position));
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView icon;
            private final TextView name, sizeText, timeText;
            private final View runButton, settingsButton;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.image_view_item_transform);
                name = itemView.findViewById(R.id.text_view_item_transform_name);
                sizeText = itemView.findViewById(R.id.text_view_item_transform_size);
                timeText = itemView.findViewById(R.id.text_view_item_transform_time);
                runButton = itemView.findViewById(R.id.button_item_run);
                settingsButton = itemView.findViewById(R.id.button_item_settings);
            }

            void bind(FileItem item) {
                name.setText(item.getName());
                sizeText.setText(item.isDirectory() ? "" : formatFileSize(item.getSize()));
                timeText.setText(formatDate(item.getLastModified()));
                try {
                    icon.setImageDrawable(ResourcesCompat.getDrawable(fragment.getResources(), item.getIconRes(), null));
                } catch (Exception e) {
                    icon.setImageResource(R.drawable.ic_file);
                }
                itemView.setOnClickListener(v -> listener.onItemClick(item));
                if (runButton != null) runButton.setOnClickListener(v -> {
                    if (item.getName().endsWith(".py")) listener.onItemClick(item);
                    else openFile(item);
                });
                if (settingsButton != null) settingsButton.setOnClickListener(v -> showActionMenu(item));
            }

            private void showActionMenu(FileItem item) {
                PopupMenu popup = new PopupMenu(fragment.requireContext(), settingsButton);
                popup.getMenu().add("重命名").setOnMenuItemClickListener(mi -> { showRenameDialog(item); return true; });
                popup.getMenu().add("删除").setOnMenuItemClickListener(mi -> { showDeleteDialog(item); return true; });
                popup.show();
            }

            private void showRenameDialog(FileItem item) {
                View dv = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.dialog_create_file, null);
                EditText input = dv.findViewById(R.id.editFileName);
                input.setText(item.getName());
                new AlertDialog.Builder(fragment.requireContext()).setTitle("重命名").setView(dv)
                        .setPositiveButton("确定", (d, w) -> {
                            String newName = input.getText().toString().trim();
                            if (newName.isEmpty() || newName.equals(item.getName())) return;
                            if (new File(item.getPath()).renameTo(new File(new File(item.getPath()).getParent(), newName))) {
                                Toast.makeText(fragment.requireContext(), "重命名成功", Toast.LENGTH_SHORT).show();
                                refreshFileList();
                            } else {
                                Toast.makeText(fragment.requireContext(), "重命名失败", Toast.LENGTH_SHORT).show();
                            }
                        }).setNegativeButton("取消", null).show();
            }

            private void showDeleteDialog(FileItem item) {
                new AlertDialog.Builder(fragment.requireContext()).setTitle("删除")
                        .setMessage("确定删除 " + item.getName() + " 吗？")
                        .setPositiveButton("确定", (d, w) -> {
                            File file = new File(item.getPath());
                            boolean deleted = file.isDirectory() ? deleteRecursive(file) : file.delete();
                            Toast.makeText(fragment.requireContext(), deleted ? "删除成功" : "删除失败", Toast.LENGTH_SHORT).show();
                            if (deleted) refreshFileList();
                        }).setNegativeButton("取消", null).show();
            }

            private boolean deleteRecursive(File fileOrDirectory) {
                if (fileOrDirectory.isDirectory()) {
                    Path path = fileOrDirectory.toPath();
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                        for (Path entry : stream) deleteRecursive(entry.toFile());
                    } catch (IOException e) { return false; }
                }
                return fileOrDirectory.delete();
            }

            private void refreshFileList() {
                File currentPath = viewModel.getCurrentPath().getValue();
                if (currentPath != null) viewModel.loadFiles(currentPath);
            }

            private void openFile(FileItem item) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = FileProvider.getUriForFile(fragment.requireContext(),
                        fragment.requireContext().getPackageName() + ".fileprovider", new File(item.getPath()));
                intent.setDataAndType(uri, getMimeType(item.getPath()));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    fragment.startActivity(Intent.createChooser(intent, "选择应用打开"));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(fragment.requireContext(), "没有应用可以打开此文件", Toast.LENGTH_SHORT).show();
                }
            }

            private String getMimeType(String path) {
                String ext = MimeTypeMap.getFileExtensionFromUrl(path);
                return ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) : "*/*";
            }

            private String formatFileSize(long size) {
                if (size <= 0) return "0 B";
                String[] units = {"B", "KB", "MB", "GB", "TB"};
                int g = (int) (Math.log10(size) / Math.log10(1024));
                return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024, g), units[g]);
            }

            private String formatDate(long ts) {
                if (ts <= 0) return "";
                return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(ts));
            }
        }
    }
}
