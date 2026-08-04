/*
 * Zebra DataWedge - apply ONE Advanced Data Formatting (ADF) rule to a Profile's
 * INTENT output plugin via the SET_CONFIG Intent API.
 *
 * Reviewed against DataWedge 15.0 documentation on 2026-08-04.
 *
 * Achieved validation level: DEVICE. Built, installed, and exercised end to end on a
 * Zebra TC701, Android 15 (SDK 35), DataWedge 15.0.73 on 2026-08-04: a 13-character
 * EAN-13 scan was reordered by ADF and delivered transformed to the host app.
 *
 * IMPORTANT - the bundle shape here differs from the published SET_CONFIG reference.
 * The documented shape (PARAM_LIST as a Bundle wrapping an ADF_RULE bundle, with
 * adf_enabled inside PARAM_LIST) returned RESULT=SUCCESS while creating NO rule. The
 * shape below is what the device accepts and reports. See references/api-patterns.md
 * "Device-verified corrections". Re-verify on your own DataWedge version.
 *
 * Owner: Nicola DZL for testing purposes
 * License / redistribution: original code authored for the dw-adf skill. No Zebra
 * sample code, binaries, or credentials are included.
 *
 * Skill route: SKILL.md -> references/implementation.md -> this file.
 */

package com.example.dwadf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

// ---------------------------------------------------------------------------
// 1. DataWedge Intent API surface.
//    Every string here is copied verbatim from the DataWedge 15.0 docs. Do not
//    "tidy" them: they are wire identifiers, not local names.
// ---------------------------------------------------------------------------

object Dw {
    // Command channel.
    const val ACTION_API = "com.symbol.datawedge.api.ACTION"
    const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    const val EXTRA_GET_CONFIG = "com.symbol.datawedge.api.GET_CONFIG"

    // Result channel. The host must register for ACTION_RESULT + CATEGORY_DEFAULT.
    const val ACTION_RESULT = "com.symbol.datawedge.api.RESULT_ACTION"
    const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
    const val EXTRA_RESULT_GET_CONFIG = "com.symbol.datawedge.api.RESULT_GET_CONFIG"

    // Result request + correlation. SEND_RESULT values below require DataWedge 7.1+.
    const val EXTRA_SEND_RESULT = "SEND_RESULT"
    const val SEND_RESULT_LAST = "LAST_RESULT"
    const val EXTRA_COMMAND_IDENTIFIER = "COMMAND_IDENTIFIER"

    // Result payload keys.
    const val EXTRA_RESULT = "RESULT"
    const val EXTRA_COMMAND = "COMMAND"
    const val EXTRA_RESULT_INFO = "RESULT_INFO"

    // SET_CONFIG main bundle keys.
    const val PROFILE_NAME = "PROFILE_NAME"
    const val PROFILE_ENABLED = "PROFILE_ENABLED"
    const val CONFIG_MODE = "CONFIG_MODE"
    const val PLUGIN_CONFIG = "PLUGIN_CONFIG"

    // CONFIG_MODE values.
    const val CONFIG_MODE_CREATE_IF_NOT_EXIST = "CREATE_IF_NOT_EXIST"
    const val CONFIG_MODE_OVERWRITE = "OVERWRITE"
    const val CONFIG_MODE_UPDATE = "UPDATE"

    // PLUGIN_CONFIG entry keys.
    const val PLUGIN_NAME = "PLUGIN_NAME"
    const val OUTPUT_PLUGIN_NAME = "OUTPUT_PLUGIN_NAME"
    const val RESET_CONFIG = "RESET_CONFIG"
    const val PARAM_LIST = "PARAM_LIST"

    // Plug-in names used by this pattern.
    const val PLUGIN_ADF = "ADF"
    const val PLUGIN_INTENT = "INTENT"

    // ADF PARAM_LIST keys.
    const val ADF_ENABLED = "adf_enabled"
    const val ADF_RULE = "ADF_RULE"
    const val ACTIONS = "ACTIONS"
    const val DEVICES = "DEVICES"
    const val DECODERS = "DECODERS"
    const val LABEL_IDS = "LABEL_IDS"

    // ADF_RULE keys.
    const val RULE_NAME = "name"
    const val RULE_ENABLED = "enabled"
    const val RULE_ALL_DEVICES = "alldevices"
    const val RULE_STRING = "string"
    const val RULE_STRING_POS = "string_pos"
    const val RULE_STRING_LEN = "string_len"

    // ACTIONS keys.
    const val ACTION_TYPE = "type"

