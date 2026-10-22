package com.example.wanandroidlearn.ui.article

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.example.wanandroid.ui.collect.ArticleCollectUiState
import com.example.wanandroid.ui.collect.ArticleCollectViewModel
import com.example.wanandroid.ui.login.LoginActivity
import com.example.wanandroid.ui.history.ReadHistoryViewModel
import android.webkit.SslErrorHandler
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.wanandroid.databinding.ActivityArticleBinding
import android.webkit.WebViewClient
import com.example.wanandroid.R
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast

class ArticleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArticleBinding
    private lateinit var collectViewModel: ArticleCollectViewModel
    private val articleId: Int
        get() = intent.getIntExtra(EXTRA_ARTICLE_ID, 0)
    private var currentUrl = ""
    private var pageFailed = false
    private var pageLoading = false

    companion object {
        private const val EXTRA_TITLE = "article_title"
        private const val EXTRA_URL = "article_url"
        private const val EXTRA_ARTICLE_ID = "article_id"
        private const val STATE_CURRENT_URL = "state_current_url"
        private const val STATE_WEB_VIEW = "state_web_view"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityArticleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.textArticleTitle.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        recordHistory()
        setupCollect()
        setupWebView()
        bindActions()
        restoreOrLoad(savedInstanceState)
    }

    private fun recordHistory() {
        val url = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        if (!isWebUrl(url)) return
        val historyViewModel = ViewModelProvider(this)[ReadHistoryViewModel::class.java]
        historyViewModel.errorMessage.observe(this) { message ->
            if (message.isNotBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                historyViewModel.clearError()
            }
        }
        historyViewModel.record(
            url = url,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            articleId = articleId
        )
    }

    private fun setupCollect() {
        binding.buttonCollect.isVisible = articleId > 0
        if (articleId <= 0) return
        collectViewModel = ViewModelProvider(this)[ArticleCollectViewModel::class.java]
        binding.buttonCollect.setOnClickListener {
            val state = collectViewModel.uiState.value ?: return@setOnClickListener
            when {
                state.needsLogin -> startActivity(Intent(this, LoginActivity::class.java))
                !state.isReady -> collectViewModel.refresh(articleId)
                else -> collectViewModel.toggle(articleId)
            }
        }
        collectViewModel.uiState.observe(this) { state -> renderCollect(state) }
    }

    private fun renderCollect(state: ArticleCollectUiState) {
        binding.buttonCollect.isEnabled = !state.isLoading
        binding.buttonCollect.setText(when {
            state.isLoading -> R.string.collect_working
            state.needsLogin -> R.string.collect_login_action
            !state.isReady -> R.string.collect_retry
            state.isCollected -> R.string.collect_remove
            else -> R.string.collect_action
        })
        binding.textCollectMessage.text = state.message
        binding.textCollectMessage.isVisible = state.message.isNotBlank()
    }

    private fun isWebUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val isHttp = uri.scheme.equals("http", ignoreCase = true)
        val isHttps = uri.scheme.equals("https", ignoreCase = true)
        return (isHttp || isHttps) && !uri.host.isNullOrBlank()
    }

    private fun updateCurrentUrl(url: String) {
        currentUrl = url
        binding.buttonBrowser.isEnabled = isWebUrl(url)
    }

    private fun showLoading() {
        pageFailed = false
        pageLoading = true
        binding.layoutWebError.isVisible = false
        binding.buttonWebRetry.isVisible = false
        binding.webArticle.isVisible = true
        binding.progressWeb.progress = 0
        binding.progressWeb.isVisible = true
    }

    private fun showContent() {
        pageFailed = false
        pageLoading = false
        binding.layoutWebError.isVisible = false
        binding.buttonWebRetry.isVisible = false
        binding.webArticle.isVisible = true
        binding.progressWeb.isVisible = false
    }

    private fun showPageError(message: String, canRetry: Boolean) {
        pageFailed = true
        pageLoading = false
        binding.progressWeb.isVisible = false
        binding.webArticle.isVisible = false
        binding.layoutWebError.isVisible = true
        binding.textWebError.text = message
        binding.buttonWebRetry.isVisible = canRetry
    }

    private fun loadArticle(url: String) {
        updateCurrentUrl(url.trim())
        if (!isWebUrl(currentUrl)) {
            showPageError(getString(R.string.article_invalid_url), canRetry = false)
            return
        }

        val uri = Uri.parse(currentUrl)
        if (uri.scheme.equals("http", ignoreCase = true)) {
            showPageError(getString(R.string.article_http_external), canRetry = false)
            return
        }

        showLoading()
        binding.webArticle.loadUrl(currentUrl)
    }

    private fun setupWebView() {
        binding.webArticle.settings.javaScriptEnabled = true
        binding.webArticle.settings.domStorageEnabled = true
        binding.webArticle.settings.allowFileAccess = false
        binding.webArticle.settings.allowContentAccess = false
        binding.webArticle.webViewClient = createArticleWebViewClient()

        binding.webArticle.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressWeb.progress = newProgress
                binding.progressWeb.isVisible =
                    pageLoading && !pageFailed && newProgress < 100
            }
        }
    }
    private fun createArticleWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isWebUrl(url)) {
                    updateCurrentUrl(url)
                    showLoading()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!pageFailed) {
                    showContent()
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!pageFailed && isWebUrl(url)) {
                    updateCurrentUrl(url)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame) return
                updateCurrentUrl(request.url.toString())
                showPageError(getString(R.string.article_network_error), canRetry = true)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (!request.isForMainFrame) return
                updateCurrentUrl(request.url.toString())
                val message = getString(R.string.article_http_error, errorResponse.statusCode)
                showPageError(message, canRetry = true)
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                if (error.url == currentUrl || error.url == view.url) {
                    showPageError(getString(R.string.article_ssl_error), canRetry = true)
                }
            }


            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) return false

                val targetUrl = request.url.toString()
                val isHttps = request.url.scheme.equals("https", ignoreCase = true)
                if (isWebUrl(targetUrl) && isHttps) return false

                view.stopLoading()
                loadArticle(targetUrl)
                return true
            }
        }
    }


    private fun bindActions() {
        binding.buttonWebRetry.setOnClickListener {
            loadArticle(currentUrl)
        }
        binding.buttonBrowser.setOnClickListener {
            openInBrowser(currentUrl)
        }
        binding.buttonBack.setOnClickListener {
            goBackOrFinish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBackOrFinish()
            }
        })
    }
    private fun goBackOrFinish() {
        if (binding.webArticle.canGoBack()) {
            showContent()
            binding.webArticle.goBack()
        } else {
            finish()
        }
    }
    private fun openInBrowser(url: String) {
        if (!isWebUrl(url)) {
            Toast.makeText(this, R.string.article_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.article_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_URL, currentUrl)

        if (!pageFailed && !pageLoading) {
            val webState = Bundle()
            val history = binding.webArticle.saveState(webState)
            if (history != null) {
                outState.putBundle(STATE_WEB_VIEW, webState)
            }
        }

        super.onSaveInstanceState(outState)
    }
    private fun restoreOrLoad(savedInstanceState: Bundle?) {
        val savedUrl = savedInstanceState?.getString(STATE_CURRENT_URL)
        val initialUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        updateCurrentUrl((savedUrl ?: initialUrl).trim())

        val webState = savedInstanceState?.getBundle(STATE_WEB_VIEW)
        val isHttps = Uri.parse(currentUrl).scheme.equals("https", ignoreCase = true)
        if (webState != null && isWebUrl(currentUrl) && isHttps) {
            showContent()
            val history = binding.webArticle.restoreState(webState)
            val restoredPage = history?.currentItem
            if (restoredPage != null) {
                updateCurrentUrl(restoredPage.url)
                return
            }
        }

        loadArticle(currentUrl)
    }
    override fun onPause() {
        binding.webArticle.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webArticle.onResume()
        if (articleId > 0) collectViewModel.refresh(articleId)
    }

    override fun onDestroy() {
        binding.webArticle.webChromeClient = null
        binding.webArticle.webViewClient = WebViewClient()
        binding.webArticle.stopLoading()
        binding.webContainer.removeView(binding.webArticle)
        binding.webArticle.destroy()
        super.onDestroy()
    }



}
