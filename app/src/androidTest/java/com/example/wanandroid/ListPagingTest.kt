package com.example.wanandroid

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.data.model.CollectedPage
import com.example.wanandroid.data.model.HotKeyword
import com.example.wanandroid.data.model.KnowledgeCategory
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiResponse
import com.example.wanandroid.data.remote.ApiService
import com.example.wanandroid.data.remote.ArticlePage
import com.example.wanandroid.data.repository.CollectRepository
import com.example.wanandroid.data.repository.KnowledgeRepository
import com.example.wanandroid.data.repository.SearchRepository
import com.example.wanandroid.ui.collect.MyCollectUiState
import com.example.wanandroid.ui.collect.MyCollectViewModel
import com.example.wanandroid.ui.knowledge.KnowledgeUiState
import com.example.wanandroid.ui.knowledge.KnowledgeViewModel
import com.example.wanandroid.ui.search.SearchUiState
import com.example.wanandroid.ui.search.SearchViewModel
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListPagingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val stores = mutableListOf<ViewModelStore>()
    @After fun clearViewModels() { main { stores.forEach { it.clear() } } }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun <T : ViewModel> create(factory: () -> T): T {
        lateinit var result: T
        main {
            result = factory()
            stores.add(ViewModelStore().apply { put("test", result) })
        }
        return result
    }
    private fun <T> await(data: LiveData<T>, predicate: (T) -> Boolean): T {
        val latch = CountDownLatch(1)
        val result = AtomicReference<T>()
        val observer = Observer<T> { if (predicate(it)) { result.set(it); latch.countDown() } }
        main { data.observeForever(observer) }
        try { assertTrue("Timed out. Current state: ${data.value}", latch.await(8, TimeUnit.SECONDS)) }
        finally { main { data.removeObserver(observer) } }
        return result.get()
    }
    private fun searchReady(model: SearchViewModel): SearchUiState =
        await(model.uiState) { !it.isLoading && !it.isLoadingMore }
    private fun knowledgeReady(model: KnowledgeViewModel): KnowledgeUiState =
        await(model.uiState) { !it.isLoading && !it.isLoadingMore }
    private fun collectReady(model: MyCollectViewModel): MyCollectUiState =
        await(model.uiState) { !it.isLoading && !it.isLoadingMore && !it.isRemoving }

    @Test fun searchRetryKeepsPageAndKeywordAndAppendsUniqueArticles() {
        val service = FakeListService()
        var fail = true
        service.searchAction = { page, _ ->
            if (page == 1 && fail) { fail = false; throw IOException("offline") }
            ok(ArticlePage(if (page == 0) listOf(article(1), article(2)) else listOf(article(2), article(3)), over = page > 0))
        }
        val model = create { SearchViewModel(SearchRepository(service)) }
        main { model.search("  Kotlin  ") }
        assertEquals(listOf(1, 2), searchReady(model).articles.map { it.id })
        main { model.loadMore() }
        assertNotNull(searchReady(model).loadMoreError)
        main { model.loadMore() }
        val result = searchReady(model)
        assertEquals(listOf(1, 2, 3), result.articles.map { it.id })
        assertTrue(result.endReached)
        main { repeat(3) { model.loadMore() } }
        assertEquals(listOf(0 to "Kotlin", 1 to "Kotlin", 1 to "Kotlin"), service.searchRequests)
    }

    @Test fun switchingKeywordIgnoresLatePreviousPageAndBlocksDuplicates() {
        val service = FakeListService()
        lateinit var pending: Continuation<ApiResponse<ArticlePage>>
        service.searchAction = { page, word ->
            if (word == "Kotlin" && page == 1) suspendCoroutine { pending = it }
            else ok(ArticlePage(listOf(article(if (word == "Kotlin") 1 else 9 + page))))
        }
        val model = create { SearchViewModel(SearchRepository(service)) }
        main { model.search("Kotlin"); model.loadMore(); repeat(3) { model.loadMore() } }
        assertEquals(2, service.searchRequests.size)
        main { model.search("Room") }
        assertEquals(listOf(9), searchReady(model).articles.map { it.id })
        main { pending.resume(ok(ArticlePage(listOf(article(99)), over = true))) }
        assertEquals(listOf(9), searchReady(model).articles.map { it.id })
        main { model.loadMore() }
        assertEquals(listOf(9, 10), searchReady(model).articles.map { it.id })
        assertEquals(listOf(0 to "Kotlin", 1 to "Kotlin", 0 to "Room", 1 to "Room"), service.searchRequests)
    }

    @Test fun searchInitialFailureRetriesAndEmptyResultStopsPaging() {
        val service = FakeListService()
        service.searchAction = { _, _ -> throw IOException("offline") }
        val model = create { SearchViewModel(SearchRepository(service)) }
        main { model.search("Kotlin") }
        assertTrue(searchReady(model).message.contains("失败"))
        main { service.searchAction = { _, _ -> ok(ArticlePage(emptyList())) }; model.search("Kotlin") }
        assertTrue(searchReady(model).endReached)
        main { model.loadMore() }
        assertEquals(listOf(0 to "Kotlin", 0 to "Kotlin"), service.searchRequests)
    }

    @Test fun knowledgeRetryKeepsCategoryAndPageAndStopsAtEnd() {
        val service = FakeListService()
        var fail = true
        service.knowledgeAction = { page, _ ->
            if (page == 1 && fail) { fail = false; throw IOException("offline") }
            ok(ArticlePage(if (page == 0) listOf(article(1), article(2)) else listOf(article(2), article(3)), over = page > 0))
        }
        val model = create { KnowledgeViewModel(KnowledgeRepository(service)) }
        assertEquals(101, knowledgeReady(model).selectedChildId)
        main { model.loadMore() }
        assertNotNull(knowledgeReady(model).loadMoreError)
        assertEquals(2, model.uiState.value!!.articles.size)
        main { model.loadMore() }
        val result = knowledgeReady(model)
        assertEquals(listOf(1, 2, 3), result.articles.map { it.id })
        assertTrue(result.endReached)
        main { model.loadMore() }
        assertEquals(listOf(0 to 101, 1 to 101, 1 to 101), service.knowledgeRequests)
    }

    @Test fun switchingChildIgnoresLateResultAndRestartsAtPageZero() {
        val service = FakeListService()
        lateinit var pending: Continuation<ApiResponse<ArticlePage>>
        service.knowledgeAction = { page, child ->
            if (child == 101 && page == 1) suspendCoroutine { pending = it }
            else ok(ArticlePage(listOf(article(child * 10 + page))))
        }
        val model = create { KnowledgeViewModel(KnowledgeRepository(service)) }
        knowledgeReady(model)
        main { model.loadMore(); repeat(3) { model.loadMore() }; model.selectChild(102) }
        assertEquals(listOf(1020), knowledgeReady(model).articles.map { it.id })
        main { pending.resume(ok(ArticlePage(listOf(article(999)), over = true))) }
        assertEquals(listOf(1020), knowledgeReady(model).articles.map { it.id })
        main { model.loadMore() }
        assertEquals(listOf(1020, 1021), knowledgeReady(model).articles.map { it.id })
        assertEquals(listOf(0 to 101, 1 to 101, 0 to 102, 1 to 102), service.knowledgeRequests)
    }

    @Test fun selectingParentWithoutChildrenClearsListAndStopsRequests() {
        val service = FakeListService()
        val model = create { KnowledgeViewModel(KnowledgeRepository(service)) }
        knowledgeReady(model)
        main { model.selectParent(20); model.loadMore(); model.selectChild(101) }
        val result = knowledgeReady(model)
        assertEquals(20, result.selectedParentId)
        assertNull(result.selectedChildId)
        assertTrue(result.articles.isEmpty())
        assertTrue(result.endReached)
        assertEquals(listOf(0 to 101), service.knowledgeRequests)
    }

    @Test fun knowledgeInitialFailureRetriesTheSameCategory() {
        val service = FakeListService()
        service.knowledgeAction = { _, _ -> throw IOException("offline") }
        val model = create { KnowledgeViewModel(KnowledgeRepository(service)) }
        assertTrue(knowledgeReady(model).canRetry)
        main { service.knowledgeAction = { _, _ -> ok(ArticlePage(listOf(article(8)), over = true)) }; model.retry() }
        assertEquals(listOf(8), knowledgeReady(model).articles.map { it.id })
        assertEquals(listOf(0 to 101, 0 to 101), service.knowledgeRequests)
    }

    @Test fun removalReloadsLoadedWindowSoShiftedItemsAreNotSkipped() {
        val service = FakeListService()
        val model = create { MyCollectViewModel(CollectRepository(service)) }
        main { model.loadCollections() }; collectReady(model)
        main { model.loadMore() }; collectReady(model)
        main { model.remove(model.uiState.value!!.articles[1]) }
        assertEquals(listOf(1, 3, 4, 5), collectReady(model).articles.map { it.id })
        main { model.loadMore() }
        val result = collectReady(model)
        assertEquals(listOf(1, 3, 4, 5, 6, 7), result.articles.map { it.id })
        assertTrue(result.endReached)
        assertEquals(listOf(0, 1, 0, 1, 2), service.collectRequests)
        assertEquals(listOf(2 to 1002), service.removals)
    }

    @Test fun failedRemovalKeepsTheItemAndCursor() {
        val service = FakeListService()
        service.removeAction = { _, _ -> throw IOException("offline") }
        val model = create { MyCollectViewModel(CollectRepository(service)) }
        main { model.loadCollections() }; collectReady(model)
        main { model.remove(model.uiState.value!!.articles.first()) }
        assertEquals(listOf(1, 2), collectReady(model).articles.map { it.id })
        main { model.loadMore() }
        assertEquals(listOf(1, 2, 3, 4), collectReady(model).articles.map { it.id })
        assertEquals(listOf(0, 1), service.collectRequests)
    }

    @Test fun successfulRemovalWithFailedSyncStaysRemovedAndRetryResyncsBeforeMore() {
        val service = FakeListService()
        val model = create { MyCollectViewModel(CollectRepository(service)) }
        main { model.loadCollections(); model.loadMore() }; collectReady(model)
        var fail = true
        service.collectAction = { page ->
            if (page == 0 && fail) { fail = false; throw IOException("offline") }
            service.collectionResponse(page)
        }
        main { model.remove(model.uiState.value!!.articles[1]) }
        val failed = collectReady(model)
        assertEquals(listOf(1, 3, 4), failed.articles.map { it.id })
        assertNotNull(failed.loadMoreError)
        main { model.loadMore() }
        assertEquals(listOf(1, 3, 4, 5), collectReady(model).articles.map { it.id })
        main { model.loadMore() }
        assertEquals(listOf(1, 3, 4, 5, 6, 7), collectReady(model).articles.map { it.id })
        assertEquals(listOf(0, 1, 0, 0, 1, 2), service.collectRequests)
        assertEquals(1, service.removals.size)
    }

    @Test fun collectionPageFailureRetriesAndLoginExpiryClearsOldItems() {
        val service = FakeListService()
        var attempt = 0
        service.collectAction = { page ->
            if (page == 1) {
                attempt += 1
                if (attempt == 1) throw IOException("offline")
                if (attempt == 3) ApiResponse(errorCode = -1001, data = null) else service.collectionResponse(page)
            } else service.collectionResponse(page)
        }
        val model = create { MyCollectViewModel(CollectRepository(service)) }
        main { model.loadCollections(); model.loadMore() }
        assertNotNull(collectReady(model).loadMoreError)
        assertEquals(listOf(1, 2), model.uiState.value!!.articles.map { it.id })
        main { model.loadMore() }
        assertEquals(listOf(1, 2, 3, 4), collectReady(model).articles.map { it.id })
        main { model.loadCollections() }
        val expired = collectReady(model)
        assertTrue(expired.needsLogin)
        assertTrue(expired.articles.isEmpty())
        main { model.loadMore() }
        assertEquals(listOf(0, 1, 1, 0, 1), service.collectRequests)
        main { service.collectAction = { page -> service.collectionResponse(page) }; model.loadCollections() }
        assertEquals(listOf(1, 2), collectReady(model).articles.map { it.id })
        assertFalse(model.uiState.value!!.needsLogin)
        assertEquals(0, service.collectRequests.last())
    }

    @Test fun refreshPreservesLoadedWindowAndNextPage() {
        val service = FakeListService()
        val model = create { MyCollectViewModel(CollectRepository(service)) }
        main { model.loadCollections(); model.loadMore() }; collectReady(model)
        main { service.collections.removeAt(0); model.loadCollections() }
        assertEquals(listOf(2, 3, 4, 5), collectReady(model).articles.map { it.id })
        main { model.loadMore() }
        assertEquals(listOf(2, 3, 4, 5, 6, 7), collectReady(model).articles.map { it.id })
        assertEquals(listOf(0, 1, 0, 1, 2), service.collectRequests)
    }

    @Test fun collectionPendingPageBlocksDuplicateRequestsAndEmptyPageStopsFurtherLoading() {
        val service = FakeListService()
        val gate = CompletableDeferred<ApiResponse<CollectedPage>>()
        service.collectAction = { page -> if (page == 1) gate.await() else service.collectionResponse(page) }
        val model = create { MyCollectViewModel(CollectRepository(service)) }
        main {
            model.loadCollections(); model.loadMore()
            repeat(3) { model.loadMore(); model.loadCollections(); model.remove(model.uiState.value!!.articles.first()) }
        }
        assertEquals(listOf(0, 1), service.collectRequests)
        assertTrue(service.removals.isEmpty())
        gate.complete(ok(CollectedPage(emptyList())))
        assertTrue(collectReady(model).endReached)
        main { model.loadMore() }
        assertEquals(listOf(0, 1), service.collectRequests)
    }

    private fun article(id: Int) = Article(id = id, title = "Article $id")
}