    /**
     * Explicit action ordering key. Not documented in the DataWedge 15.0 SET_CONFIG
     * reference, but reported by the device on GET_CONFIG readback.
     */
    const val ACTION_INDEX = "action_index"
    const val ACTION_PARAM_1 = "action_param_1"
    const val ACTION_PARAM_2 = "action_param_2"
    const val ACTION_PARAM_3 = "action_param_3"

    // DEVICES / DECODERS / LABEL_IDS keys.
    const val DEVICE_ID = "device_id"
    const val ALL_DECODERS = "alldecoders"
    const val ALL_LABEL_IDS = "all_label_ids"
    const val DECODER = "decoder"
    const val LABEL_ID = "label_id"

    /**
     * DOCUMENTED input source token. The published reference lists BARCODE / MSR /
     * RFID / SERIAL / VOICE, but DataWedge 15.0.73 reports the stored values as
     * plugin_input_scanner / plugin_input_rfid / plugin_input_serial /
     * plugin_input_voice / plugin_input_workflow on GET_CONFIG readback.
     *
     * Whether the documented token is accepted on write was NOT established: the
     * device-validated rule used alldevices="true" with no DEVICES list. Confirm the
     * token by readback before scoping a rule with it.
     */
    const val DEVICE_BARCODE = "BARCODE"

    /** Input source token as REPORTED by DataWedge 15.0.73 readback. */
    const val DEVICE_ID_SCANNER_REPORTED = "plugin_input_scanner"

    // Intent output plug-in PARAM_LIST keys.
    const val INTENT_OUTPUT_ENABLED = "intent_output_enabled"
    const val INTENT_ACTION = "intent_action"
    const val INTENT_CATEGORY = "intent_category"
    const val INTENT_DELIVERY = "intent_delivery"

    // intent_delivery values.
    const val INTENT_DELIVERY_START_ACTIVITY = "0"
    const val INTENT_DELIVERY_START_SERVICE = "1"
    const val INTENT_DELIVERY_BROADCAST = "2"

    // GET_CONFIG process plug-in query key.
    const val PROCESS_PLUGIN_NAME = "PROCESS_PLUGIN_NAME"

    // Result codes this pattern reacts to. UNLICENSED_FEATURE is terminal.
    const val CODE_EMPTY_RULE_NAME = "RESULT_ACTION_RESULT_CODE_EMPTY_RULE_NAME"
    const val CODE_UNLICENSED_FEATURE = "UNLICENSED_FEATURE"

    /** Result codes that must NOT be retried - retrying cannot fix them. */
    val TERMINAL_RESULT_CODES = setOf(
        CODE_EMPTY_RULE_NAME,
        CODE_UNLICENSED_FEATURE,
        "PLUGIN_NOT_SUPPORTED",
        "OPERATION_NOT_ALLOWED",
        "APP_ALREADY_ASSOCIATED",
    )
}

// ---------------------------------------------------------------------------
// 2. Typed ADF model.
//    Mirrors the documented bundle shape 1:1 so a rule can be reviewed as data
//    before it becomes a Bundle.
// ---------------------------------------------------------------------------

/** One ADF action. Actions run top to bottom - list order IS the behavior. */
data class AdfAction(
    val type: String,
    val param1: String? = null,
    val param2: String? = null,
    val param3: String? = null,
) {
    companion object {
        // Convenience builders for the actions this skill documents. Parameter
        // counts follow the DataWedge 15.0 action table.
        fun trimLeftZeros() = AdfAction("TRIM_LEFT_ZEROS")
        fun sendRemaining() = AdfAction("SEND_REMAINING")
        fun skipAhead(characters: Int) = AdfAction("SKIP_AHEAD", characters.toString())
        fun sendNext(characters: Int) = AdfAction("SEND_NEXT", characters.toString())
        fun sendUpTo(text: String) = AdfAction("SEND_UP_TO", text)
        fun sendString(text: String) = AdfAction("SEND_STRING", text)
        fun movePastA(text: String) = AdfAction("MOVE_PAST_A", text)
        fun replaceString(find: String, replaceWith: String) =
            AdfAction("REPLACE_STRING", find, replaceWith)

        /** position: 0 front, 1 between, 2 end, 3 center. */
        fun removeCharacters(position: Int, start: Int, count: Int) =
            AdfAction("REMOVE_CHARACTERS", position.toString(), start.toString(), count.toString())

        fun padLeftZeros(count: Int) = AdfAction("PAD_LEFT_ZEROS", count.toString())

        /** DataWedge caps DELAY at 120000 ms. */
        fun delay(milliseconds: Int) =
            AdfAction("DELAY", milliseconds.coerceIn(0, 120_000).toString())

        /** Actions that emit the rest of the buffer. A rule should normally end with one. */
        val REMAINDER_EMITTING = setOf("SEND_REMAINING")
    }
}

