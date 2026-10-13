package dk.betterlectio.android.core.lectio.http

import dk.betterlectio.android.core.lectio.model.FetchPriority
import dk.betterlectio.android.core.lectio.model.LectioCredentials
import dk.betterlectio.android.core.lectio.model.LectioError
import dk.betterlectio.android.core.lectio.model.LectioRequest
import dk.betterlectio.android.core.lectio.session.InMemoryCredentialStore
import dk.betterlectio.android.core.lectio.session.SessionEvents
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LectioHttpEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryCredentialStore
    private lateinit var events: SessionEvents
    private lateinit var engine: LectioHttpEngine

    private val creds = LectioCredentials(
        autologinkey = "AUTO",
        sessionId = "SESS",
    ).seededIsLoggedIn()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemoryCredentialStore()
        events = SessionEvents()
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()
        engine = LectioHttpEngine(
            client = client,
            credentialStore = store,
            sessionEvents = events,
            limiter = PriorityRequestLimiter(minIntervalMs = 0),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun unilogin_location_recovers_once_without_short_session_cookie() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "https://broker.unilogin.dk/auth/realms/broker"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("recovered"))

        val url = server.url("/page")
        store.saveCredentials(creds, "s1")
        val result = engine.execute(
            LectioRequest(url = url, priority = FetchPriority.Important, studentId = "s1"),
            creds,
        )
        assertTrue(result.response.body.contains("recovered"))
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val recoveryCookie = server.takeRequest().getHeader("Cookie").orEmpty()
        assertTrue(recoveryCookie.contains("autologinkeyV2=AUTO"))
        assertTrue(!recoveryCookie.contains("ASP.NET_SessionId"))
    }

    @Test
    fun failed_unilogin_recovery_expires_session_after_one_retry() = runBlocking {
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "https://broker.unilogin.dk/auth/realms/broker"),
            )
        }
        store.saveCredentials(creds, "s1")
        try {
            engine.execute(
                LectioRequest(url = server.url("/page"), priority = FetchPriority.Important, studentId = "s1"),
                creds,
            )
            org.junit.Assert.fail("expected LectioError.SessionExpired")
        } catch (_: LectioError.SessionExpired) {
            // expected
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun max_redirects_with_student_is_session_expired() = runBlocking {
        // iOS: redirect budget exceeded → session death when already authenticated.
        repeat(16) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", server.url("/loop").toString()),
            )
        }
        try {
            engine.execute(
                LectioRequest(
                    url = server.url("/loop"),
                    priority = FetchPriority.Important,
                    studentId = "s1",
                ),
                creds,
            )
            org.junit.Assert.fail("expected LectioError.SessionExpired")
        } catch (_: LectioError.SessionExpired) {
            // expected (immediate on redirect budget when studentId known)
        }
    }

    @Test
    fun max_redirects_without_student_becomes_session_expired_after_retries() = runBlocking {
        // Login path: InvalidCredentials per attempt, then outer loop declares SessionExpired.
        // 3 attempts × ~6 redirect hops each needs enough mock responses.
        repeat(30) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", server.url("/loop").toString()),
            )
        }
        try {
            engine.execute(
                LectioRequest(url = server.url("/loop"), priority = FetchPriority.Important),
                creds,
            )
            org.junit.Assert.fail("expected LectioError.SessionExpired")
        } catch (_: LectioError.SessionExpired) {
            // expected
        }
    }

    @Test
    fun robot_page_throws_robot_detection() = runBlocking {
        // Only one response: robot is not retried into success; still retried up to maxAttempts.
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("<html>Du skal bekræfte at du ikke er en robot</html>"),
            )
        }
        val url = server.url("/robot")
        try {
            engine.execute(
                LectioRequest(url = url, priority = FetchPriority.Important),
                creds,
            )
            org.junit.Assert.fail("expected robot")
        } catch (e: LectioError.RobotDetection) {
            // expected
        }
    }

    @Test
    fun successful_get_returns_body() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<html>hello lectio</html>"),
        )
        val result = engine.execute(
            LectioRequest(url = server.url("/ok"), priority = FetchPriority.Important),
            creds,
        )
        assertEquals(200, result.response.statusCode)
        assertTrue(result.response.body.contains("hello lectio"))
        assertEquals(creds.autologinkey, result.credentials.autologinkey)
    }

    @Test
    fun cookie_header_sent_on_request() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        engine.execute(
            LectioRequest(url = server.url("/cookie"), priority = FetchPriority.Important),
            creds,
        )
        val recorded = server.takeRequest()
        val cookie = recorded.getHeader("Cookie")
        assertTrue(cookie!!.contains("autologinkeyV2=AUTO"))
        assertTrue(cookie.contains("ASP.NET_SessionId=SESS"))
        assertTrue(cookie.contains("isloggedin3=Y"))
    }

    @Test
    fun cross_domain_redirect_does_not_forward_lectio_cookies() = runBlocking {
        val external = MockWebServer()
        external.start()
        try {
            val externalUrl = external.url("/landing").newBuilder()
                .host("127.0.0.1")
                .build()
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", externalUrl.toString()),
            )
            external.enqueue(MockResponse().setResponseCode(200).setBody("external"))

            engine.execute(
                LectioRequest(url = server.url("/redirect"), priority = FetchPriority.Important),
                creds,
            )

            assertEquals(null, external.takeRequest().getHeader("Cookie"))
        } finally {
            external.shutdown()
        }
    }
}
