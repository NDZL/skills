# API patterns

> **Compatibility gate — resolve before selecting an API route.**
>
> | Dimension | Declared scope |
> |---|---|
> | Product | Zebra DataWedge |
> | Reviewed version (source of truth) | **DataWedge 15.0** — `SET_CONFIG` ADF processing parameters |
> | Corroborating versions | DataWedge 14.0 for the ADF concept model and stated ADF limits only |
> | Documented elsewhere | `SEND_RESULT` values `NONE` / `LAST_RESULT` / `COMPLETE_RESULT` are documented for **DataWedge 7.1 or higher**; versions below 7.1 accept only `TRUE` / `FALSE` |
> | Platform | Android host app sending Zebra DataWedge Intent API broadcasts |
> | Output plugin | `INTENT` only. `KEYSTROKE` and `IP` are **out of scope** |
> | Input source | `BARCODE` is the in-scope `device_id`. `MSR`, `RFID`, `SERIAL`, `VOICE` are documented values but **unverified** here |
> | Android OS versions | `unknown — verify on target` |
> | Zebra device models | `unknown — verify on target` |
> | License / feature | A result of `UNLICENSED_FEATURE` means the requested capability is license-gated. **Stop; do not retry** |
> | Minimum DataWedge version for ADF via `SET_CONFIG` | **`unknown`** — no reviewed source states one |
> | Source review date | 2026-08-04 |
> | Device-verified on | Zebra **TC701**, Android **15** (SDK 35), **DataWedge 15.0.73**, 2026-08-04 |
> | Achieved evidence for this gate | `device` for the write shape, rule ordering, and end-to-end transformation. `inspection` for everything not listed under "Device-verified corrections" below |
>
> **⚠ The published SET_CONFIG reference does not match device behaviour.** Building the
> documented ADF bundle shape returns `RESULT=SUCCESS` and creates **no rule at all**.
> Read [Device-verified corrections](#device-verified-corrections) before writing any
> ADF configuration code.
>
> **Valid combination.** DataWedge 15.0 + `PLUGIN_NAME` = `ADF` + `OUTPUT_PLUGIN_NAME` = `INTENT` +
> `device_id` = `BARCODE`.
>
> **Invalid combinations.** ADF plug-in config sent without `OUTPUT_PLUGIN_NAME`; a plain
> `GET_CONFIG` used to read ADF back (process plug-ins require `PROCESS_PLUGIN_NAME`); the
> `SEND_RESULT` enum used on DataWedge below 7.1.
>
> **Stop as `unknown`.** Atomic multi-rule submission and rule ordering; RFID-source ADF and BDF;
> any `decoder` token other than the attested `Australian Postal`; any DataWedge version outside
> 15.0.

Provenance and review dates for every fact below: [sources.md](sources.md).

## Intent surface

| Purpose | Exact string |
|---|---|
| Command action | `com.symbol.datawedge.api.ACTION` |
| Set configuration extra | `com.symbol.datawedge.api.SET_CONFIG` |
| Get configuration extra | `com.symbol.datawedge.api.GET_CONFIG` |
| Get configuration result extra | `com.symbol.datawedge.api.RESULT_GET_CONFIG` |
| Result action (host receiver) | `com.symbol.datawedge.api.RESULT_ACTION` |
| Result category (host receiver) | `android.intent.category.DEFAULT` |
| Request results | extra `SEND_RESULT` = `NONE` \| `LAST_RESULT` \| `COMPLETE_RESULT` (DataWedge 7.1+) |
| Correlate a command | extra `COMMAND_IDENTIFIER` = caller-chosen string |
| Result payload keys | `RESULT`, `COMMAND`, `COMMAND_IDENTIFIER`, `RESULT_INFO`, `RESULT_LIST` (`RESULT_LIST` with `COMPLETE_RESULT`) |

## SET_CONFIG main bundle

| Key | Type | Notes |
|---|---|---|
| `PROFILE_NAME` | String | Target profile |
| `CONFIG_MODE` | String | `CREATE_IF_NOT_EXIST` \| `OVERWRITE` \| `UPDATE` |
| `PROFILE_ENABLED` | String | `true` / `false` |
| `PLUGIN_CONFIG` | Bundle or Bundle array | One entry per plug-in being configured |
| `APP_LIST` | Array | Profile-to-app association — **owned by the DataWedge profile skill, not this one** |

## PLUGIN_CONFIG entry for ADF

| Key | Value for this skill | Notes |
|---|---|---|
| `PLUGIN_NAME` | `ADF` | The ADF process plug-in |
| `OUTPUT_PLUGIN_NAME` | `INTENT` | **ADF/BDF only.** Documented values: `KEYSTROKE`, `INTENT`, `IP`. Omitting this key leaves the ADF config unbound |
| `RESET_CONFIG` | `false` to merge, `true` to replace | `true` (the default) clears any existing configuration and creates a new one; `false` merges the existing configuration with the changes |
| `PARAM_LIST` | Bundle | ADF processing parameters below |

## Device-verified corrections

Established by `GET_CONFIG` readback on **DataWedge 15.0.73 / TC701 / Android 15**, after the
documented shape silently produced no rule. **Use this column, not the documentation**, and re-verify
on your own DataWedge version.

| # | Published reference says | Device actually does |
|---|---|---|
| 1 | `PARAM_LIST` is a Bundle containing an `ADF_RULE` bundle | `PARAM_LIST` is an **ordered `ParcelableArrayList` of rule bundles**. There is no `ADF_RULE` wrapper on write or read |
| 2 | `adf_enabled` is a `PARAM_LIST` key | `adf_enabled` is a **sibling of `PARAM_LIST`** inside the ADF `PLUGIN_CONFIG` entry. Placed inside `PARAM_LIST` it is ignored and reads back `false` |
| 3 | (not documented) | Every `ACTIONS` entry carries **`action_index`** — an explicit ordering key, set as a string `"0"`, `"1"`, … |
| 4 | `device_id` ∈ `BARCODE`, `MSR`, `RFID`, `SERIAL`, `VOICE` | Readback reports **`plugin_input_scanner`**, `plugin_input_rfid`, `plugin_input_serial`, `plugin_input_voice`, `plugin_input_workflow`. Whether the documented token is accepted on write is **unverified** |
| 5 | Multi-rule submission and ordering: not documented | **Multiple rules in one call are supported.** Rule order is list order, and **the first matching rule wins** |
| 6 | Result codes imply `RESULT_INFO` reports problems | `RESULT_INFO` is **not empty on success** — it carries `{PROFILE_NAME=<profile>}` alongside `RESULT=SUCCESS`. Treating a non-empty `RESULT_INFO` as failure breaks every success |
| 7 | Only `Australian Postal` appears as an example `decoder` | Readback enumerates **53 decoders** for `plugin_input_scanner`. Enumerate them from a readback rather than guessing tokens |

Also confirmed: a single rule with **35 actions** was accepted and stored intact, so no low action
cap exists on this version. A 12-character output from 13-character input was delivered end to end.

### The rule-ordering trap

If a profile has no rules, DataWedge auto-creates `Rule0` with a single `SEND_REMAINING`. `Rule0` has
**no criteria, so it matches everything**, and it sits **first**. With `RESET_CONFIG` = `false` a
newly merged rule lands *after* it and therefore never runs — data arrives untransformed with no
error anywhere. Merging cannot reposition a rule.

To control order you must send `RESET_CONFIG` = `true` and re-supply every rule you need, in order,
most specific first, with any catch-all passthrough **last**. That conflicts with the sibling-rule
safety advice in [anti-patterns.md](anti-patterns.md) — see AP-11, which reconciles the two.

## ADF PARAM_LIST

| Key | Placement | Type | Accepted values / default |
|---|---|---|---|
| `adf_enabled` | ADF `PLUGIN_CONFIG` entry, **beside** `PARAM_LIST` | String | `true` / `false` (default `false`) |
| `PARAM_LIST` | ADF `PLUGIN_CONFIG` entry | `ParcelableArrayList<Bundle>` | **Ordered list of rule bundles** |
| `ADF_RULE` | — | Bundle | Documented as the rule wrapper inside `PARAM_LIST`; **not observed on device**. Retained only for reading older/other versions |

Each rule bundle contains `name`, `enabled`, `alldevices`, `string`, `string_pos`, `string_len`, and
the `ACTIONS`, `DEVICES`, `DECODERS`, and `LABEL_IDS` sub-lists.

### ADF_RULE keys

| Key | Type | Default | Meaning |
|---|---|---|---|
| `name` | String | required | Rule name. An empty name yields `RESULT_ACTION_RESULT_CODE_EMPTY_RULE_NAME` |
| `enabled` | String | `true` | Rule enabled |
| `alldevices` | String | `true` | Accept data from all supported input sources |
| `string` | String | empty | String to check for (criteria) |
| `string_pos` | String | `0` | String position (criteria) |
| `string_len` | String | `0` | String length (criteria) |

### ACTIONS (bundle array, ordered)

| Key | Meaning |
|---|---|
| `type` | Action name from the action table below |
| `action_index` | **Device-observed, undocumented.** Explicit ordering, string `"0"`, `"1"`, … Set it on write to make order unambiguous |
| `action_param_1` | First parameter, when the action takes one |
| `action_param_2` | Second parameter, when the action takes one |
| `action_param_3` | Third parameter, when the action takes one |

Actions execute from top to bottom. Order is the behavior — the same action set in a different
order produces different output.

### DEVICES (bundle array)

| Key | Default | Meaning |
|---|---|---|
| `device_id` | required | Documented: `BARCODE`, `MSR`, `RFID`, `SERIAL`, `VOICE`. **Device reports `plugin_input_scanner`, `plugin_input_rfid`, `plugin_input_serial`, `plugin_input_voice`, `plugin_input_workflow`** — see correction 4 |
| `enabled` | `true` | Accept data from that input source |
| `alldecoders` | `true` | Allow all barcode symbologies |
| `all_label_ids` | `true` | Allow all UDI label IDs |

Safest scoping until a token is confirmed by readback: `alldevices` = `true` with no `DEVICES` list,
using `string_len` / `string` / `string_pos` for selectivity. That is the combination that was
device-verified.

### DECODERS (bundle array)

| Key | Default | Meaning |
|---|---|---|
| `device_id` | required | `BARCODE`, `MSR`, `RFID`, `SERIAL` or `VOICE` |
| `decoder` | required | Symbology name. Only `Australian Postal` is attested in the reviewed source |
| `enabled` | `true` | Enable that decoder for the rule |

### LABEL_IDS (bundle array)

| Key | Default | Meaning |
|---|---|---|
| `device_id` | required | `BARCODE`, `MSR`, `RFID`, `SERIAL` or `VOICE` |
| `label_id` | required | `UDI_GS1`, `UDI_HIBCC` or `UDI_ICCBBA` |
| `enabled` | `true` | Enable that label ID for the rule |

## ADF action reference

| Category | `type` | Parameters |
|---|---|---|
| Cursor | `SKIP_AHEAD` | `action_param_1` = character count |
| Cursor | `SKIP_BACK` | `action_param_1` = character count |
| Cursor | `SKIP_TO_START` | none |
| Cursor | `MOVE_AHEAD_TO` | `action_param_1` = string |
| Cursor | `MOVE_PAST_A` | `action_param_1` = string |
| Modify | `CRUNCH_SPACES` / `STOP_CRUNCH_SPACE` | none |
| Modify | `REMOVE_SPACES` / `STOP_REMOVE_SPACES` | none |
| Modify | `TRIM_LEFT_ZEROS` / `STOP_TRIM_LEFT_ZEROS` | none |
| Modify | `PAD_LEFT_ZEROS` / `STOP_PAD_LEFT_ZEROS` | `action_param_1` = count |
| Modify | `PAD_LEFT_SPACES` / `STOP_PAD_LEFT_SPACES` | `action_param_1` = count |
| Modify | `REPLACE_STRING` / `STOP_REPLACE_ALL` | `action_param_1` = find, `action_param_2` = replace |
| Modify | `REMOVE_CHARACTERS` / `STOP_REMOVE_CHARS` | `action_param_1` = position (`0` front, `1` between, `2` end, `3` center), `action_param_2` = start, `action_param_3` = count |
| Send | `SEND_NEXT` | `action_param_1` = character count |
| Send | `SEND_REMAINING` | none |
| Send | `SEND_UP_TO` | `action_param_1` = string |
| Send | `SEND_STRING` | `action_param_1` = string |
| Send | `SEND_CHAR` | `action_param_1` = ASCII/Unicode character |
| Send | `DELAY` | `action_param_1` = milliseconds, maximum `120000` |
| Notify | `BEEP` | `action_param_1` = tone name |

The `STOP_*` actions end a previously started modification mode; they are not standalone
transformations.

## Documented ADF limits and caveats

- If a profile is created without at least one rule, DataWedge creates a `Rule0` with a single
  `SEND_REMAINING` action that passes data through unmodified.
- ADF rules do not support selecting which scanner was used when multiple scanners are configured.
- Control characters (codes 1–31, except ENTER and TAB) cannot be sent via `SEND_CHAR`.
- Maximum `DELAY` is 120 seconds. Zebra recommends ~50 ms pauses after ENTER, LINE FEED, or TAB to
  minimize data loss.
- ADF applies to Keystroke, IP, and Intent output. Binding is per output plug-in — configuring ADF
  for `INTENT` does not configure it for `KEYSTROKE`.

## Intent output plugin parameters

Needed because an ADF rule bound to `INTENT` only has an effect when intent output is actually
delivering data.

| Key | Accepted values |
|---|---|
| `intent_output_enabled` | `true` / `false` |
| `intent_action` | exact action string the host app receives |
| `intent_category` | exact category string |
| `intent_delivery` | `0` start activity, `1` start service, `2` broadcast |
| `intent_component_info` | bundle array: `PACKAGE_NAME`, `SIGNATURE` (SHA1 hash) |
| `intent_use_content_provider` | `true` / `false` |

## Reading configuration back (freshness)

Process plug-ins cannot be read with a plain `GET_CONFIG`; the `PROCESS_PLUGIN_NAME` parameter is
required. The reviewed source assembles it as a `ParcelableArrayList<Bundle>` placed inside the
`PLUGIN_CONFIG` bundle:

```java
Bundle bMain = new Bundle();
bMain.putString("PROFILE_NAME", "DWDemo");
Bundle bConfig = new Bundle();
ArrayList<Bundle> pluginName = new ArrayList<>();

Bundle pluginInternal = new Bundle();
pluginInternal.putString("PLUGIN_NAME", "ADF");
pluginInternal.putString("OUTPUT_PLUGIN_NAME","KEYSTROKE");
pluginName.add(pluginInternal);
bConfig.putParcelableArrayList("PROCESS_PLUGIN_NAME", pluginName);
bMain.putBundle("PLUGIN_CONFIG", bConfig);

Intent i = new Intent();
i.setAction("com.symbol.datawedge.api.ACTION");
i.putExtra("com.symbol.datawedge.api.GET_CONFIG", bMain);
this.sendBroadcast(i);
```

The reviewed sample uses `KEYSTROKE`; this skill substitutes `INTENT`. The result arrives in
`com.symbol.datawedge.api.RESULT_GET_CONFIG` carrying `PROFILE_NAME`, `PROFILE_ENABLED`, and a
`PLUGIN_CONFIG` parcelable array list. The Kotlin equivalent is
[AdfIntentOutputConfigurator.kt](../assets/code-patterns/adf-intent-output/AdfIntentOutputConfigurator.kt).

A readback is the **only** fresh confirmation that a rule exists. A cached "we configured this
already" flag is not equivalent: DataWedge configuration can also be changed by the user, by an MX
configuration, or by another app.

### Actual readback shape (device-observed)

```text
com.symbol.datawedge.api.RESULT_GET_CONFIG : Bundle
├── PROFILE_NAME  = "<profile>"
└── PLUGIN_CONFIG : List<Bundle>  [size=1]
    └── [0]
        ├── PLUGIN_NAME = "ADF"
        ├── adf_enabled = "true"          # plug-in level, NOT inside PARAM_LIST
        └── PARAM_LIST : List<Bundle>     # one entry PER RULE, in evaluation order
            ├── [0] name="MyRule", enabled, alldevices, string, string_pos,
            │        string_len, ACTIONS[...], DEVICES[...], DECODERS[53], LABEL_IDS[3]
            └── [1] name="Rule0", ... ACTIONS[{type=SEND_REMAINING, action_index=0}]
```

Parse `PARAM_LIST` as a list of rules and match on each entry's `name`. Reading it as a Bundle and
looking for `ADF_RULE` finds nothing on this version. **`PARAM_LIST` order is evaluation order**, so a
readback is also how you confirm your rule sits ahead of any catch-all.

### Detecting success correctly

`RESULT` carries the outcome; `RESULT_INFO` carries detail and is **not empty on success**. Test
`RESULT == "SUCCESS"` only, and inspect `RESULT_INFO` *values* against the terminal codes below.
Requiring an empty `RESULT_INFO` makes every success look like a failure.

Critically: `RESULT=SUCCESS` confirms the command was **accepted**, not that a rule was created. The
documented bundle shape returns `SUCCESS` and creates nothing. Only a readback proves creation.

## Lifecycle, threading, and ownership

| Concern | Contract |
|---|---|
| Command ownership | Exactly one component per profile constructs and sends ADF `SET_CONFIG`. Two owners produce interleaved, order-dependent config |
| Result ownership | Exactly one registered receiver per process consumes `com.symbol.datawedge.api.RESULT_ACTION` and forwards to the single owner |
| Threading | `BroadcastReceiver.onReceive` runs on the main thread. Keep the handler non-blocking; never block it waiting for a result |
| Correlation | Set `COMMAND_IDENTIFIER` on every command and compare it on receipt. Unmatched identifiers are late or foreign results and must be dropped |
| Command queueing | DataWedge does **not** queue API commands; a command sent while DataWedge is busy may be ignored, and Zebra recommends delay code before critical commands. Serialize one in-flight command at a time and reconcile on timeout |
| Idempotency | Re-sending the same rule with `CONFIG_MODE` = `UPDATE` and `RESET_CONFIG` = `false` is the intended repair path |
| Cleanup | Unregister the result receiver symmetrically with registration and invalidate the in-flight token |

### Result codes

| Code | Meaning |
|---|---|
| `RESULT_ACTION_RESULT_CODE_EMPTY_RULE_NAME` | ADF rule name undefined — set `ADF_RULE.name` |
| `PLUGIN_BUNDLE_INVALID` | Plug-in parameter bundle empty or incomplete |
| `PARAMETER_INVALID` | Parameters empty, null, or invalid |
| `PLUGIN_NOT_SUPPORTED` | Plug-in configuration attempted on an unsupported plug-in |
| `PROFILE_NOT_FOUND` | Operation attempted on a nonexistent profile |
| `PROFILE_NAME_EMPTY` | Profile name field empty |
| `APP_ALREADY_ASSOCIATED` | App already linked to a different profile |
| `OPERATION_NOT_ALLOWED` | Protected-profile modification, or Profile0 app association |
| `BUNDLE_EMPTY` | Bundle contains no data |
| `UNLICENSED_FEATURE` | License-gated capability — stop |

## Safe patterns and caveats

- Always terminate an `ACTIONS` list with a send action that emits the rest of the data unless
  truncation is intended. See [troubleshooting.md](troubleshooting.md).
- Prefer `CONFIG_MODE` = `UPDATE` with `RESET_CONFIG` = `false` when other rules or plug-in settings
  must survive. See [anti-patterns.md](anti-patterns.md).
- Scope a rule with `alldevices` = `false` plus an explicit `DEVICES` entry rather than relying on
  the permissive default.
- Treat every symbology token except `Australian Postal` as unverified until a readback confirms it.
- Applicable version and platform scope for all of the above is the gate at the top of this file.
