package dk.betterlectio.android.feature.feedback

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Lightweight shake detector (accelerometer). Cooldown prevents double-triggers.
 *
 * Call [start]/[stop] from the Activity lifecycle (or Compose DisposableEffect).
 */
class ShakeDetector(
    private val sensorManager: SensorManager,
    private val onShake: () -> Unit,
    private val accelerationThreshold: Float = DEFAULT_THRESHOLD,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) : SensorEventListener {

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastShakeAt = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastSampleAt = 0L
    private var seeded = false

    fun start() {
        val sensor = accelerometer ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        seeded = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!seeded) {
            lastX = x
            lastY = y
            lastZ = z
            lastSampleAt = now
            seeded = true
            return
        }

        val dt = now - lastSampleAt
        if (dt < MIN_SAMPLE_INTERVAL_MS) return
        lastSampleAt = now

        val dx = x - lastX
        val dy = y - lastY
        val dz = z - lastZ
        lastX = x
        lastY = y
        lastZ = z

        val speed = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat() / dt * 10_000f
        if (speed < accelerationThreshold) return
        if (now - lastShakeAt < cooldownMs) return

        lastShakeAt = now
        onShake()
    }

    companion object {
        /**
         * Higher = harder to trigger. Units are a scaled delta-g / Δt heuristic
         * (see [onSensorChanged]), not raw m/s² — tune by feel on a real device.
         *
         * Baseline 1100 felt right for deliberate shakes; 1200 is a bit firmer
         * so pocket/jog noise is less likely to open the sheet.
         */
        const val DEFAULT_THRESHOLD = 1_200f
        const val DEFAULT_COOLDOWN_MS = 2_500L
        private const val MIN_SAMPLE_INTERVAL_MS = 60L
    }
}
