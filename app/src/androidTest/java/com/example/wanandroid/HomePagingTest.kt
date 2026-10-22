package com.example.wanandroid

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wanandroid.data.model.HomeBanner
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiResponse
import com.example.wanandroid.data.remote.ApiService
import com.example.wanandroid.data.remote.ArticlePage
import com.example.wanandroid.data.repository.HomeRepository
import com.example.wanandroid.ui.home.HomeUiState
import com.example.wanandroid.ui.home.HomeViewModel
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePagingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun clearViewModels() {
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
    }

    @Test
    fun firstPageMergesTopAndNormalArticlesWithoutDuplicates() {
        val service = FakeHomeService()
        val model = createModel(service)
        val state = success(model)
        assertEquals(listOf(1, 2), state.articles.map { it.id })
        assertTrue(state.articles.first().isTop)
        assertEquals(listOf(0), service.pageRequests)
        assertEquals(1, service.topCalls)
        assertEquals(1, service.bannerCalls)
    }

    @Test
    fun loadMoreAppendsUniqueArticlesAndDoesNotReloadTopOrBanner() {
        val service = FakeHomeService()
        val model = createModel(service)
        success(model)
        instrumentation.runOnMainSync { model.loadMore() }
        val state = success(model)
        assertEquals(listOf(1, 2, 3), state.articles.map { it.id })
        assertEquals(listOf(0, 1), service.pageRequests)
        assertEquals(1, service.topCalls)
        assertEquals(1, service.bannerCalls)
    }

    @Test
    fun failedPageKeepsArticlesAndRetryRequestsTheSamePage() {
        val service = FakeHomeService()
        var failNextPage = true
        service.pageAction = { page ->
            if (page == 1 && failNextPage) {
                failNextPage = false
                throw IOException("offline")
            }
            service.response(page)
        }
        val model = createModel(service)
        val initial = success(model)
        instrumentation.runOnMainSync { model.loadMore() }
        val failed = success(model)
        assertEquals(initial.articles, failed.articles)
        assertNotNull(failed.loadMoreError)
        assertFalse(failed.isLoadingMore)
        instrumentation.runOnMainSync { model.loadMore() }
        val retried = success(model)
        assertEquals(listOf(0, 1, 1), service.pageRequests)
        assertEquals(listOf(1, 2, 3), retried.articles.map { it.id })
        assertNull(retried.loadMoreError)
    }

    @Test
    fun successfulRefreshReplacesOldPagesAndResetsNextPageToOne() {
        val service = FakeHomeService()
        val model = createModel(service)
        success(model)
        instrumentation.runOnMainSync { model.loadMore() }
        success(model)
        instrumentation.runOnMainSync {
            service.top = listOf(Article(id = 9))
            service.pages[0] = ArticlePage(listOf(Article(id = 7), Article(id = 8)))
            service.pages[1] = ArticlePage(listOf(Article(id = 10)))
            model.refresh()
        }
        val refreshed = success(model)
        assertEquals(listOf(9, 7, 8), refreshed.articles.map { it.id })
        assertEquals(2, refreshed.refreshVersion)
        instrumentation.runOnMainSync { model.loadMore() }
        assertEquals(listOf(9, 7, 8, 10), success(model).articles.map { it.id })
        assertEquals(listOf(0, 1, 0, 1), service.pageRequests)
        assertEquals(2, service.topCalls)
        assertEquals(2, service.bannerCalls)
    }

    @Test
    fun failedRefreshKeepsOldPagesAndTheirNextPageNumber() {
        val service = FakeHomeService()
        val model = createModel(service)
        success(model)
        instrumentation.runOnMainSync { model.loadMore() }
        val before = success(model)
        instrumentation.runOnMainSync {
            service.pageAction = { page ->
                if (page == 0) throw IOException("offline")
                service.response(page)
            }
            model.refresh()
        }
        val failed = success(model)
        assertEquals(before.articles, failed.articles)
        assertEquals(before.refreshVersion, failed.refreshVersion)
        assertNotNull(failed.refreshError)
        assertFalse(failed.isRefreshing)
        instrumentation.runOnMainSync { model.loadMore() }
        assertEquals(listOf(1, 2, 3, 4), success(model).articles.map { it.id })
        assertEquals(listOf(0, 1, 0, 2), service.pageRequests)
    }

    @Test
    fun initialFailureCanBeRetried() {
        val service = FakeHomeService()
        service.pageAction = { throw IOException("offline") }
        val model = createModel(service)
        awaitState(model) { it is HomeUiState.Error }
        instrumentation.runOnMainSync {
            service.pageAction = { page -> service.response(page) }
            model.refresh()
        }
        assertEquals(listOf(1, 2), success(model).articles.map { it.id })
        assertEquals(listOf(0, 0), service.pageRequests)
    }

    @Test
    fun serverEndFlagStopsAllFurtherPageRequests() {
        val service = FakeHomeService()
        service.pages[0] = ArticlePage(listOf(Article(id = 2)), over = true)
        val model = createModel(service)
        assertTrue(success(model).endReached)
        instrumentation.runOnMainSync { repeat(3) { model.loadMore() } }
        assertEquals(listOf(0), service.pageRequests)
    }

    @Test
    fun emptyPageStopsFurtherRequestsEvenIfServerOverFlagIsFalse() {
        val service = FakeHomeService()
        service.pages[1] = ArticlePage(emptyList(), over = false)
        val model = createModel(service)
        success(model)
        instrumentation.runOnMainSync { model.loadMore() }
        val state = success(model)
        assertEquals(listOf(1, 2), state.articles.map { it.id })
        assertTrue(state.endReached)
        instrumentation.runOnMainSync { model.loadMore() }
        assertEquals(listOf(0, 1), service.pageRequests)
    }

    @Test
    fun pendingPageBlocksDuplicateRequestsAndConcurrentRefresh() {
        val service = FakeHomeService()
        val gate = CompletableDeferred<ApiResponse<ArticlePage>>()
        service.pageAction = { page -> if (page == 1) gate.await() else service.response(page) }
        val model = createModel(service)
        success(model)
        instrumentation.runOnMainSync {
            model.loadMore()
            assertTrue((model.uiState.value as HomeUiState.Success).isLoadingMore)
            repeat(3) { model.loadMore(); model.refresh() }
        }
        assertEquals(listOf(0, 1), service.pageRequests)
        assertEquals(1, service.topCalls)
        gate.complete(service.response(1))
        assertEquals(listOf(1, 2, 3), success(model).articles.map { it.id })
    }

    @Test
    fun swipeRefreshAndFooterRetryUpdateTheActualHomeView() {
        val service = FakeHomeService()
        service.top = listOf(Article(id = 1, title = "置顶：下拉刷新与分页演示", author = "教程"))
        service.pages[0] = ArticlePage((2..21).map {
            Article(id = it, title = "首页文章 $it：继续上拉查看更多", author = "示例数据", chapterName = "Android")
        })
        val refreshGate = CompletableDeferred<ApiResponse<ArticlePage>>()
        val nextPageGate = CompletableDeferred<ApiResponse<ArticlePage>>()
        var firstPageCalls = 0
        var moreAttempts = 0
        service.pageAction = { page ->
            if (page == 0) {
                firstPageCalls += 1
                if (firstPageCalls == 1) service.response(0) else refreshGate.await()
            } else {
                moreAttempts += 1
                if (moreAttempts == 1) throw IOException("offline")
                nextPageGate.await()
            }
        }
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var home: com.example.wanandroid.ui.home.HomeFragment
            lateinit var model: HomeViewModel
            val callback = object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentCreated(
                    manager: androidx.fragment.app.FragmentManager,
                    fragment: androidx.fragment.app.Fragment,
                    savedInstanceState: android.os.Bundle?
                ) {
                    if (fragment !is com.example.wanandroid.ui.home.HomeFragment) return
                    val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            HomeViewModel(HomeRepository(service)) as T
                    }
                    model = androidx.lifecycle.ViewModelProvider(fragment, factory)[HomeViewModel::class.java]
                }
            }
            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.registerFragmentLifecycleCallbacks(callback, false)
                manager.findFragmentByTag("home")?.let { manager.beginTransaction().remove(it).commitNow() }
                home = com.example.wanandroid.ui.home.HomeFragment()
                manager.beginTransaction().add(R.id.fragment_container, home, "home")
                    .setPrimaryNavigationFragment(home).commitNow()
            }
            success(model)
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withId(R.id.swipe_refresh)
            ).perform(androidx.test.espresso.action.ViewActions.swipeDown())
            scenario.onActivity {
                assertTrue((model.uiState.value as HomeUiState.Success).isRefreshing)
                assertTrue(home.requireView().findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing)
            }
            saveScreenshot("refreshing")
            val refreshedArticles = (100..119).map {
                Article(id = it, title = "刷新后的文章 $it", author = "示例数据", chapterName = "Android")
            }
            refreshGate.complete(ApiResponse(errorCode = 0, data = ArticlePage(refreshedArticles)))
            val refreshed = success(model)
            assertEquals(listOf(1) + (100..119), refreshed.articles.map { it.id })
            scenario.onActivity {
                assertFalse(home.requireView().findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing)
                val recycler = home.requireView().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_articles)
                recycler.scrollToPosition(maxOf(0, recycler.adapter!!.itemCount - 8))
            }
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withId(R.id.recycler_articles)
            ).perform(androidx.test.espresso.action.ViewActions.swipeUp())
            awaitState(model) { it is HomeUiState.Success && it.loadMoreError != null }
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withId(R.id.button_load_more)
            ).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
            ))
            saveScreenshot("paging-error")
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withId(R.id.button_load_more)
            ).perform(androidx.test.espresso.action.ViewActions.click())
            scenario.onActivity {
                assertTrue((model.uiState.value as HomeUiState.Success).isLoadingMore)
            }
            saveScreenshot("paging-loading")
            nextPageGate.complete(ApiResponse(errorCode = 0, data = ArticlePage(
                listOf(Article(id = 200, title = "最后一页：加载成功", author = "示例数据")), over = true
            )))
            assertTrue(success(model).endReached)
            scenario.onActivity {
                val recycler = home.requireView().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_articles)
                recycler.scrollToPosition(recycler.adapter!!.itemCount - 1)
            }
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withId(R.id.text_load_more)
            ).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                androidx.test.espresso.matcher.ViewMatchers.withText(R.string.home_end_reached)
            ))
            saveScreenshot("paging-end")
            assertEquals(listOf(0, 0, 1, 1), service.pageRequests)
            scenario.onActivity { it.supportFragmentManager.unregisterFragmentLifecycleCallbacks(callback) }
        }
    }

    private fun saveScreenshot(name: String) {
        instrumentation.waitForIdleSync()
        val frames = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val choreographer = android.view.Choreographer.getInstance()
            choreographer.postFrameCallback {
                choreographer.postFrameCallback { frames.countDown() }
            }
        }
        assertTrue(frames.await(5, TimeUnit.SECONDS))
        // Let RecyclerView's insertion/change animations finish before capturing teaching images.
        android.os.SystemClock.sleep(300)
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("无法取得设备截图")
        val directory = java.io.File(instrumentation.targetContext.filesDir, "codex-chapter15-ui")
        directory.mkdirs()
        java.io.File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun createModel(service: FakeHomeService): HomeViewModel {
        lateinit var model: HomeViewModel
        instrumentation.runOnMainSync {
            model = HomeViewModel(HomeRepository(service))
            val store = ViewModelStore()
            store.put("home", model)
            stores.add(store)
        }
        return model
    }

    private fun success(model: HomeViewModel): HomeUiState.Success = awaitState(model) {
        it is HomeUiState.Success && !it.isRefreshing && !it.isLoadingMore
    } as HomeUiState.Success

    private fun awaitState(model: HomeViewModel, predicate: (HomeUiState) -> Boolean): HomeUiState {
        val result = AtomicReference<HomeUiState>()
        val latch = CountDownLatch(1)
        val observer = Observer<HomeUiState> { state ->
            if (predicate(state)) {
                result.set(state)
                latch.countDown()
            }
        }
        instrumentation.runOnMainSync { model.uiState.observeForever(observer) }
        try {
            assertTrue("没有在 10 秒内获得预期首页状态", latch.await(10, TimeUnit.SECONDS))
            return result.get()
        } finally {
            instrumentation.runOnMainSync { model.uiState.removeObserver(observer) }
        }
    }

    private class FakeHomeService : ApiService by ApiClient.service {
        val pageRequests = mutableListOf<Int>()
        var topCalls = 0
        var bannerCalls = 0
        var top = listOf(Article(id = 1))
        val pages = mutableMapOf(
            0 to ArticlePage(listOf(Article(id = 1), Article(id = 2))),
            1 to ArticlePage(listOf(Article(id = 2), Article(id = 3))),
            2 to ArticlePage(listOf(Article(id = 4)), over = true)
        )
        var pageAction: suspend (Int) -> ApiResponse<ArticlePage> = { response(it) }

        fun response(page: Int) = ApiResponse(
            errorCode = 0, data = pages[page] ?: ArticlePage(emptyList(), over = true)
        )

        override suspend fun getHomeArticles(page: Int): ApiResponse<ArticlePage> {
            pageRequests.add(page)
            return pageAction(page)
        }

        override suspend fun getTopArticles(): ApiResponse<List<Article>> {
            topCalls += 1
            return ApiResponse(errorCode = 0, data = top)
        }

        override suspend fun getBanners(): ApiResponse<List<HomeBanner>> {
            bannerCalls += 1
            return ApiResponse(errorCode = 0, data = emptyList())
        }
    }
}
