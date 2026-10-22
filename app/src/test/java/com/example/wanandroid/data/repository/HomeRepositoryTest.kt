package com.example.wanandroid.data.repository

import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.remote.ApiService
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeRepositoryTest {
    private val response = AtomicReference("""{"errorCode":0,"data":{"datas":[],"over":false}}""")
    private val paths = CopyOnWriteArrayList<String>()
    private lateinit var server: HttpServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: HomeRepository

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths.add(exchange.requestURI.path)
            val bytes = response.get().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        client = OkHttpClient()
        val service = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:${server.address.port}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
        repository = HomeRepository(service)
    }

    @After
    fun tearDown() {
        server.stop(0)
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun requestedPageIsSentToTheEndpointWithoutRequestingTopArticles() = runBlocking {
        response.set("""{"errorCode":0,"data":{"datas":[{"id":7}],"over":false}}""")
        val page = repository.getHomeArticles(2)
        assertEquals(listOf("/article/list/2/json"), paths.toList())
        assertEquals(7, page.datas!!.single().id)
        assertFalse(page.over)
    }

    @Test
    fun pageRetainsTheServerEndFlag() = runBlocking {
        response.set("""{"errorCode":0,"data":{"datas":[{"id":9}],"over":true}}""")
        assertTrue(repository.getHomeArticles(5).over)
    }

    @Test
    fun topArticlesAreFetchedSeparatelyAndMarkedAsTop() = runBlocking {
        response.set("""{"errorCode":0,"data":[{"id":3,"title":"Top"}]}""")
        val article = repository.getTopArticles().single()
        assertTrue(article.isTop)
        assertEquals(3, article.id)
        assertEquals(listOf("/article/top/json"), paths.toList())
    }

    @Test
    fun emptyPageIsReturnedAsAValidResult() = runBlocking {
        val page = repository.getHomeArticles(1)
        assertTrue(page.datas!!.isEmpty())
    }

    @Test
    fun missingArticleListIsAnErrorInsteadOfAnEmptyLastPage() = runBlocking {
        response.set("""{"errorCode":0,"data":{"over":false}}""")
        expectApiError("接口响应中缺少文章列表") { repository.getHomeArticles(0) }
    }

    @Test
    fun missingPageObjectIsAnError() = runBlocking {
        response.set("""{"errorCode":0,"data":null}""")
        expectApiError("接口响应中缺少分页数据") { repository.getHomeArticles(0) }
    }

    @Test
    fun businessFailureRetainsTheServerMessage() = runBlocking {
        response.set("""{"errorCode":-1,"errorMsg":"service unavailable","data":null}""")
        expectApiError("service unavailable") { repository.getHomeArticles(0) }
    }

    @Test
    fun bannerUsesItsOwnEndpoint() = runBlocking {
        response.set("""{"errorCode":0,"data":[{"title":"Banner","url":"https://example.com"}]}""")
        assertEquals("Banner", repository.getBanners().single().title)
        assertEquals(listOf("/banner/json"), paths.toList())
    }

    private suspend fun expectApiError(message: String, action: suspend () -> Unit) {
        try {
            action()
            fail("Expected ApiException")
        } catch (error: ApiException) {
            assertEquals(message, error.message)
        }
    }
}
