/*
 * Zebra DataWedge result plumbing for the dw-adf skill.
 *
 * Purpose: give the process EXACTLY ONE consumer of
 * "com.symbol.datawedge.api.RESULT_ACTION" and route every result to the single
 * AdfIntentOutputConfigurator that owns the profile.
 *
 * Reviewed against DataWedge 15.0 documentation on 2026-08-04.
 * Achieved validation level: DEVICE. Compiled and exercised on a Zebra TC701,
 * Android 15 (SDK 35), DataWedge 15.0.73 on 2026-08-04. The COMMAND_IDENTIFIER
 * routing was observed correctly dropping a foreign result mid-flight.
 *
 * Owner: Nicola DZL for testing purposes
 * License / redistribution: original code authored for the dw-adf skill.
 */

package com.example.dwadf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Process-wide router for DataWedge API results.
 *
 * Why this exists: DataWedge broadcasts one result to whoever is listening. If two
 * components each register their own receiver and each own ADF state, both react to
 * the same result and push competing configuration. The router keeps the fan-in at
 * one, whichever way the receiver was registered.
 */
object DwResultRouter {

    private const val TAG = "DwResultRouter"

    // Single registered owner. Not a list - a list would re-introduce the very
    // competing-owner problem this router exists to prevent.
    @Volatile
    private var owner: AdfIntentOutputConfigurator? = null

    /** Call once, from the component that constructs the configurator. */
    @Synchronized
    fun register(configurator: AdfIntentOutputConfigurator) {
        val existing = owner
        check(existing == null || existing === configurator) {
            "DwResultRouter already has an owner. Reuse it instead of creating a second one."
        }
        owner = configurator
    }

    /** Call from the same component's teardown path, symmetric with register(). */
    @Synchronized
    fun unregister(configurator: AdfIntentOutputConfigurator) {
        if (owner === configurator) owner = null
    }

    /**
     * Forward a result. Safe to call after teardown: with no owner the result is
     * dropped, which is the correct handling for a late broadcast.
     */
    fun route(intent: Intent) {
        val target = owner
        if (target == null) {
            // Expected during and after teardown - not an error.
            Log.d(TAG, "Result with no owner; dropping ${intent.action}")
            return
        }
        target.onDataWedgeResult(intent)
    }
}

/**
 * Manifest-declarable receiver for DataWedge results.
 *
 * Prefer the dynamic registration already performed by
 * AdfIntentOutputConfigurator.onHostStart(). Use this class only when results must
 * be observed while no host component is started.
 *
 * If you declare this receiver AND the host is started, one DataWedge result can
 * reach onDataWedgeResult twice - once through the configurator's own dynamic
 * receiver and once through here. That is tolerated, not desirable: the second
 * delivery finds the in-flight COMMAND_IDENTIFIER already cleared and is dropped as
 * a duplicate. Pick one path and stay on it; see AP-3 in references/anti-patterns.md.
 *
 * Caveat - verify on target: Android 8 restricts manifest-declared receivers for
 * implicit broadcasts. Whether DataWedge delivers RESULT_ACTION to a manifest
 * receiver on your DataWedge and Android version is `unknown` in this skill's
 * reviewed scope. Confirm on the target device before depending on it.
 */
class DwResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Keep this fast: onReceive runs on the main thread and the process may be
        // killed shortly after it returns.
        DwResultRouter.route(intent)
    }
}

/**
 * Receiver for the ADF-formatted scan data itself.
 *
 * This is the component whose intent action must match the Intent output plug-in's
 * `intent_action`, with `intent_delivery` = "2" (broadcast). If scans do not arrive
 * here BEFORE an ADF rule is added, a later ADF fault is indistinguishable from a
 * missing intent route - establish the baseline first.
 */
class ScanDataReceiver(
    private val onScan: (data: String, labelType: String?) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // These two extras are DataWedge intent-output data keys, not API keys.
        val data = intent.getStringExtra(EXTRA_DATA_STRING)
        val labelType = intent.getStringExtra(EXTRA_LABEL_TYPE)
        if (data == null) {
            Log.d(TAG, "Intent on ${intent.action} carried no scan data; ignoring")
            return
        }
        // `data` is the payload AFTER ADF processing. Compare it against the
        // expected output in assets/test-fixtures/adf-rule-cases/expected-cases.md.
        onScan(data, labelType)
    }

    companion object {
        private const val TAG = "ScanDataReceiver"

        /** Acquired barcode characters - this is the ADF-PROCESSED payload. */
        const val EXTRA_DATA_STRING = "com.symbol.datawedge.data_string"

        /** Barcode symbology of the scan. */
        const val EXTRA_LABEL_TYPE = "com.symbol.datawedge.label_type"

        /**
         * Raw, unmodified data as a byte-array list - NOT ADF-processed.
         * Best single diagnostic for ADF: compare data_string against decode_data.
         * If they are identical, the rule did not fire.
         */
        const val EXTRA_DECODE_DATA = "com.symbol.datawedge.decode_data"
    }
}
