package patterns.observability

/*
 * STEP-06 -- detect memory-limit kills.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * *** THIS STEP SAVES NO MEMORY AT ALL. ***
 * It buys VISIBILITY into a failure that is otherwise completely invisible. Report it as
 * observability, never as a saving -- describing it as a reduction is anti-pattern AP-05.
 *
 * WHY IT MATTERS: from Android 17 the system kills processes that exceed a memory limit "with no
 * associated stack trace". Your crash reporter shows NOTHING. The field symptom is an app that
 * "just closes" mid-workflow. The only forensic trail is the exit reason plus a description
 * containing "MemoryLimiter:AnonSwap".
 *
 * VERSION SCOPE: exit info requires API 30+. The description string is Android 17+. Guard both.
 * THREADING: read once at startup, off the main thread. Reading has no side effects.
 * WATCH FOR: double counting. De-duplicate by timestamp, or one kill is reported on every launch.
 */

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

class MemoryLimitKillReporter(
    private val context: Context,
    private val lastSeenTimestamp: () -> Long,
    private val saveLastSeenTimestamp: (Long) -> Unit,
    private val report: (MemoryLimitKill) -> Unit,
) {

    data class MemoryLimitKill(
        val timestamp: Long,
        val description: String,
        val processName: String,
        val importanceAtExit: Int,
    )

    /** Call once at startup, on a background dispatcher. Safe to call again; repeats are filtered. */
    fun reportNewKills(maxRecords: Int = 15) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return   // API 30+

        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val since = lastSeenTimestamp()
        var newest = since

        val records = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, maxRecords)
        }.getOrNull() ?: return

        for (info in records) {
            if (info.timestamp <= since) continue          // already reported
            newest = maxOf(newest, info.timestamp)
            if (!isMemoryLimitKill(info)) continue

            report(
                MemoryLimitKill(
                    timestamp = info.timestamp,
                    description = info.description ?: "",
                    processName = info.processName,
                    importanceAtExit = info.importance,
                )
            )
        }

        if (newest > since) saveLastSeenTimestamp(newest)
    }

    /**
     * A memory-limit kill reports REASON_OTHER with a description containing the limiter marker.
     * Matching on "MemoryLimiter" rather than the full "MemoryLimiter:AnonSwap" keeps this working
     * if the platform appends further detail.
     */
    private fun isMemoryLimitKill(info: ApplicationExitInfo): Boolean =
        info.reason == ApplicationExitInfo.REASON_OTHER &&
            info.description?.contains("MemoryLimiter", ignoreCase = true) == true
}

/*
 * WIRING -- in Application.onCreate, off the main thread:
 *
 *   applicationScope.launch(Dispatchers.IO) {
 *       MemoryLimitKillReporter(
 *           context = this@App,
 *           lastSeenTimestamp = { prefs.getLong(KEY_LAST_EXIT, 0L) },
 *           saveLastSeenTimestamp = { prefs.edit().putLong(KEY_LAST_EXIT, it).apply() },
 *           report = { telemetry.logMemoryLimitKill(it) },
 *       ).reportNewKills()
 *   }
 *
 * Treat the event as its own class in telemetry. Folding it into generic crashes loses the one
 * signal that distinguishes it, and it will never appear as a crash on its own.
 *
 * Also worth capturing at the same time, if the fleet is on Android 17+: the importance at exit
 * tells you whether the kill happened while the process was visible or not visible -- which
 * indicates whether the tighter background ceiling was the binding one.
 *
 * VERIFY: force a kill with the memory-limiter manual override on a running process, relaunch, and
 * confirm EXACTLY ONE event is reported. Relaunch again and confirm nothing is reported twice.
 */
