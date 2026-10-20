package com.example.wanandroid.ui.collect

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wanandroid.R
import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.databinding.ItemCollectedArticleBinding

class CollectedArticleAdapter(
    private val onArticleClick: (CollectedArticle) -> Unit,
    private val onRemoveClick: (CollectedArticle) -> Unit
) : RecyclerView.Adapter<CollectedArticleAdapter.Holder>() {
    private var articles: List<CollectedArticle> = emptyList()
    private var isLoading = false

    @SuppressLint("NotifyDataSetChanged")
    fun submitArticles(newArticles: List<CollectedArticle>, loading: Boolean) {
        articles = newArticles.toList()
        isLoading = loading
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCollectedArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val article = articles[position]
        holder.bind(article, isLoading)
        holder.itemView.setOnClickListener { onArticleClick(article) }
        holder.binding.buttonRemoveCollect.setOnClickListener { onRemoveClick(article) }
    }

    override fun getItemCount(): Int = articles.size

    class Holder(val binding: ItemCollectedArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(article: CollectedArticle, loading: Boolean) {
            binding.textCollectedTitle.text = HtmlCompat.fromHtml(
                article.title.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            binding.textCollectedAuthor.text = article.author?.takeIf { it.isNotBlank() }
                ?: binding.root.context.getString(R.string.unknown_author)
            binding.root.isEnabled = !loading
            binding.buttonRemoveCollect.isEnabled = !loading
        }
    }
}
