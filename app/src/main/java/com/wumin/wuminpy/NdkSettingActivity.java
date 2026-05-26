package com.wumin.wuminpy;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wumin.wuminpy.dialog.FilePickerDialogFragment;
import com.wumin.wuminpy.util.ToolExtractTask; // 原 BusyboxExtractTask 改名（可选）
import com.wumin.core.util.ConfigManager;
import com.wumin.wuminpy.util.NdkDetector;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.wumin.merminal.TerminalSetupHelper;

public class NdkSettingActivity extends AppCompatActivity {

    private static final String TAG = "NdkSetting";

    // UI 组件
    private TextInputEditText ndkPathEdit;
    private TextInputEditText ccEdit, cxxEdit, arEdit, ranlibEdit, stripEdit, sysrootEdit, cflagsEdit;
    private Spinner abiSpinner, apiSpinner;
    private ProgressBar progressBar;
    private TextView progressText;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private String toolDir; // 自编译工具目录，原 busyboxDir

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ndk_setting);

        // 设置工具目录（根据你的实际存放位置修改）
        toolDir = new File(getFilesDir(), "usr/bin").getAbsolutePath();

        initViews();
        setupListeners();
        loadSavedConfig();
    }

    private void initViews() {
        ndkPathEdit = findViewById(R.id.ndk_path);

        ccEdit = findViewById(R.id.cc_edit);
        cxxEdit = findViewById(R.id.cxx_edit);
        arEdit = findViewById(R.id.ar_edit);
        ranlibEdit = findViewById(R.id.ranlib_edit);
        stripEdit = findViewById(R.id.strip_edit);
        sysrootEdit = findViewById(R.id.sysroot_edit);
        cflagsEdit = findViewById(R.id.cflags_edit);

        abiSpinner = findViewById(R.id.abi_spinner);
        apiSpinner = findViewById(R.id.api_spinner);

        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);

        ArrayAdapter<CharSequence> abiAdapter = ArrayAdapter.createFromResource(this,
                R.array.ndk_abis, android.R.layout.simple_spinner_item);
        abiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        abiSpinner.setAdapter(abiAdapter);

        ArrayAdapter<CharSequence> apiAdapter = ArrayAdapter.createFromResource(this,
                R.array.ndk_apis, android.R.layout.simple_spinner_item);
        apiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        apiSpinner.setAdapter(apiAdapter);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCflagsExample();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        abiSpinner.setOnItemSelectedListener(listener);
        apiSpinner.setOnItemSelectedListener(listener);
    }

    private void setupListeners() {
        ((TextInputLayout) findViewById(R.id.ndk_textInput)).setEndIconOnClickListener(v -> showFilePicker());
        Button saveBtn = findViewById(R.id.ndk_env_save);
        saveBtn.setOnClickListener(v -> saveCurrentConfig());
    }

    private void showFilePicker() {
        FilePickerDialogFragment dialog = FilePickerDialogFragment.newInstance(
                Environment.getExternalStorageDirectory().getAbsolutePath());
        dialog.setOnFileSelectedListener(file -> {
            ndkPathEdit.setText(file.getAbsolutePath());
            startNdkProcess(file);
        });
        dialog.show(getSupportFragmentManager(), "file_picker");
    }

    private void startNdkProcess(File archiveFile) {
        showLoading("正在检测压缩包...");
        executor.execute(() -> {
            NdkDetector.NdkArchiveInfo info = NdkDetector.inspectArchive(this, archiveFile, toolDir);
            mainHandler.post(() -> {
                hideLoading();
                if (!info.isValid) {
                    Toast.makeText(this, "不是有效的 NDK 压缩包", Toast.LENGTH_LONG).show();
                    return;
                }

                File baseDir = prepareBaseNdkDir();
                if (baseDir == null) {
                    Toast.makeText(this, "创建目标目录失败", Toast.LENGTH_LONG).show();
                    return;
                }

                String topLevelDir = info.topLevelDir;
                showLoading("正在解压压缩包...");
                performExtract(archiveFile, baseDir, topLevelDir);
            });
        });
    }

    private File prepareBaseNdkDir() {
        File homeDir = new File(getFilesDir(), "home");
        if (!homeDir.exists() && !homeDir.mkdirs()) return null;
        File ndkDir = new File(homeDir, "ndk");
        if (!ndkDir.exists() && !ndkDir.mkdirs()) return null;
        return ndkDir;
    }

    private void performExtract(File archiveFile, File targetDir, String topLevelDir) {
        new ToolExtractTask(this, archiveFile, targetDir, toolDir,
                line -> mainHandler.post(() -> progressText.setText("正在解压: " + line)),
                (success, message, extractedDir) -> mainHandler.post(() -> {
                    hideLoading();
                    if (success) {
                        File ndkRoot;
                        if (topLevelDir != null && !topLevelDir.isEmpty()) {
                            ndkRoot = new File(extractedDir, topLevelDir);
                            if (!ndkRoot.exists() || !ndkRoot.isDirectory()) {
                                Log.w(TAG, "预期目录 " + ndkRoot + " 不存在，回退到 normalize");
                                ndkRoot = normalizeExtractedDir(extractedDir);
                            }
                        } else {
                            ndkRoot = normalizeExtractedDir(extractedDir);
                        }
                        validateNdkAndFillEnv(ndkRoot);
                    } else {
                        Toast.makeText(NdkSettingActivity.this, "解压失败: " + message, Toast.LENGTH_LONG).show();
                        deleteDir(extractedDir);
                    }
                })).execute();
    }

    private void validateNdkAndFillEnv(File ndkDir) {
        if (!isNdkRoot(ndkDir)) {
            Toast.makeText(this, "解压后的目录不是有效的 NDK", Toast.LENGTH_LONG).show();
            deleteDir(ndkDir);
            return;
        }

        String ccPath = findToolchainPath(ndkDir, "clang");
        String cxxPath = findToolchainPath(ndkDir, "clang++");
        String arPath = findToolchainPath(ndkDir, "llvm-ar");
        String stripPath = findToolchainPath(ndkDir, "llvm-strip");
        String ranlibPath = findToolchainPath(ndkDir, "llvm-ranlib");
        if (ccPath == null) ccPath = findToolchainPath(ndkDir, "gcc");
        if (arPath == null) arPath = findToolchainPath(ndkDir, "ar");

        String sysroot = findSysroot(ndkDir);

        String abi = abiSpinner.getSelectedItem().toString();
        int api = Integer.parseInt(apiSpinner.getSelectedItem().toString());

        String target = generateTarget(abi, api);
        String cflags = (target != null && sysroot != null) ?
                "-target " + target + " --sysroot=" + sysroot + " -fPIC" : "";
        String ldflags = (target != null && sysroot != null) ?
                "-L" + sysroot + "/usr/lib/" + target.substring(0, target.lastIndexOf('-')) : "";

        ndkPathEdit.setText(ndkDir.getAbsolutePath());
        setTextIfNotNull(ccEdit, ccPath);
        setTextIfNotNull(cxxEdit, cxxPath);
        setTextIfNotNull(arEdit, arPath);
        setTextIfNotNull(stripEdit, stripPath);
        setTextIfNotNull(ranlibEdit, ranlibPath);
        setTextIfNotNull(sysrootEdit, sysroot);
        cflagsEdit.setText(cflags);

        Toast.makeText(this, "NDK 解析成功，请检查环境变量", Toast.LENGTH_SHORT).show();
    }

    private void setTextIfNotNull(TextInputEditText edit, String text) {
        if (text != null) edit.setText(text);
    }

    // ========== NDK 工具方法 ==========
    public String findToolchainPath(File dir, String toolName) {
        File prebuilt = new File(dir, "toolchains/llvm/prebuilt");
        if (prebuilt.exists() && prebuilt.isDirectory()) {
            File[] hosts = prebuilt.listFiles();
            if (hosts != null) {
                for (File host : hosts) {
                    File bin = new File(host, "bin");
                    if (bin.exists()) {
                        File tool = new File(bin, toolName);
                        if (tool.exists()) return tool.getAbsolutePath();
                    }
                }
            }
        }
        File[] dirs = dir.listFiles();
        if (dirs != null) {
            for (File sub : dirs) {
                if (sub.getName().startsWith("toolchains")) {
                    File[] versions = sub.listFiles();
                    if (versions != null) {
                        for (File ver : versions) {
                            File prebuilt2 = new File(ver, "prebuilt");
                            if (prebuilt2.exists()) {
                                File[] hosts = prebuilt2.listFiles();
                                if (hosts != null) {
                                    for (File host : hosts) {
                                        File bin = new File(host, "bin");
                                        if (bin.exists()) {
                                            File tool = new File(bin, toolName);
                                            if (tool.exists()) return tool.getAbsolutePath();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String findSysroot(File ndkDir) {
        File prebuilt = new File(ndkDir, "toolchains/llvm/prebuilt");
        if (prebuilt.exists()) {
            File[] hosts = prebuilt.listFiles();
            if (hosts != null) {
                for (File host : hosts) {
                    File sysroot = new File(host, "sysroot");
                    if (sysroot.exists() && sysroot.isDirectory()) return sysroot.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private String generateTarget(String abi, int api) {
        switch (abi) {
            case "arm64-v8a":
                return "aarch64-linux-android" + api;
            case "armeabi-v7a":
                return "armv7a-linux-androideabi" + api;
            case "x86_64":
                return "x86_64-linux-android" + api;
            case "x86":
                return "i686-linux-android" + api;
            default:
                return null;
        }
    }

    private void updateCflagsExample() {
        String ndkDirStr = ndkPathEdit.getText().toString();
        if (ndkDirStr.isEmpty()) return;
        File ndkDir = new File(ndkDirStr);
        if (!ndkDir.exists()) return;

        String sysroot = findSysroot(ndkDir);
        String abi = abiSpinner.getSelectedItem().toString();
        int api = Integer.parseInt(apiSpinner.getSelectedItem().toString());
        String target = generateTarget(abi, api);
        if (target != null && sysroot != null) {
            cflagsEdit.setText("-target " + target + " --sysroot=" + sysroot + " -fPIC");
        }
    }

    // ========== 配置保存与加载 ==========
    private void saveCurrentConfig() {
        ConfigManager configManager = new ConfigManager(this, "env");
        configManager.putString("NDK", ndkPathEdit.getText().toString());
        configManager.putString("CC", ccEdit.getText().toString());
        configManager.putString("CXX", cxxEdit.getText().toString());
        configManager.putString("AR", arEdit.getText().toString());
        configManager.putString("RANLIB", ranlibEdit.getText().toString());
        configManager.putString("STRIP", stripEdit.getText().toString());
        configManager.putString("SYSROOT", sysrootEdit.getText().toString());
        configManager.putString("CFLAGS", cflagsEdit.getText().toString());
        configManager.putString("ABI", abiSpinner.getSelectedItem().toString());
        configManager.putString("API", apiSpinner.getSelectedItem().toString());

        TerminalSetupHelper.updateNdkConfigInBashrc(this);
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void loadSavedConfig() {
        ConfigManager configManager = new ConfigManager(this, "env");
        setTextIfNotNull(ndkPathEdit, configManager.getString("NDK", ""));
        setTextIfNotNull(ccEdit, configManager.getString("CC", ""));
        setTextIfNotNull(cxxEdit, configManager.getString("CXX", ""));
        setTextIfNotNull(arEdit, configManager.getString("AR", ""));
        setTextIfNotNull(ranlibEdit, configManager.getString("RANLIB", ""));
        setTextIfNotNull(stripEdit, configManager.getString("STRIP", ""));
        setTextIfNotNull(sysrootEdit, configManager.getString("SYSROOT", ""));
        setTextIfNotNull(cflagsEdit, configManager.getString("CFLAGS", ""));

        String savedAbi = configManager.getString("ABI", "arm64-v8a");
        String savedApi = configManager.getString("API", "24");
        setSpinnerSelection(abiSpinner, savedAbi);
        setSpinnerSelection(apiSpinner, savedApi);
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    // ========== UI 辅助 ==========
    private void showLoading(String msg) {
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        progressText.setText(msg);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
    }

    private File normalizeExtractedDir(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return dir;
        File[] children = dir.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) {
            File onlyChild = children[0];
            if (isNdkRoot(onlyChild)) return onlyChild;
        }
        return dir;
    }

    private boolean isNdkRoot(File dir) {
        File toolchains = new File(dir, "toolchains");
        File ndkBuild = new File(dir, "ndk-build");
        File meta = new File(dir, "meta");
        return (toolchains.exists() && toolchains.isDirectory()) ||
                ndkBuild.exists() || meta.exists();
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDir(child);
            }
        }
        dir.delete();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}