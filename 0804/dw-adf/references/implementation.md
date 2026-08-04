# Implementation

## Compatibility gate

The authoritative gate is at the top of [api-patterns.md](api-patterns.md). Clear it first. In
short: DataWedge 15.0 reviewed, `INTENT` output plug-in only, `BARCODE` input source, Android OS
versions and Zebra device models `unknown — verify on target`, and `UNLICENSED_FEATURE` is a stop.

## Prerequisites and setup checks

Confirm each of these before sending anything. None requires a shell or network.

| Prerequisite | How to confirm without tooling |
|---|---|
| DataWedge is present and enabled on the device | DataWedge app is visible in the launcher / app list; profile list opens |
| A target profile exists, is enabled, and is associated with the host app | Inspect the profile in the DataWedge UI, or hand the association work to the DataWedge profile skill — `APP_LIST` is not owned here |
| The Intent output plug-in is enabled with a known `intent_action` and `intent_delivery` | Profile → Intent output in the DataWedge UI; the host app must actually receive scans **before** ADF is introduced, otherwise a silent ADF fault is indistinguishable from a missing intent route |
| The host app declares or registers a receiver for `com.symbol.datawedge.api.RESULT_ACTION` | See [manifest-and-wiring.md](../assets/code-patterns/adf-intent-output/manifest-and-wiring.md) |
| Existing ADF rules on that profile are known | Request a readback first. Do not push a rule blind — `RESET_CONFIG` = `true` would discard the rules you have not looked at |

Baseline check, in order: scans reach the app **without** ADF → then add the rule → then confirm the
transformation. Skipping the baseline makes every later failure ambiguous.

## New and existing project integration

**New project.** Copy the whole
[assets/code-patterns/adf-intent-output/](../assets/code-patterns/adf-intent-output/) directory into
one package. Construct a single `AdfIntentOutputConfigurator` — the app must have exactly one per
profile — pass the profile name and the intent-output action, and call `onHostStart()` from the host
lifecycle. Do not construct a second one in a fragment, service, or view model.

**Existing project.** If the app already sends DataWedge Intent API commands, do **not** add a second
result receiver. Route the existing receiver's `onReceive` into
`AdfIntentOutputConfigurator.onDataWedgeResult(intent)` and delete any competing ADF push path. If
the app already owns profile creation, keep that ownership there and give this configurator only the
ADF plug-in bundle, sent with `CONFIG_MODE` = `UPDATE` and `RESET_CONFIG` = `false`.

### The bundle tree to build

**Device-verified on DataWedge 15.0.73 / TC701 / Android 15.** It differs from the published
SET_CONFIG reference: the documented shape (`PARAM_LIST` as a Bundle wrapping `ADF_RULE`, with
`adf_enabled` inside `PARAM_LIST`) returns `RESULT=SUCCESS` and creates **no rule at all**. See
[Device-verified corrections](api-patterns.md).

```text
Intent action: com.symbol.datawedge.api.ACTION
└── extra "com.symbol.datawedge.api.SET_CONFIG" : Bundle
    ├── PROFILE_NAME    = "<profile>"
    ├── PROFILE_ENABLED = "true"
    ├── CONFIG_MODE     = "UPDATE"          # CREATE_IF_NOT_EXIST | OVERWRITE | UPDATE
    └── PLUGIN_CONFIG   : ArrayList<Bundle>
        └── [0] : Bundle                    # the ADF process plug-in
            ├── PLUGIN_NAME        = "ADF"
            ├── OUTPUT_PLUGIN_NAME = "INTENT"   # binds ADF to intent output
            ├── RESET_CONFIG       = "false"    # false merges; true clears + lets you ORDER
            ├── adf_enabled        = "true"     # PLUG-IN LEVEL, beside PARAM_LIST
            └── PARAM_LIST : ArrayList<Bundle>  # ORDERED LIST OF RULES, first match wins
                ├── [0] : Bundle                # most specific rule FIRST
                │   ├── name       = "<rule name>"  # empty => EMPTY_RULE_NAME result
                │   ├── enabled    = "true"
                │   ├── alldevices = "true"     # safest until a device_id is confirmed
                │   ├── string     = ""         # criteria: string to check for
                │   ├── string_pos = "0"
                │   ├── string_len = "13"       # criteria: exact input length
                │   ├── DEVICES  : ArrayList<Bundle>   # omit when alldevices="true"
                │   ├── DECODERS : ArrayList<Bundle>   # only when alldecoders="false"
                │   ├── LABEL_IDS: ArrayList<Bundle>   # only for UDI work
                │   └── ACTIONS  : ArrayList<Bundle>   # ordered, top to bottom
                │       ├── [0] type="TRIM_LEFT_ZEROS", action_index="0"
                │       └── [1] type="SEND_REMAINING",  action_index="1"
                └── [1] : Bundle                # OPTIONAL catch-all passthrough, LAST
                    ├── name="PassThroughEverythingElse", enabled="true"
                    ├── alldevices="true", string="", string_pos="0", string_len="0"
                    └── ACTIONS: [{ type="SEND_REMAINING", action_index="0" }]
└── extra "SEND_RESULT"        = "LAST_RESULT"
└── extra "COMMAND_IDENTIFIER" = "<correlation token>"
```

