package com.example.wanandroid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wanandroid.databinding.ActivityMainBinding
import com.example.wanandroid.ui.home.ArticleAdapter
import com.example.wanandroid.ui.home.HomeUiState
import com.example.wanandroid.ui.home.HomeViewModel
import com.example.wanandroidlearn.ui.article.ArticleActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: HomeViewModel
//    private val articleAdapter = ArticleAdapter { article ->
//        Toast.makeText(
//            this,
//            article.title.orEmpty(),
//            Toast.LENGTH_SHORT
//        ).show()
//    }
//    private val articleAdapter = ArticleAdapter { article ->
//        val url = article.link.orEmpty().trim()
//        if (url.isBlank()) {
//            Toast.makeText(this, R.string.article_invalid_url, Toast.LENGTH_SHORT).show()
//        } else {
//            ArticleActivity.start(
//                activity = this,
//                title = article.title.orEmpty(),
//                url = url
//            )
//        }
//    }
    private val articleAdapter = ArticleAdapter(
        onArticleClick = { clickedArticle ->
            val intent = Intent(this, ArticleActivity::class.java)
            intent.putExtra("article_title", clickedArticle.title.orEmpty())
            intent.putExtra("article_url", clickedArticle.link.orEmpty())
            startActivity(intent)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        //隐藏默认标题栏
        applySystemInsets()
        binding.recyclerArticles.layoutManager = LinearLayoutManager(this)
        binding.recyclerArticles.adapter = articleAdapter
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding.buttonRetry.setOnClickListener {
            viewModel.loadArticles()
        }
        viewModel.uiState.observe(this) { state ->
            render(state)
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun render(state: HomeUiState) {
        binding.progressLoading.isVisible = state is HomeUiState.Loading
        binding.recyclerArticles.isVisible = state is HomeUiState.Success
        binding.layoutMessage.isVisible =
            state is HomeUiState.Empty || state is HomeUiState.Error

        when (state) {
            HomeUiState.Loading -> Unit
            is HomeUiState.Success -> {
                articleAdapter.submitArticles(state.articles)
            }
            HomeUiState.Empty -> {
                binding.textMessage.setText(R.string.empty_articles)
            }
            is HomeUiState.Error -> {
                binding.textMessage.text = state.message
            }
        }
    }
}
