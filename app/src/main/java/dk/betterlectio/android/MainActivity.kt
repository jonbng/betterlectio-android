package dk.betterlectio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.posthog.PostHog
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dk.betterlectio.android.core.lectio.auth.AuthSessionInstaller
import dk.betterlectio.android.core.lectio.session.AuthState
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.feature.notifications.NotificationDiffWorker
import dk.betterlectio.android.feature.settings.AppearanceMode
import dk.betterlectio.android.feature.settings.SettingsStore
import dk.betterlectio.android.ui.navigation.BetterLectioRoot
import dk.betterlectio.android.ui.theme.BetterLectioTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionController: SessionController

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var authSessionInstaller: AuthSessionInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore.applyStoredLanguage()
        sessionController.restore()
        (sessionController.authState.value as? AuthState.Authenticated)?.let { state ->
            // Re-identify the user so all events in this session are linked to their profile.
            if (!state.student.isDemo) {
                PostHog.identify(
                    distinctId = state.student.studentId,
                    userProperties = mapOf("gym_id" to state.student.gymId),
                )
            }
            // Session gate opens inside ensureSupabaseSession; subject sync runs after mint.
            authSessionInstaller.ensureSupabaseSession(state.student)
        }
        NotificationDiffWorker.enqueue(applicationContext)
        enableEdgeToEdge()
        setContent {
            val appearance by settingsStore.appearance.collectAsStateWithLifecycle()
            val dark = when (appearance) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            BetterLectioTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BetterLectioRoot(sessionController = sessionController)
                }
            }
        }
    }
}
