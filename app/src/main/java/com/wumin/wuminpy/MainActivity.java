package com.wumin.wuminpy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;
import com.wumin.ai.api.AINavigation;
import com.wumin.codeeditor.LogActivity;
import com.wumin.codeeditor.PylspServerManager;
import com.wumin.core.AppConfig;
import com.wumin.core.ServiceRegistry;
import com.wumin.core.TerminalNavigation;
import com.wumin.debug.VSCodeDebugServer;
import com.wumin.floating.FloatingWindowService;
import com.wumin.merminal.AbiDetector;
import com.wumin.merminal.TerminalSetupHelper;
import com.wumin.python.PythonRunner;
import com.wumin.screen.ScreenCaptureService;
import com.wumin.wuminpy.databinding.ActivityMainBinding;
import com.wumin.wuminpy.drawer.DrawerAdapter;
import com.wumin.wuminpy.drawer.DrawerDividerItemDecoration;
import com.wumin.wuminpy.drawer.DrawerMenuConfig;
import com.wumin.wuminpy.drawer.DrawerMenuItem;
import com.wumin.wuminpy.git.GitConfigActivity;
import com.wumin.wuminpy.ui.IOnBackPressed;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREF_DRAWER_EXPANDED = "drawer_expanded";
    private static final String PREF_DISCLAIMER_ACCEPTED = "disclaimer_accepted";
    private static final long DOUBLE_BACK_INTERVAL = 2000;

    // ── 抽屉 ──
    private DrawerLayout drawerLayout;
    private DrawerAdapter drawerAdapter;
    private List<DrawerMenuItem> menuItems;
    private DrawerSwitchHandler switchHandler;
    private VSCodeBridge vscodeBridge;
    private PylspServerManager pylspManager;

    // ── 悬浮窗 ──
    private FloatingWindowService.MsgBinder binder;
    private boolean isBound;
    private boolean pendingShowFloat;
    private final ServiceConnection floatConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (FloatingWindowService.MsgBinder) service;
            isBound = true;
            if (pendingShowFloat) {
                binder.showFloatingWindow();
                pendingShowFloat = false;
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            binder = null;
        }
    };

    // ── 录屏 ──
    private MediaProjectionManager mProjectionManager;
    private final ActivityResultLauncher<Intent> screenCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                    serviceIntent.putExtra("resultCode", result.getResultCode());
                    serviceIntent.putExtra("resultData", result.getData());

                    DisplayMetrics metrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                    serviceIntent.putExtra("screenWidth", metrics.widthPixels);
                    serviceIntent.putExtra("screenHeight", metrics.heightPixels);
                    serviceIntent.putExtra("screenDensity", metrics.densityDpi);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    // 🌟 不再在这里通知 VS Code，改由 ScreenCaptureService 在 H264 服务器就绪后通知
                } else {
                    Toast.makeText(this, "已取消投屏", Toast.LENGTH_SHORT).show();
                    vscodeBridge.notifyScreenRejected();
                }
            });

    // ═══════════════════════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.appBarMain.toolbar);

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (savedInstanceState == null) {
            initEnvironment();
            showDisclaimerIfNeeded();
        }
        TerminalSetupHelper.createBashConfig(this);

        // 导航
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        NavController navController = Objects.requireNonNull(navHostFragment).getNavController();
        AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
                R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow,
                R.id.nav_script, R.id.nav_plugin, R.id.nav_settings)
                .setOpenableLayout(binding.drawerLayout).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);
        NavigationUI.setupWithNavController(binding.appBarMain.contentMain.bottomNavView, navController);

        // 抽屉
        drawerLayout = findViewById(R.id.drawer_layout);
        RecyclerView drawerRecycler = findViewById(R.id.drawer_recycler);
        setupDrawer(drawerRecycler, savedInstanceState);
        findViewById(R.id.btn_drawer_exit).setOnClickListener(v -> finishAffinity());

        // 返回键
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            private long lastBackPressTime;
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                Fragment f = getCurrentFragment();
                if (f instanceof IOnBackPressed && ((IOnBackPressed) f).onBackPressed()) return;

                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < DOUBLE_BACK_INTERVAL) {
                    startHomeActivity();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(MainActivity.this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // VS Code 指令桥接
        vscodeBridge = new VSCodeBridge(this);
        vscodeBridge.setup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (switchHandler != null) switchHandler.refreshSwitchStates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveExpandedState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(floatConnection);
            isBound = false;
        }
    }

    // ═══════════════════════════════════════════════════════
    // 抽屉
    // ═══════════════════════════════════════════════════════

    private void setupDrawer(RecyclerView recycler, Bundle savedInstanceState) {
        boolean isColdStart = (savedInstanceState == null);
        Map<Integer, Boolean> expandedMap = isColdStart ? null : loadExpandedState();

        menuItems = DrawerMenuConfig.build(expandedMap);
        switchHandler = new DrawerSwitchHandler(this, drawerAdapter = new DrawerAdapter(menuItems,
                new DrawerAdapter.OnMenuItemClickListener() {
                    @Override public void onParentClick(DrawerMenuItem parent) { handleNavigation(parent.id); }
                    @Override public void onChildClick(DrawerMenuItem child)   { handleNavigation(child.id); }
                    @Override public void onSwitchChanged(DrawerMenuItem item, boolean checked) {
                        switchHandler.onSwitchChanged(item, checked);
                    }
                }), menuItems);

        switchHandler.restoreSwitchState(menuItems, isColdStart);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(drawerAdapter);
        Drawable divider = ContextCompat.getDrawable(this, R.drawable.divider);
        recycler.addItemDecoration(new DrawerDividerItemDecoration(divider, drawerAdapter));
    }

    private void handleNavigation(int itemId) {
        if (itemId == DrawerMenuConfig.ID_NDK_INSTALL)
            startActivity(new Intent(this, NdkSettingActivity.class));
        else if (itemId == DrawerMenuConfig.ID_EXIT)
            finishAffinity();
        else if (itemId == DrawerMenuConfig.ID_LSP_CONFIG)
            startActivity(new Intent(this, com.wumin.codeeditor.LspConfigActivity.class));
        else if (itemId == DrawerMenuConfig.ID_MORE_SETTINGS)
            startActivity(new Intent(this, MoreActivity.class));
        else if (itemId == DrawerMenuConfig.ID_GIT_CONFIG)
            startActivity(new Intent(this, GitConfigActivity.class));
        else if (itemId == DrawerMenuConfig.ID_AI_CONFIG) {
            ServiceRegistry.INSTANCE.get(AINavigation.class).openConfig(this);
        }
        else if (itemId == DrawerMenuConfig.ID_DEV_TUTORIAL) {
            showConnectionTutorial();
        }
        else if (itemId == DrawerMenuConfig.ID_OPENSOURCE) {
            startInfoPage(getString(R.string.opensource_title), getString(R.string.opensource_content));
        }
        else if (itemId == DrawerMenuConfig.ID_PRIVACY) {
            startInfoPage(getString(R.string.privacy_title), getString(R.string.privacy_content));
        }
        else if (itemId == DrawerMenuConfig.ID_SPONSOR) {
            startActivity(new Intent(this, SponsorActivity.class));
        }
    }

    private void startInfoPage(String title, String content) {
        Intent intent = new Intent(this, InfoPageActivity.class);
        intent.putExtra(InfoPageActivity.EXTRA_TITLE, title);
        intent.putExtra(InfoPageActivity.EXTRA_CONTENT, content);
        startActivity(intent);
    }

    private void showDisclaimerIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREF_DISCLAIMER_ACCEPTED, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_DISCLAIMER_ACCEPTED, false)) return;

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer_title))
            .setMessage(getString(R.string.disclaimer_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.disclaimer_agree), (dialog, which) -> {
                prefs.edit().putBoolean(PREF_DISCLAIMER_ACCEPTED, true).apply();
            })
            .setNegativeButton(getString(R.string.disclaimer_disagree), (dialog, which) -> finishAffinity())
            .show();
    }

    private void showConnectionTutorial() {
        String tutorial =
            "═══ VS Code 连接教程 ═══\n\n" +
            "【方式一：USB 数据线连接】\n" +
            "需要电脑安装 ADB 并配置环境变量：\n" +
            "1. 将 adb.exe 所在目录添加到系统 Path\n" +
            "   例如：C:\\adb\n" +
            "2. 终端输入 adb version 验证成功\n\n" +
            "连接步骤：\n" +
            "1. 手机开启 USB 调试\n" +
            "   （设置 → 开发者选项 → USB 调试）\n" +
            "2. 数据线连接手机和电脑，点击允许\n" +
            "3. App 中打开「电脑连接(ADB)」开关\n" +
            "4. VS Code 点左下角「WuminPy 离线」\n" +
            "   → 选择 USB 连接（自动转发端口）\n" +
            "5. 投屏工具栏点击操作模式选 ADB\n\n\n" +
            "【方式二：Wi-Fi 无线连接】\n" +
            "无需 ADB，点击滑动走无障碍手势：\n" +
            "1. 确保手机和电脑在同一 Wi-Fi\n" +
            "2. 先开启「无障碍权限」\n" +
            "3. App 中打开「无线连接(LAN)」\n" +
            "   记下弹出的 IP 和端口\n" +
            "4. VS Code 点左下角「WuminPy 离线」\n" +
            "   → 选择 WIFI，输入 IP:端口\n" +
            "5. 投屏工具栏操作模式选 无障碍\n\n\n" +
            "【连接成功后】\n" +
            "• 状态栏变蓝 = 已连接\n" +
            "• 点击「手机投屏」查看实时画面\n" +
            "• 点击「远程终端」打开手机 Shell\n" +
            "• 代码保存自动同步到手机\n\n" +
            "操作模式说明：\n" +
            "• ADB 模式 → 通过 adb 命令点击滑动\n" +
            "• 无障碍模式 → 通过无障碍服务操作";

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📖 连接教程")
            .setMessage(tutorial)
            .setPositiveButton("知道了", null)
            .show();
    }

    // ═══════════════════════════════════════════════════════
    // 悬浮窗
    // ═══════════════════════════════════════════════════════

    public void showFloat() {
        if (isBound && binder != null) {
            binder.showFloatingWindow();
            return;
        }
        pendingShowFloat = true;
        Intent intent = new Intent(this, FloatingWindowService.class);
        startService(intent);
        bindService(intent, floatConnection, Context.BIND_AUTO_CREATE);
    }

    public void hideFloat() {
        if (isBound && binder != null) binder.hideFloatingWindow();
    }

    // ═══════════════════════════════════════════════════════
    // 录屏
    // ═══════════════════════════════════════════════════════

    public void startScreenCapture() {
        if (mProjectionManager != null) {
            screenCaptureLauncher.launch(mProjectionManager.createScreenCaptureIntent());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 屏幕常亮
    // ═══════════════════════════════════════════════════════

    public void setKeepScreenOn(boolean enable) {
        if (enable) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 工具栏菜单
    // ═══════════════════════════════════════════════════════

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        if (id == R.id.action_btn_1) {
            ServiceRegistry.INSTANCE.get(TerminalNavigation.class).startActivity(this);
            return true;
        }
        if (id == R.id.action_btn_2) {
            startActivity(new Intent(this, LogActivity.class));
            return true;
        }
        if (id == R.id.action_btn_3) {
            Toast.makeText(this, "点击了按钮3", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_btn_4) {
            if (Settings.canDrawOverlays(this)) {
                ServiceRegistry.INSTANCE.get(AINavigation.class).startFloating(this);
            } else {
                XXPermissions.with(this)
                        .permissions(java.util.Arrays.asList(PermissionLists.getSystemAlertWindowPermission()))
                        .request((granted, denied) -> {});
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController nav = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(nav, (DrawerLayout) null) || super.onSupportNavigateUp();
    }

    // ═══════════════════════════════════════════════════════
    // 内部
    // ═══════════════════════════════════════════════════════

    private Fragment getCurrentFragment() {
        NavHostFragment nav = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        if (nav != null) {
            List<Fragment> fragments = nav.getChildFragmentManager().getFragments();
            if (!fragments.isEmpty()) return fragments.get(0);
        }
        return null;
    }

    private void startHomeActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void saveExpandedState() {
        SharedPreferences prefs = getSharedPreferences(PREF_DRAWER_EXPANDED, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (DrawerMenuItem item : menuItems) {
            if (item.type == DrawerMenuItem.TYPE_PARENT) {
                editor.putBoolean(String.valueOf(item.id), item.isExpanded);
            }
        }
        editor.apply();
    }

    private Map<Integer, Boolean> loadExpandedState() {
        SharedPreferences prefs = getSharedPreferences(PREF_DRAWER_EXPANDED, MODE_PRIVATE);
        Map<Integer, Boolean> map = new HashMap<>();
        for (int id : DrawerMenuConfig.getParentIds()) {
            map.put(id, prefs.getBoolean(String.valueOf(id), false));
        }
        return map;
    }

    private void initEnvironment() {
        File homeDir = new File(getFilesDir(), "home");
        if (!homeDir.exists()) {
            TerminalSetupHelper.createDirectoryStructure(this);
            String abi = AbiDetector.getCurrentAbi();
            TerminalSetupHelper.copyAllBinariesFromAssets(this, abi);
            TerminalSetupHelper.unzipCoreutils(this);
            TerminalSetupHelper.createBashConfig(this);
            TerminalSetupHelper.createInputConfig(this);
            TerminalSetupHelper.createVimConfig(this);
            TerminalSetupHelper.setupTerminfo(this);
            TerminalSetupHelper.copyPythonLibraries(this);
            TerminalSetupHelper.creatSHTest(this);
            TerminalSetupHelper.setupBusyboxLinks(this);
            TerminalSetupHelper.copyAssetsDirectory(this, "cacert", "usr/cacert");
            TerminalSetupHelper.copyAssetsDirectory(this, "share", "usr/share");
        }

        String abi = AbiDetector.getCurrentAbi();
        TerminalSetupHelper.copyAllBinariesFromAssets(this, abi);
        TerminalSetupHelper.copyAssetsDirectory(this, "example", "usr/example");
        TerminalSetupHelper.copyAssetsDirectory(this, "python/lib/python3.14/wumin", "usr/lib/python3.14/wumin");
        TerminalSetupHelper.copyAssetsDirectory(this, "python/lib/python3.14/wumin_auto", "usr/lib/python3.14/wumin_auto");
        TerminalSetupHelper.copyAssetsDirectory(this, "ai_tools", "ai_tools");
        TerminalSetupHelper.copyAssetsDirectory(this, "skills", "skills");

        PythonRunner.ensureInitialized(App.getInstance());
        // Always create and register — start/stop is controlled by LSP config page switch
        pylspManager = new PylspServerManager(this);
        ServiceRegistry.INSTANCE.register(com.wumin.codeeditor.PylspServerManager.class, pylspManager);
        if (AppConfig.INSTANCE.getLspEnabled()) {
            pylspManager.start();
        }
    }
}
