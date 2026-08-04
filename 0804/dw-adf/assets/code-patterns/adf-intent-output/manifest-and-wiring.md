# Host wiring: manifest, definition, initialization, and run

Every callback in this pattern maps to an exact host action and an exact code location. A callback
name or a comment is not wiring.

- **Owner:** Nicola DZL for testing purposes
- **Reviewed:** 2026-08-04 against DataWedge 15.0
- **Achieved validation level:** `inspection` only — none of this was compiled, run, or exercised on
  a Zebra device
- **Licensing / redistribution:** original code authored for the `dw-adf` skill; no Zebra sample
  code or binaries are bundled

## Callback-to-host-action map

| Callback / signal | Exact host action | Code location |
|---|---|---|
| Host started | Register the result receiver, then reconcile | `AdfIntentOutputConfigurator.onHostStart()` |
| DataWedge API result broadcast | Match `COMMAND_IDENTIFIER`, read `RESULT` / `RESULT_INFO`, reconcile | `AdfIntentOutputConfigurator.onDataWedgeResult(intent)`, reached via `DwResultRouter.route(intent)` |
| `GET_CONFIG` readback result | Compare stored rule name, set `verifiedByReadback`, reconcile | `AdfIntentOutputConfigurator.readbackMatchesRequestedRule(intent)` |
| No result before timeout | Clear the token and reconcile (retry) | `AdfIntentOutputConfigurator.timeoutRunnable` |
| Terminal result code | Set `blockedReason`, stop retrying, surface to host | `AdfIntentOutputConfigurator.onDataWedgeResult(intent)` |
| Readiness state changed | Update UI / gate scanning | `onStateChanged: (AdfState) -> Unit` supplied by the host |
| Scan delivered (ADF-formatted) | Consume `com.symbol.datawedge.data_string` | `ScanDataReceiver.onReceive` |
| Host stopped | Unregister, cancel timeout, drop in-flight token | `AdfIntentOutputConfigurator.onHostStop()` |
| Owner discarded | Release the one-owner claim | `AdfIntentOutputConfigurator.release()` |

## AndroidManifest.xml

No Zebra permission is required to send DataWedge Intent API broadcasts. Declare only the scan-data
receiver, and only if you need a manifest-declared one:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>

        <activity
            android:name=".ScanHostActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!--
          Optional: manifest-declared receiver for the ADF-formatted scan data.
          The action MUST equal the profile's intent_action.
          Verify on target: Android 8+ restricts manifest receivers for implicit
          broadcasts, and whether DataWedge delivers to a manifest receiver on your
          DataWedge/Android version is `unknown` in this skill's reviewed scope.
          The dynamic registration in ScanHostActivity below is the supported route.
        -->
        <receiver
            android:name=".ScanDataReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.example.dwadf.ACTION_SCAN" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

### Choose one result-receiver path, not both

`AdfIntentOutputConfigurator.onHostStart()` already registers a dynamic receiver for
`RESULT_ACTION`. If you *also* declare `DwResultReceiver` in the manifest while the host is started,
one result can reach `onDataWedgeResult` twice. The `COMMAND_IDENTIFIER` check drops the second
delivery as a duplicate, so it is safe — but it is the duplicate-registration shape that AP-3 in
[../../../references/anti-patterns.md](../../../references/anti-patterns.md) warns about. Pick the
dynamic path unless you specifically need results with no started component.

## Define, initialize, and run

Complete host Activity. This is the whole setup: define the rule, construct the single owner, wire
both receivers, and run.

