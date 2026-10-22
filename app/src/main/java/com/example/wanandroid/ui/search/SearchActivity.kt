package com.example.wanandroid.ui.search

import com.google.android.material.chip.Chip
import com.example.wanandroid.data.model.HotKeyword
import androidx.core.view.children
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
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
import com.example.wanandroid.R
import com.example.wanandroid.databinding.ActivitySearchBinding
import com.example.wanandroid.ui.home.ArticleAdapter
import com.example.wanandroidlearn.ui.article.ArticleActivity

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var viewModel: SearchViewModel
    private var renderedVersion = 0
    private val footerAdapter = PageFooterAdapter { viewModel.loadMore() }

    private val articleAdapter = ArticleAdapter { article ->
        val intent = Intent(this, ArticleActivity::class.java)
        val title = HtmlCompat.fromHtml(
            article.title.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
        intent.putExtra("article_title", title)
        intent.putExtra("article_url", article.link.orEmpty())
        intent.putExtra("article_id", article.id)
        startActivity(intent)
    }//点击链接跳转

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        applySystemInsets()

        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = ConcatAdapter(articleAdapter, footerAdapter)
        viewModel = ViewModelProvider(this)[SearchViewModel::class.java]
        renderedVersion = viewModel.uiState.value?.requestVersion ?: 0
        binding.recyclerResults.loadMoreOnScroll(
            canLoad = { viewModel.uiState.value?.let {
                !it.isLoading && !it.isLoadingMore && !it.endReached && it.loadMoreError == null
            } == true },
            loadMore = { viewModel.loadMore() }
        )

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonSearch.setOnClickListener { performSearch() }
        binding.editKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        viewModel.uiState.observe(this) { state -> render(state) }
        viewModel.hotKeywords.observe(this) { keywords ->
            renderHotKeywords(keywords)
        }
    }

    private fun performSearch() {
        val keyword = binding.editKeyword.text.toString().trim()
        if (keyword.isEmpty()) {
            binding.editKeyword.error = getString(R.string.search_enter_keyword)
            return
        }
        viewModel.search(keyword)
    }

    private fun render(state: SearchUiState) {
        binding.progressSearch.isVisible = state.isLoading
        binding.textSearchMessage.text = state.message
        binding.buttonSearch.isEnabled = true
        binding.editKeyword.isEnabled = true
        binding.chipHotKeywords.children.forEach { chip ->
            chip.isEnabled = true
        }
        articleAdapter.submitArticles(state.articles)
        footerAdapter.submitState(PageFooterState(
            visible = state.articles.isNotEmpty(), isLoading = state.isLoadingMore,
            endReached = state.endReached, error = state.loadMoreError
        ))
        if (renderedVersion != state.requestVersion) {
            binding.recyclerResults.scrollToPosition(0)
            renderedVersion = state.requestVersion
        }
        //提交文章，adapter 会显示文章叫 recycleview 刷新
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun renderHotKeywords(keywords: List<HotKeyword>) {
        //根据列表生成标签
        binding.layoutHotKeywords.isVisible = keywords.isNotEmpty()
        binding.chipHotKeywords.removeAllViews()
        keywords.forEach { keyword ->
            val name = keyword.name.orEmpty().trim()
            val chip = Chip(this).apply {
                text = name
                isCheckable = false
                isEnabled = true
                setOnClickListener {
                    binding.editKeyword.error = null
                    binding.editKeyword.setText(name)
                    binding.editKeyword.setSelection(name.length)
                    performSearch()
                }
            }
            binding.chipHotKeywords.addView(chip)
        }
    }
}