internal fun <T> ok(data: T) = ApiResponse(errorCode = 0, data = data)

internal class FakeListService : ApiService by ApiClient.service {
    val searchRequests = CopyOnWriteArrayList<Pair<Int, String>>()
    val knowledgeRequests = CopyOnWriteArrayList<Pair<Int, Int>>()
    val collectRequests = CopyOnWriteArrayList<Int>()
    val removals = CopyOnWriteArrayList<Pair<Int, Int>>()
    var searchAction: suspend (Int, String) -> ApiResponse<ArticlePage> = { _, _ -> ok(ArticlePage(listOf(Article(id = 1)))) }
    var knowledgeAction: suspend (Int, Int) -> ApiResponse<ArticlePage> = { _, _ -> ok(ArticlePage(listOf(Article(id = 1)))) }
    val collections = (1..7).map { CollectedArticle(id = it, originId = 1000 + it,
        title = "收藏文章 $it", author = "教程示例", link = "https://www.wanandroid.com/blog/show/2") }.toMutableList()
    var pageSize = 2
    var collectAction: suspend (Int) -> ApiResponse<CollectedPage> = { collectionResponse(it) }
    var removeAction: suspend (Int, Int) -> ApiResponse<Any> = { id, _ ->
        collections.removeAll { it.id == id }; ApiResponse(errorCode = 0, data = null)
    }
    fun collectionResponse(page: Int): ApiResponse<CollectedPage> = ok(CollectedPage(
        collections.drop(page * pageSize).take(pageSize), over = (page + 1) * pageSize >= collections.size
    ))
    override suspend fun getHotKeywords(): ApiResponse<List<HotKeyword>> = ok(emptyList())
    override suspend fun searchArticles(page: Int, keyword: String): ApiResponse<ArticlePage> {
        searchRequests.add(page to keyword); return searchAction(page, keyword)
    }
    override suspend fun getKnowledgeTree(): ApiResponse<List<KnowledgeCategory>> = ok(listOf(
        KnowledgeCategory(10, "Android", listOf(KnowledgeCategory(101, "基础"), KnowledgeCategory(102, "进阶"))),
        KnowledgeCategory(20, "空分类", emptyList())
    ))
    override suspend fun getKnowledgeArticles(page: Int, categoryId: Int): ApiResponse<ArticlePage> {
        knowledgeRequests.add(page to categoryId); return knowledgeAction(page, categoryId)
    }
    override suspend fun getMyCollections(page: Int): ApiResponse<CollectedPage> {
        collectRequests.add(page); return collectAction(page)
    }
    override suspend fun removeCollection(collectionId: Int, originId: Int): ApiResponse<Any> {
        removals.add(collectionId to originId); return removeAction(collectionId, originId)
    }
}
