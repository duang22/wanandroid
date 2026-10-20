package com.example.wanandroid.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wanandroid.R
import com.example.wanandroid.databinding.FragmentHomeBinding
import com.example.wanandroid.ui.search.SearchActivity
import com.example.wanandroidlearn.ui.article.ArticleActivity

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: HomeViewModel
    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var homeBannerAdapter: HomeBannerAdapter

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        articleAdapter = ArticleAdapter { clickedArticle ->
            val intent = Intent(requireContext(), ArticleActivity::class.java)
            intent.putExtra("article_title", clickedArticle.title.orEmpty())
            intent.putExtra("article_url", clickedArticle.link.orEmpty())
            intent.putExtra("article_id", clickedArticle.id)
            startActivity(intent)
        }
        homeBannerAdapter = HomeBannerAdapter(
            lifecycleOwner = viewLifecycleOwner,
            onBannerClick = { banner ->
                val intent = Intent(requireContext(), ArticleActivity::class.java)
                intent.putExtra("article_title", banner.title.orEmpty())
                intent.putExtra("article_url", banner.url.orEmpty())
                startActivity(intent)
            }
        )
        binding.recyclerArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerArticles.adapter = ConcatAdapter(homeBannerAdapter, articleAdapter)
        binding.buttonSearchArticles.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.buttonRetry.setOnClickListener { viewModel.loadArticles() }
        viewModel.uiState.observe(viewLifecycleOwner) { state -> render(state) }
        viewModel.banners.observe(viewLifecycleOwner) { banners ->
            homeBannerAdapter.submitBanners(banners)
        }
    }

    private fun render(state: HomeUiState) {
        binding.progressLoading.isVisible = state is HomeUiState.Loading
        binding.recyclerArticles.isVisible = state is HomeUiState.Success
        binding.layoutMessage.isVisible =
            state is HomeUiState.Empty || state is HomeUiState.Error
        when (state) {
            HomeUiState.Loading -> Unit
            is HomeUiState.Success -> articleAdapter.submitArticles(state.articles)
            HomeUiState.Empty -> binding.textMessage.setText(R.string.empty_articles)
            is HomeUiState.Error -> binding.textMessage.text = state.message
        }
    }

    override fun onDestroyView() {
        binding.recyclerArticles.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
