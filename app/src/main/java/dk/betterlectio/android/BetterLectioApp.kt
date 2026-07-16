package dk.betterlectio.android

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dagger.hilt.android.HiltAndroidApp
import dk.betterlectio.android.core.lectio.session.SessionController
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class BetterLectioApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var sessionController: SessionController

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Hilt injection for Application happens after super.onCreate in @HiltAndroidApp.
        // restore() is called from MainActivity to ensure injection is ready.

        if (BuildConfig.POSTHOG_API_KEY.isNotBlank()) {
            val posthogConfig = PostHogAndroidConfig(
                apiKey = BuildConfig.POSTHOG_API_KEY,
                host = BuildConfig.POSTHOG_HOST,
            ).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = true
                debug = BuildConfig.DEBUG
                errorTrackingConfig.autoCapture = true
            }
            PostHogAndroid.setup(this, posthogConfig)
        } else if (BuildConfig.DEBUG) {
            Timber.w(
                "PostHog disabled: POSTHOG_API_KEY empty. " +
                    "Set posthog.apiKey in local.properties or POSTHOG_API_KEY env.",
            )
        }
    }

    /** Coil uses the rate-limited ImageLoader from [dk.betterlectio.android.core.di.AppModule]. */
    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return if (this::imageLoader.isInitialized) imageLoader
        else ImageLoader.Builder(context).build()
    }
}

