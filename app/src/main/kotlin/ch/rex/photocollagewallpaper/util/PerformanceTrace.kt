package ch.rex.photocollagewallpaper.util

import android.os.SystemClock
import android.os.Trace
import android.util.Log

object PerformanceTrace {
    fun <T> measure(
        section: String,
        slowLogThresholdMillis: Long = Long.MAX_VALUE,
        block: () -> T,
    ): T {
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        Trace.beginSection(section)
        return try {
            block()
        } finally {
            Trace.endSection()
            val durationMillis =
                (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / NANOS_PER_MILLISECOND
            if (durationMillis >= slowLogThresholdMillis) {
                Log.d(LOG_TAG, "$section: ${durationMillis}ms")
            }
        }
    }

    fun mark(section: String) {
        Trace.beginSection(section)
        Trace.endSection()
    }

    private const val LOG_TAG = "CollagePerf"
    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
