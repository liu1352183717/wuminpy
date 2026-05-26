package com.wumin.wuminpy.drawer;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class DrawerDividerItemDecoration extends RecyclerView.ItemDecoration {
    private final Drawable divider;
    private final DrawerAdapter adapter;

    public DrawerDividerItemDecoration(Drawable divider, DrawerAdapter adapter) {
        this.divider = divider;
        this.adapter = adapter;
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;

            // 判断是否需要在当前 child 上方绘制分割线
            if (shouldDrawDividerAbove(position)) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                int top = child.getTop() - params.topMargin;
                int bottom = top + divider.getIntrinsicHeight();
                divider.setBounds(left, top, right, bottom);
                divider.draw(c);
            }
        }
    }

    private boolean shouldDrawDividerAbove(int position) {
        if (position == 0) return false; // 第一个 item 上方不绘制

        DrawerMenuItem prev = adapter.getItem(position - 1);
        DrawerMenuItem current = adapter.getItem(position);

        // 判断是否是子项
        boolean prevIsChild = prev.type == DrawerMenuItem.TYPE_CHILD_NORMAL || prev.type == DrawerMenuItem.TYPE_CHILD_SWITCH;
        boolean currIsChild = current.type == DrawerMenuItem.TYPE_CHILD_NORMAL || current.type == DrawerMenuItem.TYPE_CHILD_SWITCH;

        // 如果前一个和当前都是子项，且属于同一父项 → 不绘制分割线
        if (prevIsChild && currIsChild && prev.parentId == current.parentId) {
            return false;
        }

        // 如果前一个是父项，当前是子项，且子项属于该父项 → 不绘制分割线
        if (prev.type == DrawerMenuItem.TYPE_PARENT && currIsChild && current.parentId == prev.id) {
            return false;
        }

        // 其他情况绘制分割线
        return true;
    }
}