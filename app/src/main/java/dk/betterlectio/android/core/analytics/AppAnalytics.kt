package dk.betterlectio.android.core.analytics

import android.content.Context
import android.os.SystemClock
import com.posthog.PostHog
import dk.betterlectio.android.BuildConfig
import dk.betterlectio.android.core.model.Student

/** Low-volume operational analytics shared by every Android surface. */
object AppAnalytics {
    private const val PREFS = "posthog_observability"
    private const val SUCCESS_LOAD_SAMPLE_RATE = 0.1
    private const val ACTIVE_SESSION_GAP_MS = 30 * 60 * 1_000L
    private var appContext: Context? = null
    private var lastActiveAt = 0L
    private var startupCaptured = false

    object Event {
        const val APP_ACTIVE = "app_active"
        const val APP_LOAD_COMPLETED = "app_load_completed"
        const val LOAD_COMPLETED = "load_completed"
        const val AUTH_LOGIN_STARTED = "auth_login_started"
        const val AUTH_LOGIN_COMPLETED = "auth_login_completed"
        const val AUTH_LOGIN_FAILED = "auth_login_failed"
        const val AUTH_SESSION_LOST = "auth_session_lost"
        const val AUTH_LOGGED_OUT = "auth_logged_out"
        const val REFERRAL_SHARED = "referral_shared"
    }

    fun configure(context: Context) {
        appContext = context.applicationContext
        PostHog.register("platform", "android")
        PostHog.register("app_version", BuildConfig.VERSION_NAME)
        PostHog.register("app_build", BuildConfig.VERSION_CODE)
    }

    fun identify(student: Student) {
        if (BuildConfig.ADMIN_BUILD || student.isDemo) return
        val studentId = student.studentId.trim()
        if (!studentId.matches(Regex("^[0-9A-Za-z_-]{1,48}$"))) return
        val canonicalId = canonicalDistinctId(studentId)
        val properties = buildMap<String, Any> {
            put("student_id", studentId)
            put("school_id", student.gymId.toString())
            put("platform", "android")
            put("last_platform", "android")
            put("uses_android", true)
            put("app_version", BuildConfig.VERSION_NAME)
            put("app_build", BuildConfig.VERSION_CODE)
            student.name?.takeIf(String::isNotBlank)?.let {
                put("name", it)
                put("\$name", it)
            }
            student.schoolName?.takeIf(String::isNotBlank)?.let { put("school_name", it) }
            student.classLabel?.takeIf(String::isNotBlank)?.let { put("class_name", it) }
        }
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fingerprint = buildString {
            append(canonicalId)
            properties.toSortedMap().forEach { (key, value) -> append('|').append(key).append('=').append(value) }
        }
        val fingerprintKey = "identify_fingerprint"
        if (prefs?.getString(fingerprintKey, null) != fingerprint) {
            PostHog.identify(
                distinctId = canonicalId,
                userProperties = properties,
            )
            prefs?.edit()?.putString(fingerprintKey, fingerprint)?.apply()
        }
        val migrationKey = "canonical_identity_migrated:${student.studentId}"
        if (prefs?.getBoolean(migrationKey, false) != true) {
            PostHog.alias(student.studentId)
            prefs?.edit()?.putBoolean(migrationKey, true)?.apply()
        }
    }

    @Synchronized
    fun captureActive(student: Student) {
        if (BuildConfig.ADMIN_BUILD || student.isDemo) return
        val now = SystemClock.elapsedRealtime()
        val previousActiveAt = lastActiveAt
        lastActiveAt = now
        if (previousActiveAt > 0 && now - previousActiveAt < ACTIVE_SESSION_GAP_MS) return
        PostHog.capture(
            event = Event.APP_ACTIVE,
            properties = mapOf(
                "auth_state" to "authenticated",
                "school_id" to student.gymId.toString(),
            ),
        )
    }

    @Synchronized
    fun captureStartupCompleted(student: Student, startedAt: Long) {
        if (BuildConfig.ADMIN_BUILD || student.isDemo || startupCaptured) return
        startupCaptured = true
        PostHog.capture(
            event = Event.APP_LOAD_COMPLETED,
            properties = mapOf(
                "outcome" to "success",
                "auth_state" to "authenticated",
                "duration_ms" to (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            ),
        )
    }

    fun captureLoad(
        operation: String,
        outcome: String,
        startedAt: Long,
        properties: Map<String, Any> = emptyMap(),
    ) {
        if (BuildConfig.ADMIN_BUILD) return
        val rate = if (outcome == "success") SUCCESS_LOAD_SAMPLE_RATE else 1.0
        val inHealthSample = sampleBucket() < SUCCESS_LOAD_SAMPLE_RATE
        if (outcome == "success" && !inHealthSample) return
        PostHog.capture(
            event = Event.LOAD_COMPLETED,
            properties = properties + mapOf(
                "operation" to operation,
                "outcome" to outcome,
                "duration_ms" to (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                "sample_rate" to rate,
                "health_sample" to inHealthSample,
            ),
        )
    }

    fun canonicalDistinctId(studentId: String): String = "lectio:${studentId.trim()}"

    fun reset() {
        appContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove("identify_fingerprint")
            ?.apply()
        PostHog.reset()
    }

    private fun sampleBucket(): Double {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return 1.0
        if (prefs.contains("load_sample_bucket")) {
            return prefs.getFloat("load_sample_bucket", 1f).toDouble()
        }
        val value = Math.random().toFloat()
        prefs.edit().putFloat("load_sample_bucket", value).apply()
        return value.toDouble()
    }
}