**Order is behaviour.** A rule with no criteria matches everything, so it must come last. The
auto-created `Rule0` is exactly such a rule and merging cannot move it — see the ordering trap in
[api-patterns.md](api-patterns.md) and AP-11 in [anti-patterns.md](anti-patterns.md).

The maintained code equivalent is
[AdfIntentOutputConfigurator.kt](../assets/code-patterns/adf-intent-output/AdfIntentOutputConfigurator.kt).
Worked outcomes are in [usage-patterns.md](usage-patterns.md).

## Integration ownership and readiness

ADF configuration is asynchronous, and its prerequisites complete **independently and in either
order**. This section is the contract; the anti-patterns it protects are in
[anti-patterns.md](anti-patterns.md).

### Component and host ownership

| Component | Owner | Owns |
|---|---|---|
| DataWedge service | Zebra, external to the app | Profile store, ADF rule store, actual data transformation |
| `AdfIntentOutputConfigurator` | Host app, **exactly one instance per profile** | Bundle construction, in-flight token, readiness state, `reconcileAdfState()` |
| `DwResultReceiver` | Host app, **exactly one registration per process** | Receiving `com.symbol.datawedge.api.RESULT_ACTION` and forwarding it |
| Host Activity or Service | Host app | Receiver registration lifetime; consuming the ADF-formatted scan payload |

### Independently completed readiness prerequisites

| ID | Prerequisite | Completion signal | Invalidation signal |
|---|---|---|---|
| R1 | DataWedge present, enabled, not busy | A result arrives for any sent command | No result within the timeout; DataWedge disabled or upgraded |
| R2 | Result receiver registered | `registerReceiver` returns in `onHostStart()` | `onHostStop()` unregisters |
| R3 | Profile exists, enabled, associated with the app | `RESULT` success for the profile command, or a readback showing the profile | `PROFILE_NOT_FOUND`, `APP_ALREADY_ASSOCIATED`, `OPERATION_NOT_ALLOWED` |
| R4 | ADF plug-in applied for `OUTPUT_PLUGIN_NAME` = `INTENT` | `RESULT` success for the ADF `SET_CONFIG` **and** a matching readback | `PLUGIN_BUNDLE_INVALID`, `PARAMETER_INVALID`, `EMPTY_RULE_NAME`, or a readback that lacks the rule |
| R5 | Intent output enabled with the action the host receives | A scan actually arrives at the host component | `intent_output_enabled` = `false`, or a changed `intent_action` |

R2 and R4 are the pair that most often completes out of order: a `SET_CONFIG` result can be
broadcast before the host has finished registering its receiver, in which case the result is simply
never seen.

### Reconciliation

There is exactly one repair path: **`reconcileAdfState()`**. It is idempotent, safe to call
repeatedly, and re-entrancy-guarded. Every prerequisite that can complete last calls it:

| Trigger | Caller |
|---|---|
| Receiver became ready (R2) | `onHostStart()` → `reconcileAdfState()` |
| A command result arrived (R1, R3, R4) | `onDataWedgeResult()` → `reconcileAdfState()` |
| A readback arrived (R4 freshness) | `onDataWedgeResult()` → `reconcileAdfState()` |
| Nothing arrived before the timeout (R1) | timeout runnable → `reconcileAdfState()` |
| Host resumed after a warm restart | `onHostStart()` → `reconcileAdfState()` |

Retrying from only one of these is a **one-sided retry** and produces a missed wakeup: if the app
retries only when a result arrives, the "result arrived before registration" order never recovers,
and if it retries only at registration, a slow DataWedge never recovers. Both edges must call the
same function.

### Freshness

`R4` is satisfied only by a **fresh** `GET_CONFIG` + `PROCESS_PLUGIN_NAME` readback whose returned
rule matches what was requested. A cached "already configured" boolean, a previous session's
`SharedPreferences` flag, or a success result alone does **not** satisfy it — DataWedge configuration
can be changed by the user, by MX, or by another app between runs. `verifiedForToken` is therefore
reset on every invalidation signal.

### Cancellation and teardown

| Event | Required action |
|---|---|
| New command superseding an in-flight one | Rotate `COMMAND_IDENTIFIER`; results carrying the old token are dropped |
| Host stopping | `onHostStop()` unregisters the receiver, cancels the pending timeout, and clears the in-flight token |
| Late result after teardown | Dropped — the receiver is unregistered and the token no longer matches |
| Process death | Nothing persists; the next `onHostStart()` reconciles from scratch |

Never disable the DataWedge profile during teardown. The profile outlives the host app and other
components may depend on it.

### Completion orders to verify

| Case | Expected behavior |
|---|---|
| Cold start, receiver ready before result | Result observed, readback requested, state verified once |
| Cold start, result broadcast before receiver ready | Timeout fires, `reconcileAdfState()` re-sends, state converges |
| Warm restart, rule already applied | Readback confirms; **no** redundant `SET_CONFIG` |
| Duplicate result for one command | Second result is a no-op; no second readback storm |
| Late result after `onHostStop()` | Dropped; no crash, no state mutation |
| Cancellation mid-flight | Old token's result ignored; only the newest command settles state |
| Shutdown during a pending timeout | Timeout removed; no leaked receiver |

## Manual fallbacks

This skill ships **no scripts** and makes **no network calls**, so there is no script route to fall
back from. When you cannot run the host app at all, the manual procedure is:

1. Reproduce the bundle tree above on paper and check every key name against
   [api-patterns.md](api-patterns.md).
2. Apply the same rule by hand in the DataWedge UI: Profile → Advanced Data Formatting → Rules, with
   the Intent output plug-in selected, then compare the UI's rule to your bundle field by field.
3. Scan a fixture input from
   [expected-cases.md](../assets/test-fixtures/adf-rule-cases/expected-cases.md) and compare the
   delivered payload to the expected output.

What remains unverified without execution: that DataWedge accepted the bundle, that key spellings are
correct on the installed version, that the symbology token resolved, and the actual transformed
output. Report these as unverified rather than assuming success.

## Verification and result format

1. Send the ADF `SET_CONFIG` with `SEND_RESULT` = `LAST_RESULT` and a `COMMAND_IDENTIFIER`.
2. Confirm the result's `COMMAND_IDENTIFIER` matches and inspect `RESULT` / `RESULT_INFO`.
3. Request the `GET_CONFIG` + `PROCESS_PLUGIN_NAME` readback and confirm the rule name, criteria, and
   ordered actions came back as sent.
4. Scan the fixture input and compare the delivered payload against the expected output.
5. Report in this shape:

```markdown
- Achieved level: inspection | build and behavior | device | production review
- Verified:
- Not verified:
- Warnings:
- Blocking errors:
- Required next actions:
```

Achieved level, owner, provenance, and redistribution basis for the bundled code are recorded in
[sources.md](sources.md). Bundling a rule is not evidence of acceptance; only a matching readback,
and ultimately a scan on a real device, is.
