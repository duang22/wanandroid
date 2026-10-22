package com.example.wanandroid.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var loadMoreAdapter: HomeLoadMoreAdapter
    private var lastRefreshVersion = 0


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
        loadMoreAdapter = HomeLoadMoreAdapter { viewModel.loadMore() }
        binding.recyclerArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerArticles.adapter = ConcatAdapter(
            homeBannerAdapter, articleAdapter, loadMoreAdapter
        )
        setupPagination()
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.buttonSearchArticles.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.buttonRetry.setOnClickListener { viewModel.refresh() }
        viewModel.uiState.observe(viewLifecycleOwner) { state -> render(state) }
        viewModel.banners.observe(viewLifecycleOwner) { banners ->
            homeBannerAdapter.submitBanners(banners)
        }
    }

    private fun setupPagination() {
        binding.recyclerArticles.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as LinearLayoutManager
                if (manager.findLastVisibleItemPosition() < manager.itemCount - 3) return
                recyclerView.post {
                    val currentBinding = _binding ?: return@post
                    if (currentBinding.recyclerArticles !== recyclerView || !isVisible) return@post
                    val state = viewModel.uiState.value as? HomeUiState.Success ?: return@post
                    if (state.loadMoreError == null) viewModel.loadMore()
                }
            }
        })
    }

    private fun render(state: HomeUiState) {
        val success = state as? HomeUiState.Success
        binding.progressLoading.isVisible = state is HomeUiState.Loading
        binding.swipeRefresh.isVisible = success != null
        binding.swipeRefresh.isEnabled =
            state !is HomeUiState.Loading && success?.isLoadingMore != true
        binding.swipeRefresh.isRefreshing = success?.isRefreshing == true
        binding.layoutMessage.isVisible =
            state is HomeUiState.Empty || state is HomeUiState.Error
        binding.textRefreshError.isVisible = success?.refreshError != null
        binding.textRefreshError.text = success?.refreshError?.let {
            getString(R.string.home_refresh_failed, it)
        }.orEmpty()
        loadMoreAdapter.submitState(success)
        when (state) {
            HomeUiState.Loading -> Unit
            is HomeUiState.Success -> {
                val returnToTop = lastRefreshVersion > 0 &&
                    lastRefreshVersion != state.refreshVersion
                lastRefreshVersion = state.refreshVersion
                articleAdapter.submitArticles(state.articles)
                if (returnToTop) binding.recyclerArticles.scrollToPosition(0)
            }
            HomeUiState.Empty -> binding.textMessage.setText(R.string.empty_articles)
            is HomeUiState.Error -> binding.textMessage.text = state.message
        }
    }

    override fun onDestroyView() {
        binding.recyclerArticles.clearOnScrollListeners()
        binding.recyclerArticles.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
