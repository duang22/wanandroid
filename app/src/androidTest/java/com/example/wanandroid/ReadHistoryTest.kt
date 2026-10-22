package com.example.wanandroid

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wanandroid.data.local.AppDatabase
import com.example.wanandroid.data.local.ReadRecord
import com.example.wanandroid.data.repository.ReadHistoryRepository
import com.example.wanandroid.ui.history.ReadHistoryActivity
import com.example.wanandroid.ui.history.ReadHistoryViewModel
import com.example.wanandroidlearn.ui.article.ArticleActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReadHistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun sameUrlReplacesRecordAndMovesItToTheFront() = runBlocking {
        val dao = database.readHistoryDao()
        dao.save(ReadRecord("https://example.com/a", "旧标题", 100, 1))
        dao.save(ReadRecord("https://example.com/b", "另一篇", 200, 2))
        dao.save(ReadRecord("https://example.com/a", "更新标题", 300, 99))
        val records = awaitRecords(dao.observeAll()) { it.size == 2 }
        assertEquals(listOf("更新标题", "另一篇"), records.map { it.title })
        assertEquals(300L, records.first().readAt)
        assertEquals(99, records.first().articleId)
    }

    @Test
    fun recordsAreSortedByReadTimeDescending() = runBlocking {
        val dao = database.readHistoryDao()
        dao.save(ReadRecord("https://example.com/a", "A", 100))
        dao.save(ReadRecord("https://example.com/b", "B", 300))
        dao.save(ReadRecord("https://example.com/c", "C", 200))
        val records = awaitRecords(dao.observeAll()) { it.size == 3 }
        assertEquals(listOf("B", "C", "A"), records.map { it.title })
    }

    @Test
    fun deleteAndClearNotifyAnExistingObserver() {
        val dao = database.readHistoryDao()
        val liveData = dao.observeAll()
        val snapshots = java.util.concurrent.LinkedBlockingQueue<List<ReadRecord>>()
        val observer = Observer<List<ReadRecord>> { snapshots.offer(it) }
        instrumentation.runOnMainSync { liveData.observeForever(observer) }
        try {
            runBlocking {
                dao.save(ReadRecord("https://example.com/a", "A", 100))
                dao.save(ReadRecord("https://example.com/b", "B", 200))
            }
            awaitSnapshot(snapshots) { it.size == 2 }
            runBlocking { dao.delete("https://example.com/a") }
            val remaining = awaitSnapshot(snapshots) { it.size == 1 }
            assertEquals("B", remaining.single().title)
            runBlocking { dao.clear() }
            assertTrue(awaitSnapshot(snapshots) { it.isEmpty() }.isEmpty())
        } finally {
            instrumentation.runOnMainSync { liveData.removeObserver(observer) }
        }
    }

    @Test
    fun diskDatabaseRetainsRecordsAfterItIsClosedAndReopened() = runBlocking {
        val name = "history-test-${UUID.randomUUID()}.db"
        var diskDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            diskDatabase.readHistoryDao().save(ReadRecord("https://example.com/a", "持久化", 100, 42))
            diskDatabase.close()
            diskDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val records = awaitRecords(diskDatabase.readHistoryDao().observeAll()) { it.size == 1 }
            assertEquals(ReadRecord("https://example.com/a", "持久化", 100, 42), records.single())
        } finally {
            diskDatabase.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun repositoryUsesUrlWhenTitleIsBlank() = runBlocking {
        val repository = ReadHistoryRepository(database.readHistoryDao())
        repository.save("https://example.com/a", "  ", 123)
        val record = awaitRecords(repository.records) { it.size == 1 }.single()
        assertEquals("https://example.com/a", record.title)
        assertEquals(123, record.articleId)
        assertTrue(record.readAt > 0)
    }

    @Test
    fun readerRecordsItsIntentAndRecreationDoesNotChangeReadTime() {
        val url = "https://example.invalid/history-test-${UUID.randomUUID()}"
        val dao = WanApplication.database.readHistoryDao()
        val intent = Intent(context, ArticleActivity::class.java)
            .putExtra("article_url", url)
            .putExtra("article_title", "阅读历史集成测试")
            .putExtra("article_id", 321)
        try {
            ActivityScenario.launch<ArticleActivity>(intent).use { scenario ->
                val before = awaitRecords(dao.observeAll()) { list -> list.any { it.url == url } }
                    .single { it.url == url }
                assertEquals("阅读历史集成测试", before.title)
                assertEquals(321, before.articleId)
                lateinit var originalModel: ReadHistoryViewModel
                scenario.onActivity { originalModel = ViewModelProvider(it)[ReadHistoryViewModel::class.java] }
                scenario.recreate()
                scenario.onActivity {
                    assertSame(originalModel, ViewModelProvider(it)[ReadHistoryViewModel::class.java])
                }
                val after = awaitRecords(dao.observeAll()) { list -> list.any { it.url == url } }
                    .single { it.url == url }
                assertEquals(before.readAt, after.readAt)
            }
        } finally {
            runBlocking { dao.delete(url) }
        }
    }

    @Test
    fun historyRowReopensReaderWithItsOriginalArticleId() {
        val title = "历史跳转测试-${UUID.randomUUID()}"
        val url = "https://example.invalid/history-reopen-${UUID.randomUUID()}"
        val dao = WanApplication.database.readHistoryDao()
        runBlocking { dao.save(ReadRecord(url, title, System.currentTimeMillis(), 456)) }
        val monitor = instrumentation.addMonitor(ArticleActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(ReadHistoryActivity::class.java).use {
                awaitVisibleText(title)
                onView(withText(title)).perform(click())
                val reader = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
                assertNotNull("历史条目没有打开阅读页", reader)
                instrumentation.runOnMainSync {
                    assertEquals(url, reader.intent.getStringExtra("article_url"))
                    assertEquals(title, reader.intent.getStringExtra("article_title"))
                    assertEquals(456, reader.intent.getIntExtra("article_id", 0))
                    reader.finish()
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            runBlocking { dao.delete(url) }
        }
    }

    private fun awaitVisibleText(title: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            try {
                onView(withText(title)).check(matches(isDisplayed()))
                return
            } catch (error: NoMatchingViewException) {
                if (System.nanoTime() >= deadline) throw error
                Thread.sleep(100)
            }
        }
    }

    private fun awaitRecords(
        liveData: LiveData<List<ReadRecord>>,
        predicate: (List<ReadRecord>) -> Boolean
    ): List<ReadRecord> {
        val result = AtomicReference<List<ReadRecord>>()
        val latch = CountDownLatch(1)
        val observer = Observer<List<ReadRecord>> { records ->
            if (predicate(records)) {
                result.set(records)
                latch.countDown()
            }
        }
        instrumentation.runOnMainSync { liveData.observeForever(observer) }
        try {
            assertTrue("Room 没有在 10 秒内返回预期记录", latch.await(10, TimeUnit.SECONDS))
            return result.get()
        } finally {
            instrumentation.runOnMainSync { liveData.removeObserver(observer) }
        }
    }

    private fun awaitSnapshot(
        snapshots: java.util.concurrent.LinkedBlockingQueue<List<ReadRecord>>,
        predicate: (List<ReadRecord>) -> Boolean
    ): List<ReadRecord> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val records = snapshots.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (predicate(records)) return records
        }
        throw AssertionError("LiveData 没有通知列表变化")
    }
}
