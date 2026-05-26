package com.wumin.wuminpy.drawer;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wumin.wuminpy.R;

import java.util.List;

public class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<DrawerMenuItem> items;
    private OnMenuItemClickListener listener;

    public interface OnMenuItemClickListener {
        void onParentClick(DrawerMenuItem parent);          // 点击没有子项的父项（如“终端”）
        void onChildClick(DrawerMenuItem child);            // 点击普通子项
        void onSwitchChanged(DrawerMenuItem item, boolean isChecked); // 开关状态变化
    }

    public DrawerAdapter(List<DrawerMenuItem> items, OnMenuItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    public DrawerMenuItem getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == DrawerMenuItem.TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_drawer_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == DrawerMenuItem.TYPE_PARENT) {
            View view = inflater.inflate(R.layout.item_drawer_parent, parent, false);
            return new ParentViewHolder(view);
        } else if (viewType == DrawerMenuItem.TYPE_CHILD_SWITCH) {
            View view = inflater.inflate(R.layout.item_drawer_child_switch, parent, false);
            return new ChildSwitchViewHolder(view);
        } else { // TYPE_CHILD_NORMAL
            View view = inflater.inflate(R.layout.item_drawer_child_normal, parent, false);
            return new ChildNormalViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DrawerMenuItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (holder instanceof ParentViewHolder) {
            ((ParentViewHolder) holder).bind(item);
        } else if (holder instanceof ChildSwitchViewHolder) {
            ((ChildSwitchViewHolder) holder).bind(item);
        } else if (holder instanceof ChildNormalViewHolder) {
            ((ChildNormalViewHolder) holder).bind(item);
        }
    }

    // ========== 头部 ViewHolder ==========
    class HeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView username, email;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.iv_avatar);
            username = itemView.findViewById(R.id.tv_username);
            email = itemView.findViewById(R.id.tv_email);
        }

        void bind(DrawerMenuItem item) {
            // 可以动态设置头像、用户名等
            username.setText("用户名");
            email.setText("user@example.com");
        }
    }

    // ========== 父项 ViewHolder ==========
    class ParentViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView icon, arrow;

        ParentViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_title);
            icon = itemView.findViewById(R.id.iv_icon);
            arrow = itemView.findViewById(R.id.iv_arrow);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    toggleParent(position);
                }
            });
        }

        void bind(DrawerMenuItem item) {
            title.setText(item.title);
            if (item.iconRes != 0) {
                icon.setImageResource(item.iconRes);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }
            // 箭头旋转：展开时向下（180°），折叠时向右（0°）
            arrow.setRotation(item.isExpanded ? 180f : 0f);
            // 如果没有子项，隐藏箭头
            if (item.children == null || item.children.isEmpty()) {
                arrow.setVisibility(View.GONE);
            } else {
                arrow.setVisibility(View.VISIBLE);
            }
        }
    }

    // ========== 普通子项 ViewHolder ==========
    class ChildNormalViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView icon;

        ChildNormalViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_title);
            icon = itemView.findViewById(R.id.iv_icon);
            itemView.setOnClickListener(v -> {
                DrawerMenuItem item = (DrawerMenuItem) itemView.getTag();
                if (listener != null) listener.onChildClick(item);
            });
        }

        void bind(DrawerMenuItem item) {
            itemView.setTag(item);
            title.setText(item.title);
            if (item.iconRes != 0) {
                icon.setImageResource(item.iconRes);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }
        }
    }

    class ChildSwitchViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView icon;
        com.google.android.material.materialswitch.MaterialSwitch switchView;

        ChildSwitchViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_title);
            icon = itemView.findViewById(R.id.iv_icon);
            switchView = itemView.findViewById(R.id.sw_item);

            // 点击整个条目 -> 切换开关状态
            itemView.setOnClickListener(v -> {
                // 切换开关，会触发 OnCheckedChangeListener
                switchView.setChecked(!switchView.isChecked());
            });

            // 开关状态变化监听
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                DrawerMenuItem item = (DrawerMenuItem) itemView.getTag();
                if (item != null) {
                    item.isChecked = isChecked; // 同步状态
                    if (listener != null) {
                        listener.onSwitchChanged(item, isChecked); // 回调给 Activity
                    }
                }
            });
        }

        void bind(DrawerMenuItem item) {
            itemView.setTag(item);
            title.setText(item.title);
            if (item.iconRes != 0) {
                icon.setImageResource(item.iconRes);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }

            // 绑定开关状态时，临时移除监听器避免触发回调
            switchView.setOnCheckedChangeListener(null);
            switchView.setChecked(item.isChecked);
            // 重新设置监听器
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                DrawerMenuItem current = (DrawerMenuItem) itemView.getTag();
                if (current != null) {
                    current.isChecked = isChecked;
                    if (listener != null) {
                        listener.onSwitchChanged(current, isChecked);
                    }
                }
            });
        }
    }
    // ========== 展开/折叠逻辑（在适配器内部处理） ==========
    public void toggleParent(int position) {
        if (position < 0 || position >= items.size()) return;
        DrawerMenuItem parent = items.get(position);
        if (parent.type != DrawerMenuItem.TYPE_PARENT) return;

        // 如果没有子项，当作普通父项处理（回调给监听器）
        if (parent.children == null || parent.children.isEmpty()) {
            if (listener != null) listener.onParentClick(parent);
            return;
        }

        if (parent.isExpanded) {
            // 折叠：删除子项
            int childCount = parent.children.size();
            int start = position + 1;
            // 防御性检查：确保要删除的范围有效
            if (start + childCount <= items.size()) {
                items.subList(start, start + childCount).clear();
                notifyItemRangeRemoved(start, childCount);
                parent.isExpanded = false;
                notifyItemChanged(position); // 更新箭头
            } else {
                // 数据不一致，强制重置（极少发生）
                parent.isExpanded = false;
                rebuildConsistentState();
            }
        } else {
            // 展开：插入子项
            int start = position + 1;
            items.addAll(start, parent.children);
            notifyItemRangeInserted(start, parent.children.size());
            parent.isExpanded = true;
            notifyItemChanged(position);
        }
    }

    // 极端情况下的数据恢复（重置所有父项为折叠，移除所有子项）
    private void rebuildConsistentState() {
        for (int i = items.size() - 1; i >= 0; i--) {
            DrawerMenuItem item = items.get(i);
            if (item.type == DrawerMenuItem.TYPE_CHILD_NORMAL || item.type == DrawerMenuItem.TYPE_CHILD_SWITCH) {
                items.remove(i);
            } else if (item.type == DrawerMenuItem.TYPE_PARENT) {
                item.isExpanded = false;
            }
        }
        notifyDataSetChanged();
    }
}