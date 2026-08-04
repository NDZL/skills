---
name: dw-adf
description: "Zebra DataWedge Advanced Data Formatting (ADF): author, modify, and verify one ADF rule applied to the Intent output plugin of a DataWedge Profile through the SET_CONFIG Intent API, using PLUGIN_NAME ADF with OUTPUT_PLUGIN_NAME INTENT, adf_enabled, ADF_RULE, ACTIONS (type, action_param_1 to action_param_3), DEVICES, DECODERS, and LABEL_IDS. Use when scanned data must be transformed before intent delivery - trim leading zeros, REPLACE_STRING, REMOVE_CHARACTERS, pad, skip or send segments, or scope a rule by symbology or input source - or when diagnosing RESULT_ACTION_RESULT_CODE_EMPTY_RULE_NAME, PLUGIN_BUNDLE_INVALID, or ADF rules that disappear after a config push. Do not use for general DataWedge profile creation or enablement, barcode and decoder parameters, SOFT_SCAN_TRIGGER, keystroke or IP output formatting, or BDF and RFID input-plugin formatting; hand those to the DataWedge profile and intent-output skill."
---

# dw-adf

## Release metadata

- Version: `1.0.0-beta.1`
- Stage: `Beta`

> **Beta:** This skill is available for early evaluation. Its workflows and behavioral contract
> may change before stable release. Validate generated output before production use. This status
> applies to the skill package—not to referenced Zebra products, SDKs, APIs, or models.

Apply exactly one Advanced Data Formatting rule to the **Intent output plugin** of a DataWedge
Profile, using the `SET_CONFIG` Intent API. Every route in this skill sends the ADF process
plug-in as `PLUGIN_NAME` = `ADF` bound to `OUTPUT_PLUGIN_NAME` = `INTENT`. ADF bound to
`KEYSTROKE` or `IP` output is out of scope.

## Supported task modes

| Mode | Use it for |
|---|---|
| `explain` | Explain the ADF rule/criteria/action model and the `SET_CONFIG` bundle tree without editing code |
| `create` | Add a new ADF rule to a profile's Intent output plugin |
| `modify` | Change an existing rule's criteria or action list without wiping sibling rules |
| `diagnose` | Investigate a rule that never fires, truncates data, or vanishes after a config push |
| `validate` | Read the applied rule back from DataWedge and report the achieved evidence level |

## Inspect → compatibility → implement → verify

1. **Inspect.** Identify the target profile name, whether it already exists, the app package
   associated with it, the Intent output plugin's `intent_action` and `intent_delivery`, and which
   ADF rules already exist. Never assume the profile is unconfigured.
2. **Compatibility.** Clear the gate at the top of
   [references/api-patterns.md](references/api-patterns.md) before choosing keys or values. This
   skill's reviewed scope is DataWedge 15.0. Stop and report `unknown` for anything the gate does
   not cover; do not infer key names or version behavior.
3. **Implement.** Build the bundle tree from
   [references/implementation.md](references/implementation.md), pick the outcome workflow in
   [references/usage-patterns.md](references/usage-patterns.md), and copy the maintained Kotlin from
   [assets/code-patterns/adf-intent-output/AdfIntentOutputConfigurator.kt](assets/code-patterns/adf-intent-output/AdfIntentOutputConfigurator.kt).
   Wire every host callback to the exact function named in
   [assets/code-patterns/adf-intent-output/manifest-and-wiring.md](assets/code-patterns/adf-intent-output/manifest-and-wiring.md).
4. **Integration contract.** ADF configuration is asynchronous and has more than one independently
   completed readiness prerequisite. Follow the ownership, readiness, reconciliation, freshness,
   invalidation, cancellation, and teardown tables in
   [references/implementation.md](references/implementation.md). Every prerequisite that can
   complete last must reach the same idempotent `reconcileAdfState()` entry point.
5. **Verify.** Read the rule back with `GET_CONFIG` + `PROCESS_PLUGIN_NAME`, compare against the
   expected case in
   [assets/test-fixtures/adf-rule-cases/expected-cases.md](assets/test-fixtures/adf-rule-cases/expected-cases.md),
   and report the level actually reached.

## Local task routes

