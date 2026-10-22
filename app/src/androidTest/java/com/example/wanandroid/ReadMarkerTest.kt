package com.example.wanandroid

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wanandroid.data.local.AppDatabase
import com.example.wanandroid.data.local.ReadRecord
import com.example.wanandroid.data.model.HomeBanner
import com.example.wanandroid.data.model.HotKeyword
import com.example.wanandroid.data.model.KnowledgeCategory
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiResponse
import com.example.wanandroid.data.remote.ApiService
import com.example.wanandroid.data.remote.ArticlePage
import com.example.wanandroid.data.repository.HomeRepository
import com.example.wanandroid.data.repository.KnowledgeRepository
import com.example.wanandroid.data.repository.ReadHistoryRepository
import com.example.wanandroid.data.repository.SearchRepository
import com.example.wanandroid.ui.history.ReadHistoryViewModel
import com.example.wanandroid.ui.home.HomeFragment
import com.example.wanandroid.ui.home.HomeViewModel
import com.example.wanandroid.ui.knowledge.KnowledgeFragment
import com.example.wanandroid.ui.knowledge.KnowledgeViewModel
import com.example.wanandroid.ui.search.SearchActivity
import com.example.wanandroid.ui.search.SearchViewModel
import com.example.wanandroidlearn.ui.article.ArticleActivity
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadMarkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun readingUpdatesAllThreeListsAndDeletingHistoryRestoresUnreadColors() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val root = "https://example.invalid/read-marker-${UUID.randomUUID()}"
        try {
            Fixture(database, root).use {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    awaitColors(scenario, R.id.recycler_articles, mapOf(0 to false, 1 to false))
                    val monitor = instrumentation.addMonitor(ArticleActivity::class.java.name, null, false)
                    try {
                        onView(withText("已读标记验证文章 0")).perform(click())
                        val reader = instrumentation.waitForMonitorWithTimeout(monitor, 8_000)
                        assertNotNull("Article row did not open the reader", reader)
                        awaitRecorded(database, "$root/0")
                        instrumentation.runOnMainSync { reader.finish() }
                    } finally {
                        instrumentation.removeMonitor(monitor)
                    }
                    awaitColors(scenario, R.id.recycler_articles, mapOf(0 to true, 1 to false))
                    saveScreenshot("home-read")

                    // Recycle the gray row into a screen of unread rows: colors must not leak.
                    scenario.onActivity { activity ->
                        activity.findViewById<RecyclerView>(R.id.recycler_articles).apply {
                            setItemViewCacheSize(0)
                            scrollToPosition(12)
                        }
                    }
                    awaitVisibleUnreadRows(scenario)
                    scenario.onActivity { it.findViewById<RecyclerView>(R.id.recycler_articles).scrollToPosition(0) }
                    awaitColors(scenario, R.id.recycler_articles, mapOf(0 to true, 1 to false))

                    onView(withId(R.id.button_search_articles)).perform(click())
                    val search = instrumentation.waitForIdleSync().let { currentSearch }
                    assertNotNull("Search page did not start", search)
                    instrumentation.runOnMainSync {
                        ViewModelProvider(search!!)[SearchViewModel::class.java].search("read marker")
                    }
                    awaitActivityColors(search!!, R.id.recycler_results, mapOf(0 to true, 1 to false))
                    instrumentation.runOnMainSync { search.finish() }

                    onView(withId(R.id.nav_knowledge)).perform(click())
                    awaitColors(scenario, R.id.recycler_knowledge_articles, mapOf(0 to true, 1 to false))
                    runBlocking { database.readHistoryDao().delete("$root/0") }
                    awaitColors(scenario, R.id.recycler_knowledge_articles, mapOf(0 to false, 1 to false))

                    runBlocking {
                        database.readHistoryDao().save(ReadRecord("$root/0", "第一篇", 1))
                        database.readHistoryDao().save(ReadRecord("$root/1", "第二篇", 2))
                    }
                    awaitColors(scenario, R.id.recycler_knowledge_articles, mapOf(0 to true, 1 to true))
                    runBlocking { database.readHistoryDao().clear() }
                    awaitColors(scenario, R.id.recycler_knowledge_articles, mapOf(0 to false, 1 to false))
                    onView(withId(R.id.nav_home)).perform(click())
                    awaitColors(scenario, R.id.recycler_articles, mapOf(0 to false, 1 to false))
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun reopeningDatabaseAndCreatingNewPageRetainsReadColorsWithoutNetworkHistory() {
        val name = "read-marker-test-${UUID.randomUUID()}.db"
        val root = "https://example.invalid/read-marker-${UUID.randomUUID()}"
        var database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            runBlocking { database.readHistoryDao().save(ReadRecord("$root/0", "已读文章", 1)) }
            Fixture(database, root).use {
                ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
                    scenario.onActivity { ViewModelProvider(it)[SearchViewModel::class.java].search("test") }
                    awaitColors(scenario, R.id.recycler_results, mapOf(0 to true, 1 to false))
                }
            }
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            Fixture(database, root).use {
                ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
                    scenario.onActivity { ViewModelProvider(it)[SearchViewModel::class.java].search("test") }
                    awaitColors(scenario, R.id.recycler_results, mapOf(0 to true, 1 to false))
                    scenario.recreate()
                    awaitColors(scenario, R.id.recycler_results, mapOf(0 to true, 1 to false))
                }
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun <T> response(data: T) = ApiResponse(errorCode = 0, data = data)

    private fun awaitRecorded(database: AppDatabase, url: String) {
        val records = database.readHistoryDao().observeAll()
        val saved = CountDownLatch(1)
        val observer = Observer<List<ReadRecord>> { if (it.any { record -> record.url == url }) saved.countDown() }
        instrumentation.runOnMainSync { records.observeForever(observer) }
        try { assertTrue("Reader did not save its URL", saved.await(8, TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { records.removeObserver(observer) } }
    }

    private var currentSearch: SearchActivity? = null

    private inner class Fixture(database: AppDatabase, root: String) : AutoCloseable {
        private val repository = ReadHistoryRepository(database.readHistoryDao())
        private val articles = (0..19).map {
            Article(id = it, title = "已读标记验证文章 $it", author = "教程示例",
                link = "$root/$it", chapterName = "Android")
        }
        private val service = object : ApiService by ApiClient.service {
            override suspend fun getBanners(): ApiResponse<List<HomeBanner>> = response(emptyList())
            override suspend fun getTopArticles(): ApiResponse<List<Article>> = response(emptyList())
            override suspend fun getHomeArticles(page: Int) = response(ArticlePage(articles, over = true))
            override suspend fun getHotKeywords(): ApiResponse<List<HotKeyword>> = response(emptyList())
            override suspend fun searchArticles(page: Int, keyword: String) = response(ArticlePage(articles, over = true))
            override suspend fun getKnowledgeArticles(page: Int, categoryId: Int) = response(ArticlePage(articles, over = true))
            override suspend fun getKnowledgeTree() = response(listOf(
                KnowledgeCategory(10, "Android", listOf(KnowledgeCategory(101, "基础")))
            ))
        }
        private val fragments = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentCreated(manager: FragmentManager, item: Fragment, state: Bundle?) {
                when (item) {
                    is HomeFragment -> {
                        seed(item, HomeViewModel::class.java) { HomeViewModel(HomeRepository(service)) }
                        seed(item, ReadHistoryViewModel::class.java) { ReadHistoryViewModel(repository) }
                    }
                    is KnowledgeFragment -> {
                        seed(item, KnowledgeViewModel::class.java) { KnowledgeViewModel(KnowledgeRepository(service)) }
                        seed(item, ReadHistoryViewModel::class.java) { ReadHistoryViewModel(repository) }
                    }
                }
            }
        }
        private val activities = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, state: Bundle?) {
                when (activity) {
                    is MainActivity -> activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragments, false)
                    is SearchActivity -> {
                        currentSearch = activity
                        seed(activity, SearchViewModel::class.java) { SearchViewModel(SearchRepository(service)) }
                        seed(activity, ReadHistoryViewModel::class.java) { ReadHistoryViewModel(repository) }
                    }
                    is ArticleActivity -> seed(activity, ReadHistoryViewModel::class.java) { ReadHistoryViewModel(repository) }
                }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) { }
            override fun onActivityStarted(activity: Activity) { }
            override fun onActivityResumed(activity: Activity) { }
            override fun onActivityPaused(activity: Activity) { }
            override fun onActivityStopped(activity: Activity) { }
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) { }
            override fun onActivityDestroyed(activity: Activity) { if (currentSearch === activity) currentSearch = null }
        }
        private val app = context.applicationContext as Application
        init { instrumentation.runOnMainSync { app.registerActivityLifecycleCallbacks(activities) } }
        override fun close() { instrumentation.runOnMainSync { app.unregisterActivityLifecycleCallbacks(activities) } }
    }

    private fun <T : ViewModel> seed(owner: androidx.lifecycle.ViewModelStoreOwner, cls: Class<T>, create: () -> T): T =
        ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
        })[cls]

    private fun <A : Activity> awaitColors(scenario: ActivityScenario<A>, id: Int, states: Map<Int, Boolean>) {
        waitUntil("List did not display the expected read colors") {
            var matches = false
            scenario.onActivity { matches = colorsMatch(it, id, states) }
            matches
        }
    }
    private fun awaitActivityColors(activity: Activity, id: Int, states: Map<Int, Boolean>) {
        waitUntil("Search did not receive the Room read state") {
            var matches = false
            instrumentation.runOnMainSync { matches = colorsMatch(activity, id, states) }
            matches
        }
    }
    private fun colorsMatch(activity: Activity, id: Int, states: Map<Int, Boolean>): Boolean {
        val recycler = activity.findViewById<RecyclerView>(id) ?: return false
        return states.all { (position, read) ->
            val row = recycler.findViewHolderForAdapterPosition(position)?.itemView ?: return@all false
            val title = row.findViewById<TextView>(R.id.text_title) ?: return@all false
            val author = row.findViewById<TextView>(R.id.text_author)
            val attributes = title.context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
            val primary = attributes.getColorStateList(0)!!.getColorForState(title.drawableState, 0)
            attributes.recycle()
            val expected = if (read) author.currentTextColor else primary
            title.currentTextColor == expected && primary != author.currentTextColor
        }
    }
    private fun awaitVisibleUnreadRows(scenario: ActivityScenario<MainActivity>) {
        waitUntil("Recycled unread rows kept the gray title color") {
            var result = false
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recycler_articles)
                val manager = recycler.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                val positions = manager.findFirstVisibleItemPosition()..manager.findLastVisibleItemPosition()
                result = positions.first >= 2 && positions.all { colorsMatch(activity, R.id.recycler_articles, mapOf(it to false)) }
            }
            result
        }
    }
    private fun waitUntil(message: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }
    private fun saveScreenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val directory = java.io.File(context.filesDir, "codex-read-marker-ui").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
