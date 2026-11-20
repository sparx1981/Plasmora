package app.plasmora.gl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.exp

/**
 * Tilt of the phone relative to how it is being held (the neutral pose adapts over a few seconds).
 * [x] and [y] are roughly -0.35..0.35 (about 20 degrees each way).
 */
object ParallaxSensor : SensorEventListener {
    @Volatile var x = 0f
    @Volatile var y = 0f
    private var sm: SensorManager? = null
    private var wm: WindowManager? = null
    private var users = 0
    private var restX = 0f; private var restY = 0f; private var have = false
    private var lastNs = 0L

    @Synchronized fun start(ctx: Context) {
        users++
        if (users > 1) return
        val c = ctx.applicationContext
        sm = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        wm = c.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val s = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (s != null) sm?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        have = false; lastNs = 0L
    }

    @Synchronized fun stop() {
        if (users == 0) return
        users--
        if (users == 0) { sm?.unregisterListener(this); x = 0f; y = 0f }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Suppress("DEPRECATION")
    override fun onSensorChanged(e: SensorEvent) {
        val gx = e.values[0] / 9.81f; val gy = e.values[1] / 9.81f; val gz = e.values[2] / 9.81f
        val rot = try { wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0 } catch (_: Throwable) { Surface.ROTATION_0 }
        val sx: Float; val sy: Float
        when (rot) {
            Surface.ROTATION_90 -> { sx = -gy; sy = gx }
            Surface.ROTATION_180 -> { sx = -gx; sy = -gy }
            Surface.ROTATION_270 -> { sx = gy; sy = -gx }
            else -> { sx = gx; sy = gy }
        }
        // left/right lean = sx, forward/back lean = gz (screen normal)
        val tx = sx; val ty = gz
        val now = e.timestamp
        val dt = if (lastNs == 0L) 0.02f else ((now - lastNs) / 1e9f).coerceIn(0.001f, 0.2f)
        lastNs = now
        if (!have) { restX = tx; restY = ty; have = true }
        val a = 1f - exp(-dt / 4f)
        restX += (tx - restX) * a; restY += (ty - restY) * a
        val k = 1f - exp(-dt / 0.10f)
        val tgtX = (tx - restX).coerceIn(-0.35f, 0.35f)
        val tgtY = (ty - restY).coerceIn(-0.35f, 0.35f)
        x += (tgtX - x) * k; y += (tgtY - y) * k
        if (abs(x) < 1e-4f) x = 0f
    }
}
