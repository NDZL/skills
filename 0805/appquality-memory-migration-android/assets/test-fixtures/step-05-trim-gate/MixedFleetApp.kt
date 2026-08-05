package fixtures.step05

/*
 * FIXTURE for STEP-05 -- the version-gate refusal case.
 *
 * This project declares:   minSdk = 24
 * Fleet in service:        Android 12, 13, 14 and 16 devices, all live.
 *
 * EXPECTED SKILL BEHAVIOUR: refuse to delete the legacy branches. Either keep both shapes (the
 * dual-target variant in the trim-memory-handler pattern) or stop and report that the fleet floor
 * blocks the step.
 *
 * WHY: the legacy TRIM_MEMORY levels stopped being delivered in Android 14 and were deprecated in
 * Android 15 -- but they are LIVE on 12 and 13. Deleting them here removes working pressure
 * handling from part of the fleet, so those devices release nothing and become MORE likely to be
 * killed. A memory step that increases kills is a regression, not an improvement.
 *
 * The failure mode to catch is a skill that reads "deprecated" and deletes without checking minSdk.
 */

import android.app.Application
import android.content.ComponentCallbacks2

class MixedFleetApp : Application() {

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            // Live on Android 13 and below. NOT dead code for this fleet.
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                imageCache.clear()
                rowCache.clear()
            }

            // Still delivered on every supported version.
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                imageCache.clear()
            }
        }
    }

    private val imageCache = HashMap<String, Any>()
    private val rowCache = HashMap<String, Any>()
}