/** An input source the rule accepts data from. */
data class AdfDevice(
    val deviceId: String = Dw.DEVICE_BARCODE,
    val enabled: Boolean = true,
    val allDecoders: Boolean = true,
    val allLabelIds: Boolean = true,
)

/**
 * A symbology restriction. Only "Australian Postal" is attested verbatim in the
 * reviewed source - confirm any other token by GET_CONFIG readback before trusting it.
 */
data class AdfDecoder(
    val decoder: String,
    val deviceId: String = Dw.DEVICE_BARCODE,
    val enabled: Boolean = true,
)

/** A UDI label ID restriction: UDI_GS1, UDI_HIBCC or UDI_ICCBBA. */
data class AdfLabelId(
    val labelId: String,
    val deviceId: String = Dw.DEVICE_BARCODE,
    val enabled: Boolean = true,
)

/** One ADF rule: criteria plus an ordered action list. */
data class AdfRule(
    val name: String,
    val actions: List<AdfAction>,
    val enabled: Boolean = true,
    /** false + an explicit DEVICES entry is safer than the permissive default. */
    val allDevices: Boolean = false,
    val matchString: String = "",
    val stringPosition: Int = 0,
    val stringLength: Int = 0,
    val devices: List<AdfDevice> = listOf(AdfDevice()),
    val decoders: List<AdfDecoder> = emptyList(),
    val labelIds: List<AdfLabelId> = emptyList(),
) {
    init {
        // An empty name is rejected by DataWedge with EMPTY_RULE_NAME. Fail locally
        // instead of paying a broadcast round trip to learn that.
        require(name.isNotBlank()) { "ADF_RULE.name must not be blank" }
        require(actions.isNotEmpty()) { "An ADF rule needs at least one action" }
    }

    /**
     * True when the action list ends by emitting the remainder. When false the rule
     * truncates output - intentional sometimes, a silent data-loss bug usually.
     */
    val emitsRemainder: Boolean
        get() = actions.last().type in AdfAction.REMAINDER_EMITTING
}

/** Intent output plug-in settings. Optional: omit when another owner manages them. */
data class IntentOutputConfig(
    val intentAction: String,
    val intentCategory: String = Dw.CATEGORY_DEFAULT,
    val delivery: String = Dw.INTENT_DELIVERY_BROADCAST,
    val enabled: Boolean = true,
)

/** Observable readiness snapshot. All five map to R1-R5 in implementation.md. */
data class AdfState(
    val receiverReady: Boolean = false,
    val configAcknowledged: Boolean = false,
    val verifiedByReadback: Boolean = false,
    val lastResultInfo: String? = null,
    /** Non-null means a terminal failure: stop, do not retry. */
    val blockedReason: String? = null,
) {
    val isSettled: Boolean get() = verifiedByReadback || blockedReason != null
}

// ---------------------------------------------------------------------------
// 3. The single owner.
//    ONE instance per profile per process. Two owners interleave SET_CONFIG
//    commands and the last writer silently wins.
// ---------------------------------------------------------------------------