| Route | Read this | Then use |
|---|---|---|
| Explain the ADF model / choose an action | [references/api-patterns.md](references/api-patterns.md) | — |
| Create or modify a rule | [references/implementation.md](references/implementation.md) then [references/usage-patterns.md](references/usage-patterns.md) | [AdfIntentOutputConfigurator.kt](assets/code-patterns/adf-intent-output/AdfIntentOutputConfigurator.kt) |
| Wire results and readiness | [references/implementation.md](references/implementation.md) | [DwResultReceiver.kt](assets/code-patterns/adf-intent-output/DwResultReceiver.kt) and [manifest-and-wiring.md](assets/code-patterns/adf-intent-output/manifest-and-wiring.md) |
| Rule never fires, truncates, or disappears | [references/troubleshooting.md](references/troubleshooting.md) | [expected-cases.md](assets/test-fixtures/adf-rule-cases/expected-cases.md) |
| Avoid a known-unsafe design | [references/anti-patterns.md](references/anti-patterns.md) | — |
| Check provenance, scope, ownership | [references/sources.md](references/sources.md) | — |

This skill ships **no scripts** and performs **no live checks**, so no script fallback is owed.
Every route above is text-first and works with no shell and no network access: all key names,
accepted values, and action parameters are recorded in the bundled references rather than fetched
at runtime.

## Stop conditions

> **Before writing any ADF bundle:** the published `SET_CONFIG` reference does not match device
> behaviour. The documented shape returns `RESULT=SUCCESS` and creates **no rule**. Use the
> device-verified shape in [references/api-patterns.md](references/api-patterns.md).

Stop, report `unknown`, and do not guess when any of these apply:
- **RFID input-source ADF, or BDF of any kind.** The Zebra support article on BDF/ADF for the RFID
  input plugin could not be retrieved during authoring, so no behavior is claimed. Hand off.
- **`KEYSTROKE` or `IP` output ADF.** Out of capability. Hand off.
- **General profile work** — creating or enabling profiles, `APP_LIST` association, barcode and
  decoder parameters, `SOFT_SCAN_TRIGGER`, scanner selection. Hand off to the DataWedge profile and
  intent-output skill.
- **DataWedge versions other than 15.0**, any Android OS version, and any specific Zebra device
  model: outside the reviewed gate. State `unknown — verify on target`.
- **`UNLICENSED_FEATURE`** in a result: the requested capability is license-gated. Stop; do not retry.
- **Exact `decoder` and `device_id` tokens.** The documented `device_id` vocabulary
  (`BARCODE`/`MSR`/`RFID`/…) does **not** match what DataWedge 15.0.73 reports
  (`plugin_input_scanner`/…). Enumerate the device's 53 decoders and its input-source tokens from a
  `GET_CONFIG` readback rather than trusting the documented vocabulary. Until a token is confirmed,
  scope with `alldevices` = `true` plus `string_len`/`string`/`string_pos`.

**Resolved by device evidence (DataWedge 15.0.73) — no longer `unknown`:**

- **Multiple rules and rule ordering.** `PARAM_LIST` is an ordered **list of rules**, several rules in
  one call are supported, and **the first matching rule wins**. Ordering requires
  `RESET_CONFIG` = `true` plus re-supplying every rule, most specific first, catch-all last. Merging
  cannot reposition a rule, so a scoped rule merged behind the auto-created `Rule0` never fires. See
  AP-11 in [references/anti-patterns.md](references/anti-patterns.md).

## Completion criteria and validation result

The task is complete only when all of the following hold:

- one `ADF_RULE` carries a non-empty `name`, its criteria, and an ordered `ACTIONS` list whose final
  action emits the remaining data (normally `SEND_REMAINING`) unless truncation is intended;
- the ADF plug-in bundle carries both `PLUGIN_NAME` = `ADF` and `OUTPUT_PLUGIN_NAME` = `INTENT`;
- `RESET_CONFIG` is set deliberately, with `false` used whenever sibling rules must survive;
- every host callback resolves to a named function in the bundled Kotlin, not to a comment;
- each readiness prerequisite reaches the single idempotent `reconcileAdfState()`; and
- a `GET_CONFIG` + `PROCESS_PLUGIN_NAME` readback was requested and its outcome reported.

Report the **achieved validation** level explicitly and never imply more than was collected:

- `inspection` — bundle tree, key names, and wiring reviewed against the bundled references;
- `build and behavior` — the host app actually compiled and ran the configurator;
- `device` — verified on a stated Zebra device running a stated DataWedge version; or
- `production review` — accountable owners approved the result.

Always state the achieved level, the evidence collected, unverified items, warnings or blockers, and
required next actions. Generating a `SET_CONFIG` bundle is not evidence that DataWedge accepted it;
only a matching readback is.
