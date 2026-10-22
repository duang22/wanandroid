package com.example.wanandroid.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.wanandroid.R
import com.example.wanandroid.databinding.ItemHomeLoadMoreBinding

data class PageFooterState(
    val visible: Boolean = false,
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val enabled: Boolean = true
)

class PageFooterAdapter(private val onLoadMore: () -> Unit) :
    RecyclerView.Adapter<PageFooterAdapter.Holder>() {
    private var state = PageFooterState()

    fun submitState(newState: PageFooterState) {
        if (newState == state) return
        val oldCount = itemCount
        state = newState
        when {
            oldCount == 0 && itemCount == 1 -> notifyItemInserted(0)
            oldCount == 1 && itemCount == 0 -> notifyItemRemoved(0)
            itemCount == 1 -> notifyItemChanged(0)
        }
    }

    override fun getItemCount(): Int = if (state.visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemHomeLoadMoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val binding = holder.binding
        val context = binding.root.context
        binding.progressLoadMore.isVisible = state.isLoading
        binding.textLoadMore.text = when {
            state.isLoading -> context.getString(R.string.home_loading_more)
            !state.enabled -> context.getString(R.string.collect_working)
            state.error != null -> context.getString(R.string.home_load_more_failed, state.error)
            state.endReached -> context.getString(R.string.home_end_reached)
            else -> context.getString(R.string.home_more_hint)
        }
        binding.buttonLoadMore.isVisible = !state.isLoading && (!state.endReached || state.error != null)
        binding.buttonLoadMore.isEnabled = state.enabled
        binding.buttonLoadMore.setText(if (state.error == null) R.string.home_load_more else R.string.retry)
        binding.buttonLoadMore.setOnClickListener { onLoadMore() }
    }

    class Holder(val binding: ItemHomeLoadMoreBinding) : RecyclerView.ViewHolder(binding.root)
}