class AdfIntentOutputConfigurator private constructor(
    context: Context,
    private val profileName: String,
    private val rule: AdfRule,
    /**
     * Rules appended AFTER [rule], in order. Use for a catch-all passthrough so input
     * that fails the primary rule's criteria still reaches the app.
     */
    private val fallbackRules: List<AdfRule> = emptyList(),
    private val intentOutput: IntentOutputConfig? = null,
    /** false merges with existing config; true CLEARS it, discarding sibling rules. */
    private val resetAdfConfig: Boolean = false,
    private val configMode: String = Dw.CONFIG_MODE_UPDATE,
    /** DataWedge does not queue commands, so an unanswered command needs a retry. */
    private val commandTimeoutMs: Long = 4_000L,
    private val maxAttempts: Int = 3,
) {

    /**
     * Host sink for readiness changes. Assign on attach and clear on teardown, or a
     * process-scoped configurator would retain a destroyed Activity.
     *
     * Assigning replays the current state immediately, so a recreated host renders
     * the right thing without waiting for the next transition.
     */
    var stateListener: ((AdfState) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(state)
        }

    // Application context only: this object outlives any single Activity instance.
    private val appContext: Context = context.applicationContext

    // All mutable state is confined to the main thread. onReceive already runs
    // there, so no extra locking is needed - only discipline.
    private val mainHandler = Handler(Looper.getMainLooper())

    private var receiver: BroadcastReceiver? = null
    private var inFlightToken: String? = null
    private var attempts = 0
    private var readbackAttempts = 0
    private var reconciling = false
    private var readbackRequested = false
    private var state = AdfState()

    private val timeoutRunnable = Runnable {
        // R1 completed last (or never). Same repair path as every other trigger.
        Log.w(TAG, "No DataWedge result within ${commandTimeoutMs}ms; reconciling")
        inFlightToken = null
        reconcileAdfState()
    }

    init {
        // Single ownership is enforced by the per-profile registry in the companion
        // object, NOT by throwing here. An earlier version threw from this constructor
        // when the profile was already claimed, which crashed the app on ordinary
        // activity recreation: Android creates the new instance before destroying the
        // old one, so an overlapping claim is normal and transient, not a defect.
        if (!rule.emitsRemainder) {
            Log.w(
                TAG,
                "Rule '${rule.name}' does not end with SEND_REMAINING; output will be truncated.",
            )
        }
    }

    // -- Host lifecycle callbacks ------------------------------------------
    // Wire these to the host exactly as named. See manifest-and-wiring.md.

    /**
     * Call from Activity.onStart() / Service.onCreate().
     * Satisfies R2 (receiver registered) and then reconciles, because R2 may be
     * the prerequisite that completed LAST.
     */
    fun onHostStart() {
        if (receiver == null) {
            val filter = IntentFilter().apply {
                addAction(Dw.ACTION_RESULT)
                addCategory(Dw.CATEGORY_DEFAULT)
            }
            val created = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onDataWedgeResult(intent)
                }
            }
            // DataWedge is a different app, so on Android 13+ the receiver must be
            // explicitly exported to receive its broadcasts.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(created, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(created, filter)
            }
            receiver = created
        }
        updateState(state.copy(receiverReady = true))
        reconcileAdfState()
    }

    /**
     * Call from Activity.onStop() / Service.onDestroy().
     * Teardown is symmetric with onHostStart: unregister, cancel the timeout, and
     * drop the in-flight token so a late result cannot mutate state.
     */
    fun onHostStop() {
        receiver?.let {
            runCatching { appContext.unregisterReceiver(it) }
                .onFailure { error -> Log.w(TAG, "Receiver already unregistered", error) }
        }
        receiver = null
        mainHandler.removeCallbacks(timeoutRunnable)
        inFlightToken = null
        readbackRequested = false
        updateState(state.copy(receiverReady = false))
    }

    /**
     * Release the single-owner claim when this configurator is discarded for good.
     * Prefer [AdfIntentOutputConfigurator.release] so the registry entry is dropped too.
     */
    fun release() {
        stateListener = null
        onHostStop()
        releaseProfile(profileName)
    }

    /**
     * The ONLY host callback for DataWedge results. Call from your BroadcastReceiver
     * (see DwResultReceiver.kt) or from an existing receiver the app already owns.
     * Never register a second receiver for ACTION_RESULT.
     */
    fun onDataWedgeResult(intent: Intent) {
        if (intent.action != Dw.ACTION_RESULT) return

        // A readback answers the freshness question, so handle it first.
        if (intent.hasExtra(Dw.EXTRA_RESULT_GET_CONFIG)) {
            mainHandler.removeCallbacks(timeoutRunnable)

            // Diagnostic: print the returned tree so a mismatch can be read off logcat
            // instead of guessed at. Capture with: adb logcat -s ADF_VALIDATION:I
            dumpReadback(intent.getBundleExtra(Dw.EXTRA_RESULT_GET_CONFIG))

            val matched = readbackMatchesRequestedRule(intent)
            readbackRequested = false
            updateState(state.copy(verifiedByReadback = matched))
            if (!matched) {
                readbackAttempts++
                inFlightToken = null
                // Bounded. Without this cap an unrecognised readback shape retries
                // forever, flooding DataWedge with broadcasts and never converging.
                if (readbackAttempts >= maxAttempts) {
                    Log.w(TAG, "Readback never contained rule '${rule.name}'; giving up")
                    updateState(
                        state.copy(
                            blockedReason = "Readback did not confirm rule after " +
                                "$readbackAttempts attempts",
                        ),
                    )
                    return
                }
                Log.w(
                    TAG,
                    "Readback did not contain rule '${rule.name}'; " +
                        "retry $readbackAttempts/$maxAttempts",
                )
            }
            reconcileAdfState()
            return
        }

        val token = intent.getStringExtra(Dw.EXTRA_COMMAND_IDENTIFIER)
        // Drop foreign, duplicate, and late results. Without this check a stale
        // result from a superseded command settles state for the current one.
        if (token == null || token != inFlightToken) {
            Log.d(TAG, "Ignoring unmatched result token=$token inFlight=$inFlightToken")
            return
        }

        mainHandler.removeCallbacks(timeoutRunnable)
        inFlightToken = null

        val result = intent.getStringExtra(Dw.EXTRA_RESULT)
        val command = intent.getStringExtra(Dw.EXTRA_COMMAND)
        val info = readResultInfo(intent)
        Log.d(TAG, "result=$result command=$command info=$info")

        val terminal = info.values.firstOrNull { it in Dw.TERMINAL_RESULT_CODES }
        if (terminal != null) {
            // Retrying cannot fix these. Stop and surface the reason to the host.
            updateState(
                state.copy(
                    lastResultInfo = info.toString(),
                    blockedReason = terminal,
                ),
            )
            return
        }

        // RESULT carries the outcome; RESULT_INFO carries detail and is NOT empty on
        // success - DataWedge 15.0.73 returns {PROFILE_NAME=<profile>} alongside
        // RESULT=SUCCESS. Requiring an empty RESULT_INFO here made every success read
        // as a failure and retried into a blocked state. Error codes are handled by
        // the TERMINAL_RESULT_CODES check above, which inspects RESULT_INFO values.
        val succeeded = result.equals("SUCCESS", ignoreCase = true)
        updateState(
            state.copy(
                configAcknowledged = succeeded,
                lastResultInfo = if (info.isEmpty()) result else info.toString(),
            ),
        )
        reconcileAdfState()
    }

    // -- The single idempotent repair path ---------------------------------

    /**
     * Idempotent reconciliation. Every readiness prerequisite that can complete
     * last calls THIS function - registration, results, readbacks, and timeouts.
     * Retrying from only one edge causes a missed wakeup in the other order.
     */
    fun reconcileAdfState() {
        if (reconciling) return // re-entrancy guard: results can arrive during a send
        reconciling = true
        try {
            if (state.blockedReason != null) return           // terminal, do not retry
            if (!state.receiverReady) return                  // R2 outstanding; onHostStart resumes
            if (state.verifiedByReadback) return              // already settled - no redundant push
            if (inFlightToken != null) return                 // one command in flight at a time

            if (!state.configAcknowledged) {
                if (attempts >= maxAttempts) {
                    updateState(state.copy(blockedReason = "No DataWedge result after $attempts attempts"))
                    return
                }
                attempts++
                sendSetConfig()
                return
            }
            if (!readbackRequested) {
                // Success alone is not freshness. Ask DataWedge what it actually stored.
                readbackRequested = true
                sendReadback()
            }
        } finally {
            reconciling = false
        }
    }

    // -- Bundle construction ----------------------------------------------

    /**
     * The ADF process plug-in bundle, bound to the INTENT output plug-in.
     *
     * SHAPE NOTE - corrected from device evidence, DataWedge 15.0.73 on a TC701:
     * the documented write shape (PARAM_LIST as a Bundle containing an ADF_RULE
     * bundle, with adf_enabled inside PARAM_LIST) returned RESULT=SUCCESS but created
     * NO rule - a GET_CONFIG readback showed only the auto-generated Rule0. The shape
     * below mirrors what the device actually reports on readback:
     *   - adf_enabled is a sibling of PARAM_LIST at plug-in level, not inside it
     *   - PARAM_LIST is a LIST of rule bundles; there is no ADF_RULE wrapper
     *   - each ACTIONS entry carries an explicit action_index ordering key
     */
    fun buildAdfPluginBundle(): Bundle {
        // Rule ORDER is the list order, and order decides behaviour: the first matching
        // rule wins. Device evidence, DataWedge 15.0.73: with the auto-created Rule0
        // (no criteria, plain SEND_REMAINING) ahead of a length-scoped rule, Rule0
        // matched every scan and the scoped rule never ran - the payload arrived
        // untransformed. The primary rule therefore goes FIRST, and any catch-all
        // passthrough must come after it in fallbackRules.
        val ruleBundles = (listOf(rule) + fallbackRules).mapTo(ArrayList()) { toRuleBundle(it) }

        return Bundle().apply {
            putString(Dw.PLUGIN_NAME, Dw.PLUGIN_ADF)
            // Without OUTPUT_PLUGIN_NAME the ADF config is not bound to any output.
            putString(Dw.OUTPUT_PLUGIN_NAME, Dw.PLUGIN_INTENT)
            // true CLEARS existing config. Required when rule ORDER must be controlled,
            // because merging cannot reposition a rule ahead of Rule0. Only safe when
            // this app exclusively owns the profile and re-supplies every rule it needs.
            putString(Dw.RESET_CONFIG, resetAdfConfig.dw())
            // Plug-in level, NOT inside PARAM_LIST - see the shape note above.
            putString(Dw.ADF_ENABLED, true.dw())
            // PARAM_LIST is an ordered LIST of rules.
            putParcelableArrayList(Dw.PARAM_LIST, ruleBundles)
        }
    }

    /** One AdfRule to its bundle form, including explicit action ordering. */
    private fun toRuleBundle(rule: AdfRule): Bundle {
        return Bundle().apply {
            putString(Dw.RULE_NAME, rule.name)
            putString(Dw.RULE_ENABLED, rule.enabled.dw())
            putString(Dw.RULE_ALL_DEVICES, rule.allDevices.dw())
            putString(Dw.RULE_STRING, rule.matchString)
            putString(Dw.RULE_STRING_POS, rule.stringPosition.toString())
            putString(Dw.RULE_STRING_LEN, rule.stringLength.toString())

            // ACTIONS, DEVICES, DECODERS and LABEL_IDS are children of the rule.
            // action_index makes the ordering explicit rather than relying on list
            // position - the device reports this key, so it is set on write too.
            putParcelableArrayList(
                Dw.ACTIONS,
                rule.actions.mapIndexedTo(ArrayList()) { index, action ->
                    action.toBundle().apply { putString(Dw.ACTION_INDEX, index.toString()) }
                },
            )
            if (rule.devices.isNotEmpty()) {
                putParcelableArrayList(Dw.DEVICES, rule.devices.mapTo(ArrayList()) { it.toBundle() })
            }
            if (rule.decoders.isNotEmpty()) {
                putParcelableArrayList(Dw.DECODERS, rule.decoders.mapTo(ArrayList()) { it.toBundle() })
            }
            if (rule.labelIds.isNotEmpty()) {
                putParcelableArrayList(Dw.LABEL_IDS, rule.labelIds.mapTo(ArrayList()) { it.toBundle() })
            }
        }
    }

    /** Optional INTENT output plug-in bundle - only when this app owns those settings. */
    fun buildIntentOutputPluginBundle(config: IntentOutputConfig): Bundle {
        val paramList = Bundle().apply {
            putString(Dw.INTENT_OUTPUT_ENABLED, config.enabled.dw())
            putString(Dw.INTENT_ACTION, config.intentAction)
            putString(Dw.INTENT_CATEGORY, config.intentCategory)
            putString(Dw.INTENT_DELIVERY, config.delivery)
        }
        return Bundle().apply {
            putString(Dw.PLUGIN_NAME, Dw.PLUGIN_INTENT)
            putString(Dw.RESET_CONFIG, false.dw())
            putBundle(Dw.PARAM_LIST, paramList)
        }
    }

    /** The complete SET_CONFIG main bundle. */
    fun buildSetConfigBundle(): Bundle {
        val plugins = ArrayList<Bundle>()
        // Configure intent output first when owned here, so the ADF binding target
        // is enabled before ADF references it.
        intentOutput?.let { plugins.add(buildIntentOutputPluginBundle(it)) }
        plugins.add(buildAdfPluginBundle())

        return Bundle().apply {
            putString(Dw.PROFILE_NAME, profileName)
            putString(Dw.PROFILE_ENABLED, true.dw())
            putString(Dw.CONFIG_MODE, configMode)
            putParcelableArrayList(Dw.PLUGIN_CONFIG, plugins)
        }
    }

    // -- Sending -----------------------------------------------------------

    private fun sendSetConfig() {
        val token = nextToken()
        inFlightToken = token
        val intent = Intent(Dw.ACTION_API).apply {
            putExtra(Dw.EXTRA_SET_CONFIG, buildSetConfigBundle())
            putExtra(Dw.EXTRA_SEND_RESULT, Dw.SEND_RESULT_LAST) // DataWedge 7.1+
            putExtra(Dw.EXTRA_COMMAND_IDENTIFIER, token)
        }
        appContext.sendBroadcast(intent)
        // DataWedge may ignore a command sent while busy, so arm a timeout that
        // routes back into reconcileAdfState() rather than waiting forever.
        mainHandler.postDelayed(timeoutRunnable, commandTimeoutMs)
        Log.d(TAG, "SET_CONFIG sent (attempt $attempts, token=$token)")
    }

    private fun sendReadback() {
        val token = nextToken()
        inFlightToken = token

        // Process plug-ins (ADF/BDF) cannot be read with a plain GET_CONFIG: the
        // PROCESS_PLUGIN_NAME list is required.
        val processPlugin = Bundle().apply {
            putString(Dw.PLUGIN_NAME, Dw.PLUGIN_ADF)
            putString(Dw.OUTPUT_PLUGIN_NAME, Dw.PLUGIN_INTENT)
        }
        val pluginConfig = Bundle().apply {
            putParcelableArrayList(Dw.PROCESS_PLUGIN_NAME, arrayListOf(processPlugin))
        }
        val main = Bundle().apply {
            putString(Dw.PROFILE_NAME, profileName)
            putBundle(Dw.PLUGIN_CONFIG, pluginConfig)
        }

        val intent = Intent(Dw.ACTION_API).apply {
            putExtra(Dw.EXTRA_GET_CONFIG, main)
            putExtra(Dw.EXTRA_SEND_RESULT, Dw.SEND_RESULT_LAST)
            putExtra(Dw.EXTRA_COMMAND_IDENTIFIER, token)
        }
        appContext.sendBroadcast(intent)
        mainHandler.postDelayed(timeoutRunnable, commandTimeoutMs)
        Log.d(TAG, "GET_CONFIG readback sent (token=$token)")
    }

    // -- Readback inspection ----------------------------------------------

    /**
     * Freshness check: does what DataWedge stored actually contain our rule name?
     * Walks the returned bundle tree defensively - shape varies with what was queried.
     */
    private fun readbackMatchesRequestedRule(intent: Intent): Boolean {
        val payload = intent.getBundleExtra(Dw.EXTRA_RESULT_GET_CONFIG) ?: return false
        val plugins: List<Bundle> = when {
            payload.containsKey(Dw.PLUGIN_CONFIG) -> {
                @Suppress("DEPRECATION")
                payload.getParcelableArrayList<Bundle>(Dw.PLUGIN_CONFIG)
                    ?: listOfNotNull(payload.getBundle(Dw.PLUGIN_CONFIG))
            }
            else -> emptyList()
        }
        // Real readback shape (DataWedge 15.0.73): each PLUGIN_CONFIG entry holds
        // PARAM_LIST as a LIST of rule bundles, each carrying `name` directly. The
        // documented ADF_RULE wrapper does not appear, so accept both.
        return plugins.any { plugin ->
            @Suppress("DEPRECATION")
            val rules: List<Bundle> = plugin.getParcelableArrayList<Bundle>(Dw.PARAM_LIST)
                ?: plugin.getBundle(Dw.PARAM_LIST)
                    ?.let { listOfNotNull(it.getBundle(Dw.ADF_RULE)) }
                ?: emptyList()

            val names = rules.mapNotNull { it.getString(Dw.RULE_NAME) }
            Log.d(TAG, "Readback rules for ${plugin.getString(Dw.PLUGIN_NAME)}: $names")
            rule.name in names
        }
    }

    /**
     * Recursively print a returned bundle tree to logcat under the validation tag.
     * Diagnostic only - nothing in the control flow depends on it.
     */
    private fun dumpReadback(payload: Bundle?) {
        if (payload == null) {
            Log.i(VALIDATION_TAG, "READBACK|null payload")
            return
        }
        Log.i(VALIDATION_TAG, "READBACK|begin")
        describe(payload, "  ").lineSequence().filter { it.isNotBlank() }.forEach {
            Log.i(VALIDATION_TAG, "READBACK|$it")
        }
        Log.i(VALIDATION_TAG, "READBACK|end")
    }

    @Suppress("DEPRECATION")
    private fun describe(bundle: Bundle, indent: String): String {
        val out = StringBuilder()
        for (key in bundle.keySet()) {
            when (val value = bundle.get(key)) {
                is Bundle -> {
                    out.append("$indent$key {\n")
                    out.append(describe(value, "$indent  "))
                    out.append("$indent}\n")
                }
                is List<*> -> {
                    out.append("$indent$key [size=${value.size}]\n")
                    value.forEachIndexed { index, item ->
                        if (item is Bundle) {
                            out.append("$indent  [$index] {\n")
                            out.append(describe(item, "$indent    "))
                            out.append("$indent  }\n")
                        } else {
                            out.append("$indent  [$index] = $item\n")
                        }
                    }
                }
                is Array<*> -> {
                    out.append("$indent$key [array=${value.size}]\n")
                    value.forEachIndexed { index, item ->
                        if (item is Bundle) {
                            out.append("$indent  [$index] {\n")
                            out.append(describe(item, "$indent    "))
                            out.append("$indent  }\n")
                        } else {
                            out.append("$indent  [$index] = $item\n")
                        }
                    }
                }
                else -> out.append("$indent$key = $value\n")
            }
        }
        return out.toString()
    }

    private fun readResultInfo(intent: Intent): Map<String, String> {
        val info = intent.getBundleExtra(Dw.EXTRA_RESULT_INFO) ?: return emptyMap()
        return info.keySet().associateWith { key -> info.getString(key) ?: "" }
    }

    // -- Helpers -----------------------------------------------------------

    private fun updateState(next: AdfState) {
        if (next == state) return // idempotent: no duplicate host notifications
        state = next
        stateListener?.invoke(next)
    }

    private fun nextToken(): String = "$profileName-adf-${TOKENS.incrementAndGet()}"

    companion object {
        private const val TAG = "AdfIntentOutput"

        /** Shared marker tag so config, verification and scans land in one capture. */
        private const val VALIDATION_TAG = "ADF_VALIDATION"

        private val TOKENS = AtomicLong(0)

        /**
         * The one-owner-per-profile registry. Process-scoped and keyed by profile, so
         * a recreated host reattaches to the SAME configurator instead of creating a
         * competing one - and keeps the readiness state it had already reached.
         */
        private val INSTANCES: MutableMap<String, AdfIntentOutputConfigurator> =
            Collections.synchronizedMap(mutableMapOf())

        /**
         * Obtain the single configurator for [profileName], creating it on first call.
         *
         * Construction arguments are honoured only on creation. A later call with
         * different arguments returns the existing owner unchanged, which is the point:
         * two callers cannot install competing configurations for one profile.
         */
        @Synchronized
        @JvmStatic
        fun forProfile(
            context: Context,
            profileName: String,
            rule: AdfRule,
            fallbackRules: List<AdfRule> = emptyList(),
            intentOutput: IntentOutputConfig? = null,
            resetAdfConfig: Boolean = false,
            configMode: String = Dw.CONFIG_MODE_UPDATE,
            commandTimeoutMs: Long = 4_000L,
            maxAttempts: Int = 3,
        ): AdfIntentOutputConfigurator {
            INSTANCES[profileName]?.let { existing ->
                Log.d(TAG, "Reusing existing owner for profile '$profileName'")
                return existing
            }
            val created = AdfIntentOutputConfigurator(
                context = context,
                profileName = profileName,
                rule = rule,
                fallbackRules = fallbackRules,
                intentOutput = intentOutput,
                resetAdfConfig = resetAdfConfig,
                configMode = configMode,
                commandTimeoutMs = commandTimeoutMs,
                maxAttempts = maxAttempts,
            )
            INSTANCES[profileName] = created
            return created
        }

        /** Drop the registry entry for [profileName]. Idempotent. */
        @Synchronized
        @JvmStatic
        fun releaseProfile(profileName: String) {
            INSTANCES.remove(profileName)
        }
    }
}

