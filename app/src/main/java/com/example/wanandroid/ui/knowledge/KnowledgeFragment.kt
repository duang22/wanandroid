package com.example.wanandroid.ui.knowledge

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.wanandroid.R
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ConcatAdapter
import com.example.wanandroid.ui.common.PageFooterAdapter
import com.example.wanandroid.ui.common.PageFooterState
import com.example.wanandroid.ui.common.loadMoreOnScroll
import com.example.wanandroid.data.model.KnowledgeCategory
import com.example.wanandroid.databinding.FragmentKnowledgeBinding
import com.example.wanandroid.ui.home.ArticleAdapter
import com.example.wanandroidlearn.ui.article.ArticleActivity
import com.google.android.material.tabs.TabLayout

class KnowledgeFragment : Fragment(R.layout.fragment_knowledge) {
    private var _binding: FragmentKnowledgeBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: KnowledgeViewModel
    private var renderingTabs = false
    private var renderedChildId: Int? = null
    private val footerAdapter = PageFooterAdapter { viewModel.loadMore() }

    private val articleAdapter = ArticleAdapter { article ->
        val intent = Intent(requireContext(), ArticleActivity::class.java)
        val title = HtmlCompat.fromHtml(
            article.title.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
        intent.putExtra("article_title", title)
        intent.putExtra("article_url", article.link.orEmpty())
        intent.putExtra("article_id", article.id)
        startActivity(intent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKnowledgeBinding.bind(view)
        renderingTabs = false
        binding.recyclerKnowledgeArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerKnowledgeArticles.adapter = ConcatAdapter(articleAdapter, footerAdapter)
        viewModel = ViewModelProvider(this)[KnowledgeViewModel::class.java]
        renderedChildId = viewModel.uiState.value?.selectedChildId
        binding.recyclerKnowledgeArticles.loadMoreOnScroll(
            canLoad = { _binding != null && isVisible && viewModel.uiState.value?.let {
                !it.isLoading && !it.isLoadingMore && !it.endReached && it.loadMoreError == null
            } == true },
            loadMore = { viewModel.loadMore() }
        )
        binding.buttonRetryKnowledge.setOnClickListener { viewModel.retry() }

        binding.tabsParents.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!renderingTabs) viewModel.selectParent(tab.tag as Int)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.tabsChildren.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!renderingTabs) viewModel.selectChild(tab.tag as Int)
                //通知 ViewModel
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        viewModel.uiState.observe(viewLifecycleOwner) { state -> render(state) }
    }

    private fun render(state: KnowledgeUiState) {
        val children = state.categories
            .firstOrNull { it.id == state.selectedParentId }?.children.orEmpty()
        renderingTabs = true
        renderTabs(binding.tabsParents, state.categories, state.selectedParentId)
        renderTabs(binding.tabsChildren, children, state.selectedChildId)
        renderingTabs = false

        binding.tabsParents.isVisible = state.categories.isNotEmpty()
        binding.tabsChildren.isVisible = children.isNotEmpty()
        binding.progressKnowledge.isVisible = state.isLoading
        binding.textKnowledgeMessage.text = state.message
        binding.buttonRetryKnowledge.isVisible = state.canRetry
        articleAdapter.submitArticles(state.articles)
        footerAdapter.submitState(PageFooterState(
            visible = state.articles.isNotEmpty(), isLoading = state.isLoadingMore,
            endReached = state.endReached, error = state.loadMoreError
        ))
        if (renderedChildId != state.selectedChildId) {
            binding.recyclerKnowledgeArticles.scrollToPosition(0)
            renderedChildId = state.selectedChildId
        }
    }

    private fun renderTabs(
        tabs: TabLayout,
        categories: List<KnowledgeCategory>,
        selectedId: Int?
    ) {
        val displayedIds = (0 until tabs.tabCount).map { tabs.getTabAt(it)?.tag }
        if (displayedIds != categories.map { it.id }) {
            tabs.removeAllTabs()
            categories.forEach { category ->
                val title = HtmlCompat.fromHtml(
                    category.name.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY
                ).toString()
                tabs.addTab(tabs.newTab().setText(title).setTag(category.id), false)
            }
        }
        val selectedIndex = categories.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0 && tabs.selectedTabPosition != selectedIndex) {
            tabs.selectTab(tabs.getTabAt(selectedIndex))
        }
    }

    override fun onDestroyView() {
        binding.recyclerKnowledgeArticles.clearOnScrollListeners()
        binding.recyclerKnowledgeArticles.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
