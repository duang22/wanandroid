package com.example.wanandroid

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.data.remote.ApiResponse
import com.example.wanandroid.data.remote.ArticlePage
import com.example.wanandroid.data.repository.CollectRepository
import com.example.wanandroid.data.repository.KnowledgeRepository
import com.example.wanandroid.data.repository.SearchRepository
import com.example.wanandroid.ui.collect.MyCollectActivity
import com.example.wanandroid.ui.collect.MyCollectViewModel
import com.example.wanandroid.ui.knowledge.KnowledgeFragment
import com.example.wanandroid.ui.knowledge.KnowledgeViewModel
import com.example.wanandroid.ui.search.SearchActivity
import com.example.wanandroid.ui.search.SearchViewModel
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListPagingUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun searchViewScrollsRetriesEndsAndStartsNewKeywordAtPageZero() {
        val service = FakeListService()
        var moreAttempts = 0
        val gate = CompletableDeferred<ApiResponse<ArticlePage>>()
        service.searchAction = { page, word ->
            if (word == "Kotlin" && page == 1) {
                moreAttempts += 1
                if (moreAttempts == 1) throw IOException("offline")
                gate.await()
            } else ok(ArticlePage(articles(if (word == "Kotlin") 1..20 else 100..104), over = word != "Kotlin"))
        }
        lateinit var model: SearchViewModel
        launchSeeded(SearchActivity::class.java) {
            model = seed(it, SearchViewModel::class.java) { SearchViewModel(SearchRepository(service)) }
        }.use { scenario ->
            onView(withId(R.id.edit_keyword)).perform(replaceText("Kotlin"), closeSoftKeyboard())
            onView(withId(R.id.button_search)).perform(click())
            scenario.onActivity { assertEquals(20, model.uiState.value!!.articles.size) }
            scrollNearEnd(scenario, R.id.recycler_results)
            onView(withId(R.id.button_load_more)).check(matches(isDisplayed()))
            onView(withId(R.id.text_load_more)).check(matches(withText(org.hamcrest.Matchers.containsString("搜索结果加载失败，请重试"))))
            saveScreenshot("search-retry")
            onView(withId(R.id.button_load_more)).perform(click())
            scenario.onActivity { assertTrue(model.uiState.value!!.isLoadingMore) }
            saveScreenshot("search-loading")
            gate.complete(ok(ArticlePage(articles(21..23), over = true)))
            settle()
            scenario.onActivity { activity ->
                assertEquals(23, model.uiState.value!!.articles.size)
                assertTrue(model.uiState.value!!.endReached)
                val recycler = activity.findViewById<RecyclerView>(R.id.recycler_results)
                recycler.scrollToPosition(recycler.adapter!!.itemCount - 1)
            }
            onView(withId(R.id.text_load_more)).check(matches(withText(R.string.home_end_reached)))
            saveScreenshot("search-end")
            scenario.recreate()
            scenario.onActivity { assertEquals(23, model.uiState.value!!.articles.size) }
            assertEquals(listOf(0 to "Kotlin", 1 to "Kotlin", 1 to "Kotlin"), service.searchRequests)
            onView(withId(R.id.edit_keyword)).perform(replaceText("Room"), closeSoftKeyboard())
            onView(withId(R.id.button_search)).perform(click())
            scenario.onActivity { assertEquals((100..104).toList(), model.uiState.value!!.articles.map { it.id }) }
            saveScreenshot("search-new-keyword")
            assertEquals(0 to "Room", service.searchRequests.last())
        }
    }

    @Test fun knowledgeViewAppendsThenResetsWhenUserSelectsAnotherChild() {
        val service = FakeListService()
        service.knowledgeAction = { page, child ->
            ok(ArticlePage(articles(if (child == 101) {
                if (page == 0) 1..20 else 21..24
            } else 100..104), over = page > 0 || child != 101))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: KnowledgeViewModel
            val callback = object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentCreated(manager: FragmentManager, item: Fragment, savedState: Bundle?) {
                    if (item !is KnowledgeFragment) return
                    model = seed(item, KnowledgeViewModel::class.java) { KnowledgeViewModel(KnowledgeRepository(service)) }
                }
            }
            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.registerFragmentLifecycleCallbacks(callback, false)
            }
            onView(withId(R.id.nav_knowledge)).perform(click())
            scenario.onActivity { assertEquals(20, model.uiState.value!!.articles.size) }
            scrollNearEnd(scenario, R.id.recycler_knowledge_articles)
            scenario.onActivity { assertEquals(24, model.uiState.value!!.articles.size) }
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recycler_knowledge_articles)
                recycler.scrollToPosition(recycler.adapter!!.itemCount - 1)
            }
            saveScreenshot("knowledge-end")
            onView(withText("进阶")).perform(click())
            scenario.onActivity { assertEquals((100..104).toList(), model.uiState.value!!.articles.map { it.id }) }
            saveScreenshot("knowledge-switch")
            assertEquals(listOf(0 to 101, 1 to 101, 0 to 102), service.knowledgeRequests)
            scenario.onActivity { it.supportFragmentManager.unregisterFragmentLifecycleCallbacks(callback) }
        }
    }

    @Test fun collectionViewCancelsAndBackfillsWithoutSkippingAndKeepsWindowOnResume() {
        val service = FakeListService().apply {
            pageSize = 20
            collections.clear()
            collections.addAll((1..43).map { CollectedArticle(it, 1000 + it,
                "收藏文章 $it：分页与取消收藏", "教程示例", "https://www.wanandroid.com/blog/show/2") })
        }
        lateinit var model: MyCollectViewModel
        launchSeeded(MyCollectActivity::class.java) {
            model = seed(it, MyCollectViewModel::class.java) { MyCollectViewModel(CollectRepository(service)) }
        }.use { scenario ->
            scenario.onActivity { assertEquals(20, model.uiState.value!!.articles.size) }
            scrollNearEnd(scenario, R.id.recycler_my_collect)
            scenario.onActivity { assertEquals(40, model.uiState.value!!.articles.size) }
            scenario.onActivity { it.findViewById<RecyclerView>(R.id.recycler_my_collect).scrollToPosition(0) }
            settle()
            // Only the first visible row's button is clicked; it mutates the fake service, not a real account.
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recycler_my_collect)
                val row = recycler.findViewHolderForAdapterPosition(0) ?: error("Missing collection row")
                row.itemView.findViewById<android.view.View>(R.id.button_remove_collect).performClick()
            }
            scenario.onActivity {
                val state = model.uiState.value!!
                assertEquals((2..41).toList(), state.articles.map { it.id })
                assertFalse(state.isRemoving)
            }
            saveScreenshot("collection-synced")
            scrollNearEnd(scenario, R.id.recycler_my_collect)
            scenario.onActivity { activity ->
                assertEquals((2..43).toList(), model.uiState.value!!.articles.map { it.id })
                assertTrue(model.uiState.value!!.endReached)
                val recycler = activity.findViewById<RecyclerView>(R.id.recycler_my_collect)
                recycler.scrollToPosition(recycler.adapter!!.itemCount - 1)
            }
            saveScreenshot("collection-end")
            assertEquals(listOf(0, 1, 0, 1, 2), service.collectRequests)
            scenario.recreate()
            scenario.onActivity { assertEquals((2..43).toList(), model.uiState.value!!.articles.map { it.id }) }
            assertEquals(listOf(0, 1, 0, 1, 2, 0, 1, 2), service.collectRequests)
            assertEquals(listOf(1 to 1001), service.removals)
        }
    }

    private fun <A : ComponentActivity> launchSeeded(cls: Class<A>, initialize: (A) -> Unit): ActivityScenario<A> {
        val app = instrumentation.targetContext.applicationContext as Application
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, state: Bundle?) {
                if (cls.isInstance(activity)) initialize(cls.cast(activity)!!)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) { }
            override fun onActivityStarted(activity: Activity) { }
            override fun onActivityResumed(activity: Activity) { }
            override fun onActivityPaused(activity: Activity) { }
            override fun onActivityStopped(activity: Activity) { }
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) { }
            override fun onActivityDestroyed(activity: Activity) { }
        }
        instrumentation.runOnMainSync { app.registerActivityLifecycleCallbacks(callback) }
        return try { ActivityScenario.launch(cls) }
        finally { instrumentation.runOnMainSync { app.unregisterActivityLifecycleCallbacks(callback) } }
    }

    private fun <T : ViewModel> seed(owner: androidx.lifecycle.ViewModelStoreOwner, cls: Class<T>, factory: () -> T): T =
        ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = factory() as V
        })[cls]

    private fun articles(ids: IntRange) = ids.map { Article(it, title = "文章 $it：分页加载示例", author = "教程示例", chapterName = "Android") }

    private fun <A : Activity> scrollNearEnd(scenario: ActivityScenario<A>, recyclerId: Int) {
        scenario.onActivity { activity ->
            val recycler = activity.findViewById<RecyclerView>(recyclerId)
            recycler.scrollToPosition(maxOf(0, recycler.adapter!!.itemCount - 8))
        }
        onView(withId(recyclerId)).perform(swipeUp())
    }

    private fun settle() {
        instrumentation.waitForIdleSync()
        val frames = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val choreographer = android.view.Choreographer.getInstance()
            choreographer.postFrameCallback { choreographer.postFrameCallback { frames.countDown() } }
        }
        assertTrue(frames.await(5, TimeUnit.SECONDS))
        android.os.SystemClock.sleep(300)
        instrumentation.waitForIdleSync()
    }
    private fun saveScreenshot(name: String) {
        settle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val folder = java.io.File(instrumentation.targetContext.filesDir, "codex-chapter16-ui").apply { mkdirs() }
        java.io.File(folder, "$name.png").outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