```kotlin
package com.example.dwadf

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ScanHostActivity : AppCompatActivity() {

    private lateinit var status: TextView

    // ---- 1. DEFINE the rule -------------------------------------------------
    // Outcome: a 12-digit barcode arrives as "000000123456" and must reach the app
    // as "123456". TRIM_LEFT_ZEROS strips the padding, SEND_REMAINING emits the rest.
    // Actions run top to bottom, so the order below IS the behavior.
    private val rule = AdfRule(
        name = "TrimLeadingZeros",
        allDevices = false,                       // scope explicitly, not by default
        devices = listOf(
            AdfDevice(
                deviceId = Dw.DEVICE_BARCODE,
                allDecoders = true,               // no unverified symbology token needed
            ),
        ),
        actions = listOf(
            AdfAction.trimLeftZeros(),
            AdfAction.sendRemaining(),            // terminal send: without it, output truncates
        ),
    )

    // ---- 2. INITIALIZE the single owner -------------------------------------
    // One instance per profile. Constructing a second one for the same profile
    // throws by design - see the LIVE_OWNERS check in the configurator.
    private val configurator by lazy {
        AdfIntentOutputConfigurator(
            context = this,
            profileName = PROFILE_NAME,
            rule = rule,
            // Supply intentOutput only if THIS app owns the intent-output settings.
            // If another component owns the profile, pass null and let it configure them.
            intentOutput = IntentOutputConfig(
                intentAction = SCAN_ACTION,
                delivery = Dw.INTENT_DELIVERY_BROADCAST,   // "2"
            ),
            // false MERGES with existing config. true would clear it and discard
            // any sibling ADF rules already on this profile.
            resetAdfConfig = false,
            configMode = Dw.CONFIG_MODE_UPDATE,
            onStateChanged = ::renderState,       // host action for readiness changes
        )
    }

    // Receiver for the ADF-formatted scan payload.
    private val scanReceiver = ScanDataReceiver { data, labelType ->
        Log.d(TAG, "scan: data=$data labelType=$labelType")
        status.text = "Scanned: $data"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).also { setContentView(it) }
        // Register the single result owner with the process-wide router so a
        // manifest-declared DwResultReceiver can also reach it.
        DwResultRouter.register(configurator)
    }

    // ---- 3. RUN ------------------------------------------------------------
    override fun onStart() {
        super.onStart()

        // Scan data receiver: register dynamically, matching the profile's intent_action.
        val scanFilter = IntentFilter(SCAN_ACTION).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, scanFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(scanReceiver, scanFilter)
        }

        // Satisfies readiness R2 and then reconciles. This is the ONLY start call
        // needed: onHostStart() applies the ADF config and verifies it by readback,
        // in whichever order those complete.
        configurator.onHostStart()
    }

    override fun onStop() {
        // Symmetric teardown. A late DataWedge result after this point is dropped.
        configurator.onHostStop()
        runCatching { unregisterReceiver(scanReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            DwResultRouter.unregister(configurator)
            configurator.release()            // frees the one-owner-per-profile claim
        }
        super.onDestroy()
    }

    // ---- Host action for readiness state -----------------------------------
    private fun renderState(state: AdfState) {
        when {
            state.blockedReason != null ->
                // Terminal: EMPTY_RULE_NAME, UNLICENSED_FEATURE, OPERATION_NOT_ALLOWED,
                // or retries exhausted. Do not retry; show it and stop.
                status.text = "ADF blocked: ${state.blockedReason}"

            state.verifiedByReadback ->
                // Only state that proves DataWedge actually stored the rule.
                status.text = "ADF rule '${rule.name}' verified. Ready to scan."

            state.configAcknowledged ->
                status.text = "ADF accepted; verifying by readback..."

            state.receiverReady ->
                status.text = "Applying ADF rule..."

            else -> status.text = "Starting..."
        }
    }

    companion object {
        private const val TAG = "ScanHostActivity"
        private const val PROFILE_NAME = "AdfIntentDemo"
        private const val SCAN_ACTION = "com.example.dwadf.ACTION_SCAN"
    }
}
```

## Verification steps

1. Launch the app. `renderState` should progress to **"ADF rule 'TrimLeadingZeros' verified"**. That
   text appears only after a `GET_CONFIG` readback returned the rule — a success result alone does
   not produce it.
2. In the DataWedge UI, open the profile → Advanced Data Formatting and confirm the rule exists with
   the Intent output plug-in selected.
3. Scan a fixture input from
   [../../test-fixtures/adf-rule-cases/expected-cases.md](../../test-fixtures/adf-rule-cases/expected-cases.md)
   and compare the logged `data` against the expected output.
4. If the payload is unchanged, compare `com.symbol.datawedge.data_string` against
   `com.symbol.datawedge.decode_data`. Identical values mean the rule did not fire — go to
   [../../../references/troubleshooting.md](../../../references/troubleshooting.md).

## Limitations and stop conditions

- One rule per configurator. Multi-rule submission and rule ordering are `unknown` in the reviewed
  scope — see the stop conditions in `SKILL.md`.
- `intent_delivery` accepted API values in the reviewed table are `0`, `1`, and `2`. The DataWedge UI
  also offers *startForegroundService*, which has no documented API integer in that table; treat it
  as `unknown`.
- Manifest-declared result receivers: `unknown — verify on target`.
- This code is `inspection`-level only. Compiling it, running it, and scanning on a stated Zebra
  device with a stated DataWedge version are all required next steps.
