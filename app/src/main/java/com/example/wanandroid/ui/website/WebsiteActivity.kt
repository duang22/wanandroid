package com.example.wanandroid.ui.website

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.wanandroid.data.model.Website
import com.example.wanandroid.databinding.ActivityWebsiteBinding
import com.example.wanandroidlearn.ui.article.ArticleActivity
import com.google.android.material.chip.Chip

class WebsiteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebsiteBinding
    private lateinit var viewModel: WebsiteViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWebsiteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        applySystemInsets()

        viewModel = ViewModelProvider(this)[WebsiteViewModel::class.java]
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRetryWebsites.setOnClickListener { viewModel.loadWebsites() }
        viewModel.uiState.observe(this) { state -> render(state) }
    }

    private fun render(state: WebsiteUiState) {
        binding.progressWebsites.isVisible = state.isLoading
        binding.textWebsitesMessage.text = state.message
        binding.buttonRetryWebsites.isVisible = state.canRetry
        binding.chipWebsites.removeAllViews()
        state.websites.forEach { website ->
            val chip = Chip(this).apply {
                text = HtmlCompat.fromHtml(
                    website.name.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY
                ).toString()
                isCheckable = false
                setOnClickListener { openWebsite(website) }
            }
            binding.chipWebsites.addView(chip)
        }
    }

    private fun openWebsite(website: Website) {
        val title = HtmlCompat.fromHtml(
            website.name.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
        val intent = Intent(this, ArticleActivity::class.java)
        intent.putExtra("article_title", title)
        intent.putExtra("article_url", website.link.orEmpty().trim())
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
