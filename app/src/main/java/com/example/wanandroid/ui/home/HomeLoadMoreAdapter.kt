package com.example.wanandroid.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.wanandroid.R
import com.example.wanandroid.databinding.ItemHomeLoadMoreBinding

class HomeLoadMoreAdapter(
    private val onLoadMoreClick: () -> Unit
) : RecyclerView.Adapter<HomeLoadMoreAdapter.Holder>() {
    private var state: HomeUiState.Success? = null

    fun submitState(newState: HomeUiState.Success?) {
        val oldCount = itemCount
        if (state == newState) return
        state = newState
        when {
            oldCount == 0 && itemCount == 1 -> notifyItemInserted(0)
            oldCount == 1 && itemCount == 0 -> notifyItemRemoved(0)
            itemCount == 1 -> notifyItemChanged(0)
        }
    }

    override fun getItemCount(): Int = if (state == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemHomeLoadMoreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = state ?: return
        val binding = holder.binding
        val context = binding.root.context
        binding.progressLoadMore.isVisible = current.isLoadingMore
        binding.textLoadMore.text = when {
            current.isRefreshing -> context.getString(R.string.home_refreshing)
            current.isLoadingMore -> context.getString(R.string.home_loading_more)
            current.loadMoreError != null -> context.getString(
                R.string.home_load_more_failed, current.loadMoreError
            )
            current.endReached -> context.getString(R.string.home_end_reached)
            else -> context.getString(R.string.home_more_hint)
        }
        binding.buttonLoadMore.isVisible =
            !current.isRefreshing && !current.isLoadingMore && !current.endReached
        binding.buttonLoadMore.setText(
            if (current.loadMoreError == null) R.string.home_load_more else R.string.retry
        )
        binding.buttonLoadMore.setOnClickListener { onLoadMoreClick() }
    }

    class Holder(val binding: ItemHomeLoadMoreBinding) : RecyclerView.ViewHolder(binding.root)
}
