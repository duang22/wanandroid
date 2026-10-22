package com.example.wanandroid.ui.common

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.loadMoreOnScroll(canLoad: () -> Boolean, loadMore: () -> Unit) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            if (manager.findLastVisibleItemPosition() < manager.itemCount - 3) return
            recyclerView.post {
                if (recyclerView.isAttachedToWindow && canLoad()) loadMore()
            }
        }
    })
}
