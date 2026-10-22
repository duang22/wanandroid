package com.example.wanandroid.ui.collect

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ConcatAdapter
import com.example.wanandroid.ui.common.PageFooterAdapter
import com.example.wanandroid.ui.common.PageFooterState
import com.example.wanandroid.ui.common.loadMoreOnScroll
import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.databinding.ActivityMyCollectBinding
import com.example.wanandroid.ui.login.LoginActivity
import com.example.wanandroidlearn.ui.article.ArticleActivity

class MyCollectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyCollectBinding
    private lateinit var viewModel: MyCollectViewModel
    private val footerAdapter = PageFooterAdapter { viewModel.loadMore() }
    private val articleAdapter = CollectedArticleAdapter(
        onArticleClick = { article -> openArticle(article) },
        onRemoveClick = { article -> viewModel.remove(article) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMyCollectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        applySystemInsets()

        viewModel = ViewModelProvider(this)[MyCollectViewModel::class.java]
        binding.recyclerMyCollect.layoutManager = LinearLayoutManager(this)
        binding.recyclerMyCollect.adapter = ConcatAdapter(articleAdapter, footerAdapter)
        binding.recyclerMyCollect.loadMoreOnScroll(
            canLoad = { viewModel.uiState.value?.let {
                !it.isLoading && !it.isLoadingMore && !it.isRemoving && !it.needsLogin &&
                    !it.endReached && it.loadMoreError == null
            } == true },
            loadMore = { viewModel.loadMore() }
        )
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefreshCollect.setOnClickListener { viewModel.loadCollections() }
        binding.buttonLoginToCollect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        viewModel.uiState.observe(this) { state ->
            val busy = state.isLoading || state.isLoadingMore || state.isRemoving
            binding.progressMyCollect.isVisible = state.isLoading || state.isRemoving
            binding.textMyCollectMessage.text = state.message
            binding.buttonLoginToCollect.isVisible = state.needsLogin
            binding.buttonLoginToCollect.isEnabled = !busy
            binding.buttonRefreshCollect.isEnabled = !busy
            articleAdapter.submitArticles(state.articles, busy)
            footerAdapter.submitState(PageFooterState(
                visible = state.articles.isNotEmpty() && !state.needsLogin,
                isLoading = state.isLoadingMore, endReached = state.endReached,
                error = state.loadMoreError, enabled = !busy
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCollections()
    }

    private fun openArticle(article: CollectedArticle) {
        val intent = Intent(this, ArticleActivity::class.java)
        val title = HtmlCompat.fromHtml(
            article.title.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
        intent.putExtra("article_title", title)
        intent.putExtra("article_url", article.link.orEmpty())
        intent.putExtra("article_id", article.originId)
        startActivity(intent)
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
