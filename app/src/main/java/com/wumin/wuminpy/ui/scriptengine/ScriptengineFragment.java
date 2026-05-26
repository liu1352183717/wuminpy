package com.wumin.wuminpy.ui.scriptengine;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.wumin.core.ServiceRegistry;
import com.wumin.core.ScriptRuntime;
import com.wumin.core.script.ScriptInfo;
import com.wumin.wuminpy.R;

import java.util.ArrayList;
import java.util.List;

public class ScriptengineFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ScriptListAdapter adapter;
    private ScriptRuntime scriptRuntime;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scriptengine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerview_transform);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ScriptListAdapter();
        recyclerView.setAdapter(adapter);
        scriptRuntime = ServiceRegistry.INSTANCE.get(ScriptRuntime.class);

        // 下拉刷新：重新加载脚本列表
        swipeRefresh.setOnRefreshListener(() -> {
            scriptRuntime.refreshScriptList();
            swipeRefresh.setRefreshing(false);
        });

        // 观察脚本列表变化
        adapter.bindRuntime(scriptRuntime);
        scriptRuntime.getScriptListLive().observe(getViewLifecycleOwner(), scripts -> {
                adapter.setData(scripts);
                if (scripts == null || scripts.isEmpty()) {
                    adapter.setEmpty(true);
                } else {
                    adapter.setEmpty(false);
                }
            });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) adapter.startAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) adapter.stopAutoRefresh();
    }

    // ---------- Adapter ----------
    static class ScriptListAdapter extends RecyclerView.Adapter<ScriptListAdapter.ViewHolder> {

        private List<ScriptInfo> data = new ArrayList<>();
        private boolean isEmpty = false;
        private ScriptRuntime scriptRuntime;

        private final Handler refreshHandler = new Handler(Looper.getMainLooper());
        private final Runnable refreshRunnable = new Runnable() {
            @Override
            public void run() {
                // 刷新所有可见项（运行时间需要更新）
                notifyItemRangeChanged(0, getItemCount());
                refreshHandler.postDelayed(this, 1000);
            }
        };

        void startAutoRefresh() {
            stopAutoRefresh(); // 先停止旧的
            refreshHandler.postDelayed(refreshRunnable, 1000);
        }

        void stopAutoRefresh() {
            refreshHandler.removeCallbacks(refreshRunnable);
        }

        void setData(List<ScriptInfo> list) {
            data = new ArrayList<>(list);
            notifyDataSetChanged();
        }

        void setEmpty(boolean empty) {
            isEmpty = empty;
            notifyDataSetChanged();
        }

        void bindRuntime(ScriptRuntime runtime) {
            scriptRuntime = runtime;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_script, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (isEmpty) {
                holder.itemView.setVisibility(View.GONE);
                return;
            }
            holder.itemView.setVisibility(View.VISIBLE);
            ScriptInfo info = data.get(position);
            holder.tvName.setText(info.displayName + (info.isUI ? " (UI)" : " (后台)"));
            String stateText;
            switch (info.state) {
                case RUNNING:
                    stateText = "运行中 " + info.elapsedSeconds() + " 秒";
                    break;
                case STOPPING:
                    stateText = "正在停止...";
                    break;
                default:
                    stateText = "已结束";
            }
            holder.tvState.setText(stateText);
            boolean canStop = info.state == ScriptInfo.State.RUNNING;
            holder.btnStop.setEnabled(canStop);
            holder.btnStop.setAlpha(canStop ? 1.0f : 0.5f);
            holder.btnStop.setOnClickListener(v -> {
                if (scriptRuntime != null) scriptRuntime.stopScript(info.id);
            });
        }

        @Override
        public int getItemCount() {
            return isEmpty ? 1 : data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvState;
            TextView btnStop;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.script_name);
                tvState = itemView.findViewById(R.id.script_state);
                btnStop = itemView.findViewById(R.id.script_stop);
            }
        }
    }
}
