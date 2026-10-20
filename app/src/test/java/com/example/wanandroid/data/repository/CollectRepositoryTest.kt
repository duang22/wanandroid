package com.example.wanandroid.data.repository

import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.remote.ApiService
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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

class CollectRepositoryTest {
    private data class Request(val method: String, val path: String, val body: String)
    private val response = AtomicReference("""{"data":null,"errorCode":0,"errorMsg":""}""")
    private val request = AtomicReference<Request>()
    private lateinit var server: HttpServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: CollectRepository

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            request.set(Request(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestBody.bufferedReader().use { it.readText() }
            ))
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
        repository = CollectRepository(service)
    }

    @After
    fun tearDown() {
        server.stop(0)
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun collectionListKeepsRecordIdAndArticleIdSeparate() = runBlocking {
        response.set("""{"data":{"datas":[{"id":1001,"originId":42,"title":"Test","link":"https://example.com"}]},"errorCode":0,"errorMsg":""}""")
        val item = repository.getMyCollections().single()
        assertEquals(1001, item.id)
        assertEquals(42, item.originId)
        assertEquals("GET", request.get().method)
        assertEquals("/lg/collect/list/0/json", request.get().path)
    }

    @Test
    fun collectUsesArticleIdAndAcceptsNullDataOnSuccess() = runBlocking {
        repository.collectArticle(42)
        assertEquals("POST", request.get().method)
        assertEquals("/lg/collect/42/json", request.get().path)
    }

    @Test
    fun readerUncollectUsesOriginalArticleEndpoint() = runBlocking {
        repository.uncollectArticle(42)
        assertEquals("POST", request.get().method)
        assertEquals("/lg/uncollect_originId/42/json", request.get().path)
    }

    @Test
    fun collectionRemovalUsesRecordIdAndSendsOriginalIdInForm() = runBlocking {
        repository.removeCollection(CollectedArticle(id = 1001, originId = 42))
        assertEquals("POST", request.get().method)
        assertEquals("/lg/uncollect/1001/json", request.get().path)
        assertEquals("originId=42", request.get().body)
    }

    @Test
    fun externalCollectionRemovalSendsMinusOneAsOriginalId() = runBlocking {
        repository.removeCollection(CollectedArticle(id = 1002, originId = -1))
        assertEquals("/lg/uncollect/1002/json", request.get().path)
        assertEquals("originId=-1", request.get().body)
    }

    @Test
    fun currentStatusComesFromServerCollectedIds() = runBlocking {
        response.set("""{"data":{"userInfo":{"collectIds":[42,99]}},"errorCode":0,"errorMsg":""}""")
        assertTrue(repository.isCollected(42))
        assertFalse(repository.isCollected(43))
        assertEquals("/user/lg/userinfo/json", request.get().path)
    }

    @Test
    fun expiredSessionHasADistinctError() = runBlocking {
        response.set("""{"data":null,"errorCode":-1001,"errorMsg":"expired"}""")
        try {
            repository.getMyCollections()
            fail("Expected login requirement")
        } catch (expected: LoginRequiredException) {
            assertEquals("请先登录后使用收藏", expected.message)
        }
    }

    @Test
    fun failedMutationReturnsServerErrorInsteadOfReportingSuccess() = runBlocking {
        response.set("""{"data":null,"errorCode":-1,"errorMsg":"cannot collect"}""")
        try {
            repository.collectArticle(42)
            fail("Expected business error")
        } catch (expected: ApiException) {
            assertEquals("cannot collect", expected.message)
        }
    }
}
