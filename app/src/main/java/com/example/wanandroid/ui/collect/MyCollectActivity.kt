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
import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.databinding.ActivityMyCollectBinding
import com.example.wanandroid.ui.login.LoginActivity
import com.example.wanandroidlearn.ui.article.ArticleActivity

class MyCollectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyCollectBinding
    private lateinit var viewModel: MyCollectViewModel
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
        binding.recyclerMyCollect.adapter = articleAdapter
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefreshCollect.setOnClickListener { viewModel.loadCollections() }
        binding.buttonLoginToCollect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        viewModel.uiState.observe(this) { state ->
            binding.progressMyCollect.isVisible = state.isLoading
            binding.textMyCollectMessage.text = state.message
            binding.buttonLoginToCollect.isVisible = state.needsLogin
            binding.buttonLoginToCollect.isEnabled = !state.isLoading
            binding.buttonRefreshCollect.isEnabled = !state.isLoading
            articleAdapter.submitArticles(state.articles, state.isLoading)
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
