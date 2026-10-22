package com.example.wanandroid.data.repository

import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.remote.ApiService
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ListPagingRepositoryTest {
    private data class Request(val method: String, val path: String, val query: String?, val body: String)
    private val response = AtomicReference("""{"errorCode":0,"data":{"datas":[{"id":7}],"over":true}}""")
    private val request = AtomicReference<Request>()
    private lateinit var server: HttpServer
    private lateinit var client: OkHttpClient
    private lateinit var service: ApiService

    @Before fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            request.set(Request(exchange.requestMethod, exchange.requestURI.path,
                exchange.requestURI.query, exchange.requestBody.bufferedReader().use { it.readText() }))
            val bytes = response.get().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        client = OkHttpClient()
        service = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
            .client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }

    @After fun tearDown() {
        server.stop(0)
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test fun searchSendsPageAndKeywordAndKeepsEndFlag() = runBlocking {
        val page = SearchRepository(service).searchArticles("Kotlin ViewModel", 2)
        assertEquals("POST", request.get().method)
        assertEquals("/article/query/2/json", request.get().path)
        assertEquals("k=Kotlin ViewModel", java.net.URLDecoder.decode(request.get().body, "UTF-8"))
        assertEquals(7, page.datas!!.single().id)
        assertTrue(page.over)
    }

    @Test fun knowledgeSendsPageAndChildCategory() = runBlocking {
        val page = KnowledgeRepository(service).getArticles(101, 3)
        assertEquals("GET", request.get().method)
        assertEquals("/article/list/3/json", request.get().path)
        assertEquals("cid=101", request.get().query)
        assertTrue(page.over)
    }

    @Test fun collectionSendsRequestedPageAndKeepsEndFlag() = runBlocking {
        val page = CollectRepository(service).getMyCollectionPage(4)
        assertEquals("/lg/collect/list/4/json", request.get().path)
        assertTrue(page.over)
    }

    @Test fun missingListIsAnErrorForAllThreeRepositories() = runBlocking {
        response.set("""{"errorCode":0,"data":{"over":false}}""")
        expectApiError { SearchRepository(service).searchArticles("Kotlin") }
        expectApiError { KnowledgeRepository(service).getArticles(101) }
        expectApiError { CollectRepository(service).getMyCollectionPage(0) }
    }

    @Test fun missingPageIsAnErrorForAllThreeRepositories() = runBlocking {
        response.set("""{"errorCode":0,"data":null}""")
        expectApiError { SearchRepository(service).searchArticles("Kotlin") }
        expectApiError { KnowledgeRepository(service).getArticles(101) }
        expectApiError { CollectRepository(service).getMyCollectionPage(0) }
    }

    @Test fun collectionLoginExpiryIsNotTreatedAsEmptyLastPage() = runBlocking {
        response.set("""{"errorCode":-1001,"data":null}""")
        try { CollectRepository(service).getMyCollectionPage(2); fail("Expected login error") }
        catch (expected: LoginRequiredException) { assertEquals("请先登录后使用收藏", expected.message) }
    }

    private suspend fun expectApiError(action: suspend () -> Any) {
        try { action(); fail("Expected ApiException") } catch (expected: ApiException) { }
    }
}