// DataWedge expects "true"/"false" STRINGS, never booleans, in these bundles.
private fun Boolean.dw(): String = if (this) "true" else "false"

private fun AdfAction.toBundle(): Bundle = Bundle().apply {
    putString(Dw.ACTION_TYPE, type)
    param1?.let { putString(Dw.ACTION_PARAM_1, it) }
    param2?.let { putString(Dw.ACTION_PARAM_2, it) }
    param3?.let { putString(Dw.ACTION_PARAM_3, it) }
}

private fun AdfDevice.toBundle(): Bundle = Bundle().apply {
    putString(Dw.DEVICE_ID, deviceId)
    putString(Dw.RULE_ENABLED, enabled.dw())
    putString(Dw.ALL_DECODERS, allDecoders.dw())
    putString(Dw.ALL_LABEL_IDS, allLabelIds.dw())
}

private fun AdfDecoder.toBundle(): Bundle = Bundle().apply {
    putString(Dw.DEVICE_ID, deviceId)
    putString(Dw.DECODER, decoder)
    putString(Dw.RULE_ENABLED, enabled.dw())
}

private fun AdfLabelId.toBundle(): Bundle = Bundle().apply {
    putString(Dw.DEVICE_ID, deviceId)
    putString(Dw.LABEL_ID, labelId)
    putString(Dw.RULE_ENABLED, enabled.dw())
}
