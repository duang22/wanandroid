package com.example.wanandroid.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wanandroid.Article
import com.example.wanandroid.R
import com.example.wanandroid.databinding.ItemArticleBinding
import androidx.core.view.isVisible
class ArticleAdapter(
    private val onArticleClick: (Article) -> Unit
    //这是个函数，保存 article 不返回
) : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {
    private var articles: List<Article> = emptyList()

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitArticles(newArticles: List<Article>) {
        articles = newArticles.toList()
        notifyDataSetChanged()
    }//保存文章，通知 RecyclerView 更新显示

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
        val article=articles[position]
        holder.bind(article)
        holder.itemView.setOnClickListener {
            onArticleClick(article)
            //调用 onArticleClick，把 Article 传出去
        }
    }

    override fun getItemCount(): Int = articles.size

    class ArticleViewHolder(private val binding: ItemArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(article: Article) {
            binding.textTop.isVisible = article.isTop
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
