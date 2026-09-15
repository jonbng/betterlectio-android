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

    fun configure(context: Context) {
        appContext = context.applicationContext
        PostHog.register("platform", "android")
        PostHog.register("app_version", BuildConfig.VERSION_NAME)
        PostHog.register("app_build", BuildConfig.VERSION_CODE)
    }

    fun identify(student: Student) {
        if (BuildConfig.ADMIN_BUILD || student.isDemo) return
        PostHog.identify(
            distinctId = canonicalDistinctId(student.studentId),
            userProperties = buildMap {
                put("gym_id", student.gymId)
                put("platform", "android")
                put("last_platform", "android")
                put("app_version", BuildConfig.VERSION_NAME)
                put("app_build", BuildConfig.VERSION_CODE)
                student.schoolName?.takeIf(String::isNotBlank)?.let { put("school_name", it) }
                student.classLabel?.takeIf(String::isNotBlank)?.let { put("class_name", it) }
            },
        )

        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
            event = "app_active",
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
            event = "app_load_completed",
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
            event = "load_completed",
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
