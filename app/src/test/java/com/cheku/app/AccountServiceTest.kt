package com.cheku.app

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountServiceTest {
    private class MapKeyValueStore : KeyValueStore {
        private val map = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?): String? = map[key] as? String ?: default
        override fun putString(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override fun getLong(key: String, default: Long): Long = (map[key] as? Long) ?: default
        override fun putLong(key: String, value: Long) { map[key] = value }
        override fun getBoolean(key: String, default: Boolean): Boolean = (map[key] as? Boolean) ?: default
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun contains(key: String): Boolean = map.containsKey(key)
    }

    private class StubApiClient(private val handler: suspend (ApiRequest) -> ApiResponse) : ApiClient {
        override suspend fun call(request: ApiRequest): ApiResponse = handler(request)
    }

    @Test
    fun emailValidation() {
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("  User.Name+Tag@Sub.Domain.cn  "))
        assertFalse(isValidEmail("not-an-email"))
        assertFalse(isValidEmail("a@b"))
        assertEquals("user@example.com", normalizeEmail("  USER@EXAMPLE.COM  "))
    }

    @Test
    fun passwordAndCodeValidation() {
        assertTrue(isValidPassword("12345678"))
        assertFalse(isValidPassword("1234567")) // 太短
        assertFalse(isValidPassword("")) // 空
        assertTrue(isValidCode("123456"))
        assertFalse(isValidCode("12")) // 太短
        assertFalse(isValidCode("abcdef")) // 非数字
    }

    @Test
    fun requestBodiesContainExpectedFields() {
        JSONObject(buildSendCodeBody("A@B.com")).let {
            assertEquals("a@b.com", it.getString("email"))
        }
        JSONObject(buildRegisterBody("A@B.com", "123456", "secret123")).let {
            assertEquals("a@b.com", it.getString("email"))
            assertEquals("123456", it.getString("code"))
            assertEquals("secret123", it.getString("password"))
        }
        JSONObject(buildLoginBody("A@B.com", "secret123")).let {
            assertEquals("a@b.com", it.getString("email"))
            assertEquals("secret123", it.getString("password"))
        }
        JSONObject(buildDeleteBody("secret123")).let {
            assertEquals("secret123", it.getString("password"))
        }
    }

    @Test
    fun parseLoginResponseReadsSession() {
        val session = parseLoginResponse(
            """{"token":"tkn","expiresAt":1893456000,"uid":"u_abc","email":"a@b.com"}""",
        )
        assertEquals("tkn", session.token)
        assertEquals("u_abc", session.uid)
        assertEquals("a@b.com", session.email)
        assertEquals(1893456000L * 1000L, session.expiresAt) // 秒级时间戳补成毫秒
    }

    @Test
    fun parseLoginResponseReadsIsoExpiry() {
        val session = parseLoginResponse(
            """{"token":"t","expiresAt":"2030-01-01T00:00:00Z","uid":"u","email":"a@b.com"}""",
        )
        assertEquals(1893456000000L, session.expiresAt)
    }

    @Test
    fun parseMeResponseReadsProfile() {
        val p = parseMeResponse(
            """{"uid":"u","email":"a@b.com","emailVerified":true,"status":"active","quotaBytes":209715200}""",
        )
        assertEquals("u", p.uid)
        assertEquals("a@b.com", p.email)
        assertTrue(p.emailVerified)
        assertEquals("active", p.status)
        assertEquals(209715200L, p.quotaBytes)
    }

    @Test
    fun mapAccountErrorGivesClearMessages() {
        assertEquals("该邮箱已被注册", mapAccountError(409, "{}").userMessage)
        assertEquals("邮箱或密码错误，请重试", mapAccountError(401, "{}").userMessage)
        assertTrue(mapAccountError(429, """{"retryAfterSeconds":30}""").retryable)
        assertTrue(mapAccountError(500, "{}").retryable)
        assertFalse(mapAccountError(400, "{}").retryable)
        assertEquals("请求参数有误：邮箱格式错误", mapAccountError(400, """{"message":"邮箱格式错误"}""").userMessage)
    }

    @Test
    fun accountClientMessageMapsServerShortCodes() {
        assertEquals("验证码不正确，请核对邮件中的 6 位数字", accountClientMessage("invalid code"))
        assertEquals("验证码已失效，请重新发送", accountClientMessage("code expired or not found"))
        assertEquals("验证码已失效，请重新发送", accountClientMessage("code expired"))
        assertEquals("验证码错误次数过多，请重新发送后再试", accountClientMessage("too many attempts"))
        assertEquals("该邮箱已注册，请直接登录", accountClientMessage("email already registered"))
        assertEquals("邮箱格式不正确", accountClientMessage("invalid email"))
        assertEquals("请求参数有误", accountClientMessage(null))
        // 没见过的短码不吞掉：原样带上，排障时仍能看到服务端说了什么。
        assertEquals("请求参数有误：weird thing", accountClientMessage("weird thing"))
    }

    @Test
    fun accountErrorRetryableFlag() {
        assertTrue(isAccountErrorRetryable(429))
        assertTrue(isAccountErrorRetryable(503))
        assertFalse(isAccountErrorRetryable(400))
        assertFalse(isAccountErrorRetryable(401))
    }

    @Test
    fun registerFlowStateMachine() {
        var state: RegisterFlowState = RegisterFlowState.Idle
        state = advanceRegisterFlow(state, RegisterFlowEvent.CodeSent("a@b.com"))
        assertTrue(state is RegisterFlowState.CodeSent)
        val session = AccountSession("t", 0L, "u", "a@b.com")
        state = advanceRegisterFlow(state, RegisterFlowEvent.Registered(session))
        assertTrue(state is RegisterFlowState.Done)
        state = advanceRegisterFlow(state, RegisterFlowEvent.Reset)
        assertTrue(state is RegisterFlowState.Idle)
        state = advanceRegisterFlow(state, RegisterFlowEvent.Failed("a@b.com", "验证码错误"))
        assertTrue(state is RegisterFlowState.Error)
    }

    @Test
    fun loginSuccessPersistsSession() = runTest {
        val store = MapKeyValueStore()
        val client = StubApiClient {
            ApiResponse(200, """{"token":"tkn","expiresAt":1893456000,"uid":"u_1","email":"a@b.com"}""")
        }
        val service = AccountService(store, client)
        val session = service.login("a@b.com", "secret123")
        assertEquals("tkn", session.token)
        assertTrue(service.isLoggedIn)
        assertEquals("a@b.com", service.getEmail())
    }

    @Test(expected = CloudApiException::class)
    fun loginFailureThrowsWithMessage() = runTest {
        val store = MapKeyValueStore()
        val client = StubApiClient { ApiResponse(401, """{"message":"bad"}""") }
        val service = AccountService(store, client)
        try {
            service.login("a@b.com", "wrong")
        } catch (e: CloudApiException) {
            assertEquals("邮箱或密码错误，请重试", e.error.userMessage)
            throw e
        }
    }

    @Test
    fun registerPersistsSessionFromResponse() = runTest {
        val store = MapKeyValueStore()
        val paths = mutableListOf<String>()
        val client = StubApiClient { req ->
            paths += req.url
            ApiResponse(200, """{"token":"tkn","expiresAt":1893456000,"uid":"u_1","email":"a@b.com"}""")
        }
        val service = AccountService(store, client)
        val session = service.register("A@B.com", "123456", "secret123")
        assertEquals("tkn", session.token)
        assertTrue(service.isLoggedIn)
        assertEquals(listOf("${DEFAULT_ACCOUNT_BASE_URL}/register"), paths)
    }

    @Test
    fun registerFallsBackToLoginWhenServerOmitsToken() = runTest {
        val store = MapKeyValueStore()
        val paths = mutableListOf<String>()
        val client = StubApiClient { req ->
            paths += req.url
            if (req.url.endsWith("/register")) {
                ApiResponse(200, """{"ok":true,"uid":"u_1"}""")
            } else {
                ApiResponse(200, """{"token":"tkn","expiresAt":1893456000,"uid":"u_1","email":"a@b.com"}""")
            }
        }
        val service = AccountService(store, client)
        val session = service.register("A@B.com", "123456", "secret123")
        assertEquals("tkn", session.token)
        assertTrue(service.isLoggedIn)
        assertEquals(
            listOf("${DEFAULT_ACCOUNT_BASE_URL}/register", "${DEFAULT_ACCOUNT_BASE_URL}/login"),
            paths
        )
    }

    @Test
    fun expiredSessionTreatedAsLoggedOut() {
        val store = MapKeyValueStore()
        store.putString("account_token", "t")
        store.putString("account_uid", "u")
        store.putString("account_email", "a@b.com")
        store.putLong("account_expires_at", 1000L) // 早已过期
        val service = AccountService(store, StubApiClient { ApiResponse(200, "{}") })
        assertFalse(service.isLoggedIn)
        assertNull(service.getSession())
    }

    @Test
    fun logoutClearsSessionEvenIfServerFails() = runTest {
        val store = MapKeyValueStore()
        store.putString("account_token", "t")
        store.putString("account_email", "a@b.com")
        val client = StubApiClient { ApiResponse(500, "{}") } // 服务端报错
        val service = AccountService(store, client)
        service.logout() // 不应抛异常
        assertFalse(service.isLoggedIn)
    }

    @Test
    fun loadMeUsesTokenAndParses() = runTest {
        val store = MapKeyValueStore()
        store.putString("account_token", "t")
        val client = StubApiClient { req ->
            assertEquals("t", req.authToken)
            assertEquals("GET", req.method)
            ApiResponse(200, """{"uid":"u","email":"a@b.com","emailVerified":false}""")
        }
        val service = AccountService(store, client)
        val profile = service.loadMe()
        assertEquals("u", profile.uid)
    }
}
