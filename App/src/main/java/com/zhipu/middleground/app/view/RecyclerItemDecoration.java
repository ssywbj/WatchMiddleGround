package com.zhipu.middleground.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.zhipu.middleground.app.R;

public class RecyclerItemDecoration extends RecyclerView.ItemDecoration {
    public static final int LIST_DIVIDER_DIMEN = 1;//分隔线尺寸
    private Drawable mListDivider;
    private boolean mHideLastDividerLine;

    public RecyclerItemDecoration(Context context) {
        mListDivider = context.getResources().getDrawable(R.drawable.ui_recycler_item_decoration);
    }

    public RecyclerItemDecoration(Context context, boolean hideLastDividerLine) {
        this(context);
        mHideLastDividerLine = hideLastDividerLine;
    }

    @Override
    public void onDraw(Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        drawHorizontal(c, parent);
        drawVertical(c, parent);
    }

    private int getSpanCount(RecyclerView parent) {
        int spanCount = -1;// 列数
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            spanCount = ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        }
        return spanCount;
    }

    private void drawHorizontal(Canvas c, RecyclerView parent) {
        View child;
        RecyclerView.LayoutParams params;
        int childCount = parent.getChildCount(), left, right, top, bottom;
        if (mHideLastDividerLine) {
            childCount -= 1;//如果有FooterView，FooterView也会被加上分隔线（测试方法：加粗分隔线尺寸，设置分隔线颜色为深色），所以此处要限制它的绘制
        }
        for (int i = 0; i < childCount; i++) {
            child = parent.getChildAt(i);
            params = (RecyclerView.LayoutParams) child.getLayoutParams();
            left = child.getLeft() - params.leftMargin;
            //int right = child.getRight() + params.rightMargin + mDivider.getIntrinsicWidth();
            right = child.getRight() + params.rightMargin + LIST_DIVIDER_DIMEN;
            top = child.getBottom() + params.bottomMargin;
            //int bottom = top + mDivider.getIntrinsicHeight();
            bottom = top + LIST_DIVIDER_DIMEN;
            mListDivider.setBounds(left, top, right, bottom);
            mListDivider.draw(c);
        }
    }

    private void drawVertical(Canvas c, RecyclerView parent) {
        View child;
        RecyclerView.LayoutParams params;
        int childCount = parent.getChildCount(), top, bottom, left, right;
        if (mHideLastDividerLine) {
            childCount -= 1;//如果有FooterView，FooterView也会被加上分隔线（测试方法：加粗分隔线尺寸，设置分隔线颜色为深色），所以此处要限制它的绘制
        }
        for (int i = 0; i < childCount; i++) {
            child = parent.getChildAt(i);
            params = (RecyclerView.LayoutParams) child.getLayoutParams();
            top = child.getTop() - params.topMargin;
            bottom = child.getBottom() + params.bottomMargin;
            left = child.getRight() + params.rightMargin;
            //right = left + mListDivider.getIntrinsicWidth();
            right = left + LIST_DIVIDER_DIMEN;
            mListDivider.setBounds(left, top, right, bottom);
            mListDivider.draw(c);
        }
    }

    private boolean isLastColumn(RecyclerView parent, int pos, int spanCount, int childCount) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        int orientation = RecyclerView.VERTICAL;
        if (layoutManager instanceof GridLayoutManager) {
            orientation = ((GridLayoutManager) layoutManager).getOrientation();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            // 由于瀑布流布局不是按顺序往多列间排序的，故这里的计算经常会不正确
            orientation = ((StaggeredGridLayoutManager) layoutManager).getOrientation();
        }

        if (orientation == StaggeredGridLayoutManager.VERTICAL) {
            //如果是最后一列，则不需要绘制右边
            return (pos + 1) % spanCount == 0;
        } else {
            childCount = childCount - childCount % spanCount;
            //如果是最后一列，则不需要绘制右边
            return pos >= childCount;
        }
    }

    private boolean isLastRaw(RecyclerView parent, int pos, int spanCount, int childCount) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        int orientation = RecyclerView.VERTICAL;
        if (layoutManager instanceof GridLayoutManager) {
            orientation = ((GridLayoutManager) layoutManager).getOrientation();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            orientation = ((StaggeredGridLayoutManager) layoutManager).getOrientation();
        }

        if (orientation == OrientationHelper.VERTICAL) {
            childCount = childCount - childCount % spanCount;
            //如果是最后一行，则不需要绘制底部
            return pos >= childCount;
        } else {
            //如果是最后一行，则不需要绘制底部
            return (pos + 1) % spanCount == 0;
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, int itemPosition, @NonNull RecyclerView parent) {
        int spanCount = getSpanCount(parent);
        int childCount = parent.getAdapter().getItemCount();
        if (this.isLastRaw(parent, itemPosition, spanCount, childCount)) {//如果是最后一行，则不需要绘制底部
            //outRect.set(0, 0, mDivider.getIntrinsicWidth(), 0);
            outRect.set(0, 0, LIST_DIVIDER_DIMEN, 0);
        } else if (this.isLastColumn(parent, itemPosition, spanCount, childCount)) {//如果是最后一列，则不需要绘制右边
            //outRect.set(0, 0, 0, mDivider.getIntrinsicHeight());
            outRect.set(0, 0, 0, LIST_DIVIDER_DIMEN);
        } else {
            //outRect.set(0, 0, mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight());
            outRect.set(0, 0, LIST_DIVIDER_DIMEN, LIST_DIVIDER_DIMEN);
        }
    }

}
