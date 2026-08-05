package fixtures.rules.negative

import android.app.Application
import android.content.ComponentCallbacks2

/**
 * NEGATIVE fixture for MEM-PRESSURE-001. This must NOT be reported when minSdk is below 34.
 *
 * The legacy trim levels stopped being delivered in Android 14 and were deprecated in Android 15.
 * On a fleet that still includes Android 12 and 13 devices -- normal for a Zebra estate, which can
 * span Android 11 through 19 simultaneously -- these branches are LIVE and are doing real work.
 *
 * Reporting them as dead code without first reading minSdk and the fleet OS spread would lead a
 * developer to delete working pressure handling for part of the fleet. That is anti-pattern AP-06
 * (applying a version-gated rule outside its version).
 *
 * Expected behaviour:
 *   minSdk 24  -> suppressed by version gate, reported as gated, NOT a finding
 *   minSdk 34  -> reported as MEM-PRESSURE-001 MEDIUM (genuinely dead code at that floor)
 *
 * The scanner flip is driven by --min-sdk; see the fixture README.
 */
class LegacyPressureApp : Application() {

    // build.gradle for this fixture declares minSdk 24.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> trimCaches(aggressive = true)

            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> trimCaches(aggressive = false)
        }
    }

    private fun trimCaches(aggressive: Boolean) {
        // Real work on Android 13 and below.
    }
}
