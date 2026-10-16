package com.example.wanandroid.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wanandroid.Article
import com.example.wanandroid.R
import com.example.wanandroid.databinding.ItemArticleBinding

class ArticleAdapter : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {
    private var articles: List<Article> = emptyList()

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitArticles(newArticles: List<Article>) {
        articles = newArticles.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ArticleViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount(): Int = articles.size

    class ArticleViewHolder(private val binding: ItemArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(article: Article) {
            val context = binding.root.context
            val author = article.author?.takeIf { it.isNotBlank() }
                ?: article.shareUser?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.unknown_author)
            val title = article.title?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.unknown_title)

            binding.textAuthor.text = author
            binding.textDate.text = article.niceDate?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.unknown_time)
            binding.textTitle.text = HtmlCompat.fromHtml(
                title,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            binding.textCategory.text =
                article.chapterName?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.unknown_category)
        }
    }
}
