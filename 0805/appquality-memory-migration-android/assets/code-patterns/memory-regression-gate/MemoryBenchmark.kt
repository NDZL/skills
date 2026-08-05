package patterns.gate

/*
 * STEP-10 -- add a memory regression gate.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * *** THIS STEP REDUCES NOTHING TODAY. *** It makes future growth observable, which is otherwise
 * completely invisible: you learn about it from field kills, months later, with no stack trace.
 *
 * TRACK RSS, NOT PSS. RSS is better for tracking changes in memory allocation and is cheaper to
 * compute; PSS is proportional attribution and is slow. Neither is the Java heap -- keep the unit
 * consistent with whatever the baseline recorded.
 *
 * THE CRITICAL LIMITATION: absolute values are NOT portable across devices. Only DELTAS ON ONE
 * DEVICE are a valid signal. Emulator CI is acceptable for deltas and must be labelled as not
 * validating headroom.
 */

import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Steady state on the primary screen. Keep the scenario IDENTICAL across runs -- a changed
     * scenario invalidates every comparison, which is the most common way this gate goes wrong.
     */
    @Test
    fun steadyStatePrimaryScreen() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // Drive the same fixed workload every run. No random data, no network variance.
        device.waitForIdle()
    }

    /**
     * The heavy path, which is usually the binding one: sync and bulk work run while the UI is
     * hidden, and a not-visible process gets the MORE RESTRICTIVE limit. Measuring only the
     * interactive screen flatters the app exactly where it is weakest.
     */
    @Test
    fun heavyPathWhileNotVisible() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // trigger the sync, then background the app so the tighter ceiling applies
        device.pressHome()
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.enterprise"
    }
}

/*
 * COMMIT A BASELINE next to the benchmark, in the repository, like a startup baseline profile:
 *
 *   memory-baseline.json
 *   {
 *     "device": "TC26", "ram": "3GB", "os": "A16",
 *     "unit": "rss", "scenario": "steadyStatePrimaryScreen",
 *     "state": "visible", "value_mb": 121, "recorded": "2026-08-04"
 *   }
 *
 * Without a committed baseline there is nothing to compare against, and the missing baseline is
 * itself the higher-priority finding.
 *
 * GATE IN CI: fail when steady-state RSS regresses beyond an agreed percentage, or when headroom
 * drops a band. Pin the CI device model; a device change invalidates the baseline and must reset it
 * deliberately rather than silently.
 *
 * VERIFY THE GATE ITSELF: introduce a deliberate regression and confirm the build fails; then
 * confirm an unchanged tree passes. A gate that has never failed has not been tested.
 */
