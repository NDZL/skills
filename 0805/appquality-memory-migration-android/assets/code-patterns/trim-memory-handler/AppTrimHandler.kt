package patterns.pressure

/*
 * STEP-05 -- rewrite pressure handling to the levels that are actually delivered.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * WHAT THIS REPLACES -- and why it is a trap
 *
 *   override fun onTrimMemory(level: Int) {
 *       when (level) {
 *           TRIM_MEMORY_RUNNING_LOW, TRIM_MEMORY_RUNNING_CRITICAL -> hardTrim()
 *           TRIM_MEMORY_MODERATE, TRIM_MEMORY_COMPLETE -> hardTrim()
 *           TRIM_MEMORY_UI_HIDDEN, TRIM_MEMORY_BACKGROUND -> softTrim()
 *       }
 *   }
 *
 * From Android 14 the system NO LONGER DELIVERS the legacy constants; they were deprecated in
 * Android 15. Only TRIM_MEMORY_UI_HIDDEN and TRIM_MEMORY_BACKGROUND still fire. So four of those
 * branches are dead code -- which is high-yield precisely because the block LOOKS diligent and
 * releases nothing. Reviewers skip it.
 *
 * *** STOP CONDITION -- READ BEFORE APPLYING ***
 * Those branches are LIVE on Android 13 and below. A Zebra fleet commonly spans Android 11 to 19
 * simultaneously. If the fleet floor is below Android 14, DO NOT delete them -- keep both shapes
 * (see the dual-target variant at the bottom). Deleting them from a mixed fleet removes working
 * pressure handling and makes those devices MORE likely to be killed: a memory step that increases
 * kills. That is anti-pattern AP-07.
 *
 * MUST NOT CHANGE: what gets released. Preserve the existing release logic; only dispatch changes.
 */

import android.app.Application
import android.content.ComponentCallbacks2

// ---------------------------------------------------------------- fleet floor is Android 14+

class App : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // Threshold comparison, not equality matching -- this is the documented shape.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // The UI is no longer visible. Release memory tied strictly to the UI:
            // bitmap caches, playback buffers, animation resources.
            releaseUiMemory()
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // The process is a termination candidate. Release what can be rebuilt.
            releaseBackgroundMemory()
        }
    }

    // Both must be IDEMPOTENT: they can be invoked more than once, and in either order.
    private fun releaseUiMemory() { /* imageCache.evictAll() etc. */ }
    private fun releaseBackgroundMemory() { /* inMemoryCaches.clear() etc. */ }
}

// ---------------------------------------------------------------- mixed fleet, floor below 14

/**
 * Use this when the fleet still includes Android 13 or below. The threshold checks cover modern
 * devices; the legacy branch still runs on older ones. Nothing is deleted, so no device loses
 * working behaviour.
 */
abstract class DualTargetApp : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) releaseUiMemory()
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) releaseBackgroundMemory()

        // Still delivered on Android 13 and below; simply never fires on 14+.
        @Suppress("DEPRECATION")
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        ) {
            releaseUiMemory()
        }
    }

    protected abstract fun releaseUiMemory()
    protected abstract fun releaseBackgroundMemory()
}

/*
 * Note also that onLowMemory() is deprecated; do not rely on it as the only pressure handling.
 *
 * EXPECTED EFFECT: no measurable steady-state change. This step reduces KILL PROBABILITY by
 * releasing before the system decides for you -- do not report it as a footprint reduction.
 *
 * VERIFY: instrument the release path, background the app, and confirm invocation.
 */
