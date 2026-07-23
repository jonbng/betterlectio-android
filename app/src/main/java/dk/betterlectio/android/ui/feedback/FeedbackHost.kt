package dk.betterlectio.android.ui.feedback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.betterlectio.android.feature.feedback.FeedbackCapture
import dk.betterlectio.android.feature.feedback.FeedbackRepository
import dk.betterlectio.android.feature.feedback.FeedbackSubmitResult
import dk.betterlectio.android.feature.feedback.FeedbackSubmission
import dk.betterlectio.android.feature.feedback.ScreenshotCapturer
import dk.betterlectio.android.feature.feedback.ShakeDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Overlay host: listens for device shakes, captures screenshot + logs, shows [FeedbackSheet].
 * Place once at the root of the Compose tree (above navigation).
 */
@Composable
fun FeedbackHost(
    viewModel: FeedbackHostViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    var activeCapture by remember { mutableStateOf<FeedbackCapture?>(null) }
    var opening by remember { mutableStateOf(false) }

    // Shake → capture → sheet
    DisposableEffect(activity) {
        if (activity == null) {
            return@DisposableEffect onDispose { }
        }
        val sensorManager =
            activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val detector = ShakeDetector(
            sensorManager = sensorManager,
            onShake = { viewModel.onShakeDetected() },
        )
        detector.start()
        onDispose { detector.stop() }
    }

    LaunchedEffect(viewModel) {
        viewModel.shakeEvents.collect {
            if (activeCapture != null || opening) return@collect
            val act = activity ?: return@collect
            opening = true
            try {
                performHaptic(view)
                val bitmap = ScreenshotCapturer.capture(act)
                val logs = viewModel.currentLogs()
                activeCapture = FeedbackCapture(
                    screenshot = bitmap,
                    logs = logs,
                )
                Timber.d(
                    "Feedback sheet opened screenshot=%s logsChars=%d",
                    bitmap != null,
                    logs.length,
                )
            } catch (t: Throwable) {
                Timber.w(t, "Failed to open feedback sheet")
                activeCapture = FeedbackCapture(
                    screenshot = null,
                    logs = viewModel.currentLogs(),
                )
            } finally {
                opening = false
            }
        }
    }

    content()

    activeCapture?.let { capture ->
        FeedbackSheet(
            capture = capture,
            onDismiss = {
                capture.screenshot?.takeIf { !it.isRecycled }?.recycle()
                activeCapture = null
            },
            onSubmit = { submission -> viewModel.submit(submission) },
        )
    }
}

@HiltViewModel
class FeedbackHostViewModel @Inject constructor(
    private val repository: FeedbackRepository,
) : ViewModel() {

    private val _shakeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shakeEvents: SharedFlow<Unit> = _shakeEvents.asSharedFlow()

    private var submitJob: Job? = null

    fun onShakeDetected() {
        _shakeEvents.tryEmit(Unit)
    }

    fun currentLogs(): String = repository.currentLogs()

    suspend fun submit(submission: FeedbackSubmission): FeedbackSubmitResult {
        return repository.submit(submission)
    }

    /** Optional: fire-and-forget submit from non-suspend call sites. */
    fun submitAsync(
        submission: FeedbackSubmission,
        onResult: (FeedbackSubmitResult) -> Unit,
    ) {
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            onResult(repository.submit(submission))
        }
    }
}

private fun performHaptic(view: View) {
    try {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } catch (_: Throwable) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
