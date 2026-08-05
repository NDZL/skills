package patterns.lifecycle

/*
 * STEP-04 -- scope a lifecycle registration.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * WHAT THIS REPLACES
 *
 *   override fun onCreate(savedInstanceState: Bundle?) {
 *       super.onCreate(savedInstanceState)
 *       registerReceiver(scanReceiver, IntentFilter(ACTION_SCAN))   // never removed
 *   }
 *
 * The signature enterprise leak. A receiver registered once and never removed holds the activity --
 * and its view tree, adapter, and bitmaps -- for the life of a process that lives a whole shift.
 * On a Zebra device the documented remedy for a leaking application is to uninstall it, so this is
 * a fleet-eviction risk, not just a slow degradation.
 *
 * THIS IS THE STEP MOST LIKELY TO CHANGE BEHAVIOUR. Before applying, confirm: does the app
 * legitimately need these events while stopped? If yes, scoping to start/stop will DROP them --
 * use a lifecycle-aware component or a foreground service instead, and say so.
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

// ---------------------------------------------------------------- Option A: symmetric start/stop

class ScanActivity : AppCompatActivity() {

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // handle the scan
        }
    }

    private var registered = false

    override fun onStart() {
        super.onStart()
        if (!registered) {
            registerReceiver(scanReceiver, IntentFilter(ACTION_SCAN))
            registered = true
        }
    }

    override fun onStop() {
        // Idempotent: must be safe if called twice, and must not throw during teardown.
        if (registered) {
            runCatching { unregisterReceiver(scanReceiver) }
            registered = false
        }
        super.onStop()
    }

    companion object {
        const val ACTION_SCAN = "com.example.ACTION_SCAN"
    }
}

// ---------------------------------------------------------------- Option B: lifecycle observer

/**
 * Preferred when several screens need the same registration: the symmetry cannot be forgotten
 * because it lives in one place.
 *
 * Use as: `lifecycle.addObserver(ScanRegistration(applicationContext, onScan))`
 */
class ScanRegistration(
    private val appContext: Context,
    private val onScan: (Intent) -> Unit,
) : DefaultLifecycleObserver {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onScan(intent)
    }

    private var registered = false

    override fun onStart(owner: LifecycleOwner) {
        if (!registered) {
            // The application context is captured, so no activity is retained by this object.
            appContext.registerReceiver(receiver, IntentFilter(ScanActivity.ACTION_SCAN))
            registered = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (registered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
    }
}

/*
 * COMPOSE EQUIVALENT
 *
 *   DisposableEffect(Unit) {
 *       val receiver = ...
 *       context.registerReceiver(receiver, filter)
 *       onDispose { runCatching { context.unregisterReceiver(receiver) } }
 *   }
 *
 * RELATED, same family, same fix shape:
 *   - null a fragment view binding when the view is destroyed, or the whole tree is retained
 *   - never hold a context, activity, view, or fragment in a static or companion object
 *   - never pass a context into a view model; inject, or expose state through a flow
 *   - scope coroutines to a lifecycle; a global-scope launch retains its captured receiver
 *
 * VERIFY: repeat the workflow 20 to 50 times, discard the first few for warm-up, and confirm the
 * RssAnon slope flattens. Express the result PER BUSINESS TRANSACTION -- per pick, per scan -- not
 * per hour: accumulation tracks work done, which is why the app fails first for the fastest
 * operators. Also confirm the activity count in the memory breakdown returns to the number
 * actually open.
 */
