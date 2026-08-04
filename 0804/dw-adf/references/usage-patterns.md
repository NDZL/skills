# Usage patterns

Every outcome below is bound to the same architecture: one ADF rule, `PLUGIN_NAME` = `ADF`,
`OUTPUT_PLUGIN_NAME` = `INTENT`, inside a named DataWedge profile.

Common to all outcomes:

- **Prerequisites and supported versions:** DataWedge 15.0 reviewed; see the gate in
  [api-patterns.md](api-patterns.md). Android OS and device model `unknown — verify on target`.
- **Bundled code location:** [../assets/code-patterns/adf-intent-output/](../assets/code-patterns/adf-intent-output/)
  — `AdfIntentOutputConfigurator.kt`, `DwResultReceiver.kt`, `manifest-and-wiring.md`.
- **Host callbacks and exact actions:** the callback-to-host-action map in
  [manifest-and-wiring.md](../assets/code-patterns/adf-intent-output/manifest-and-wiring.md).
- **Readiness, reconciliation, completion orders:** [implementation.md](implementation.md).
- **Provenance:** derived from the reviewed Zebra sources in [sources.md](sources.md).
- **Achieved validation level:** `device` for the write shape, rule ordering, success detection,
  readback parsing, and end-to-end transformation — built and scanned on a Zebra TC701, Android 15,
  DataWedge 15.0.73 on 2026-08-04. `inspection` for every action, input source, and result code not
  exercised there. See [sources.md](sources.md).
- **⚠ Bundle shape:** the published reference is wrong. Use the device-verified shape in
  [api-patterns.md](api-patterns.md) — the documented one returns `SUCCESS` and creates no rule.
- **Owner:** Nicola DZL for testing purposes.
- **Licensing / redistribution:** original code and fixtures authored for this skill; no Zebra sample
  code or binaries bundled.

## Outcome 1 — add a rule to an existing profile without disturbing it

**Developer outcome.** A scan already reaches the app; now the payload must be transformed before
delivery.

| Step | Do this |
|---|---|
| 1 | Confirm the baseline: scans arrive at the host **before** ADF is introduced |
| 2 | Read back existing ADF content (`GET_CONFIG` + `PROCESS_PLUGIN_NAME`) so you know what is already there |
| 3 | Build the rule with `CONFIG_MODE` = `UPDATE` and `RESET_CONFIG` = `false` |
| 4 | Send with `SEND_RESULT` = `LAST_RESULT` and a `COMMAND_IDENTIFIER` |
| 5 | Confirm by readback, not by the success result |

**Apply / run.** `AdfIntentOutputConfigurator(...)` then `onHostStart()`. Full host in
[manifest-and-wiring.md](../assets/code-patterns/adf-intent-output/manifest-and-wiring.md).

**Expected result.** `AdfState.verifiedByReadback` = `true`; the rule visible in the DataWedge UI
under the profile's Advanced Data Formatting with Intent output selected.

**Verification.** Fixture Case 1 in
[../assets/test-fixtures/adf-rule-cases/expected-cases.md](../assets/test-fixtures/adf-rule-cases/expected-cases.md).

**Limitations / stop conditions.** One rule per call. Ordering against existing rules is `unknown`.

## Outcome 2 — change an existing rule's actions

**Developer outcome.** The transformation is wrong, or a new step is needed.

Re-send the same `ADF_RULE.name` with the corrected `ACTIONS` list, `CONFIG_MODE` = `UPDATE`,
`RESET_CONFIG` = `false`. Reusing the name is what makes this an edit rather than an addition.

**Expected result.** The readback returns the rule with the new ordered action list.

**Verification.** Scan the same input twice — before and after — and compare `data_string`.

**Limitations.** Whether a same-name push replaces the action list wholesale or merges into it is not
stated in the reviewed source. Confirm by readback and, if the old actions persist, treat it as a
device-specific finding. Do **not** reach for `RESET_CONFIG` = `true` to force it: that discards
sibling rules. See [anti-patterns.md](anti-patterns.md).

## Outcome 3 — scope a rule to one symbology or input source

**Developer outcome.** The transformation must apply only to certain labels.

| Criteria | Keys |
|---|---|
| One input source | `alldevices` = `false` + `DEVICES` = [{`device_id` = `BARCODE`}] |
| One symbology | `DEVICES[0].alldecoders` = `false` + `DECODERS` = [{`device_id` = `BARCODE`, `decoder` = token}] |
| Content match | `ADF_RULE.string`, `string_pos`, `string_len` |
| UDI label ID | `all_label_ids` = `false` + `LABEL_IDS` = [{`label_id` = `UDI_GS1` \| `UDI_HIBCC` \| `UDI_ICCBBA`}] |

**Verification.** Fixture Cases 4 and 6. Always test a **non-matching control input** — a rule that
fires on everything is not scoped.

**Stop condition.** Only `Australian Postal` is an attested `decoder` token. Any other symbology
string must survive a readback before you trust it. ADF also cannot select *which* scanner produced
the data when several are configured.

## Outcome 4 — verify what DataWedge actually stored

**Developer outcome.** Prove the rule exists rather than assuming it.

Send `GET_CONFIG` with `PROCESS_PLUGIN_NAME` = [{`PLUGIN_NAME` = `ADF`, `OUTPUT_PLUGIN_NAME` =
`INTENT`}]. A plain `GET_CONFIG` cannot read process plug-ins and returns nothing about ADF.

**Code.** `AdfIntentOutputConfigurator.sendReadback()` and `readbackMatchesRequestedRule()`.

**Expected result.** `com.symbol.datawedge.api.RESULT_GET_CONFIG` whose `PARAM_LIST` **list**
contains a rule bundle whose `name` equals the rule sent. Parse `PARAM_LIST` as a list of rules;
looking for an `ADF_RULE` wrapper finds nothing. `PARAM_LIST` order is evaluation order, so this is
also how you confirm your rule sits ahead of any catch-all.

**Verification.** Fixture Case 7.

**Why it matters.** A cached "configured" flag is not freshness. The user, an MX configuration, or
another app can change DataWedge configuration between runs.

## Outcome 5 — diagnose a rule that does not fire

**Developer outcome.** Data arrives unchanged, or arrives truncated.

Fastest discriminator: compare `com.symbol.datawedge.data_string` (ADF-processed) with
`com.symbol.datawedge.decode_data` (raw). Identical → the rule never fired. Different but wrong →
the rule fired and the action list is wrong.

Then work the symptom table in [troubleshooting.md](troubleshooting.md).

**Verification.** Reproduce with fixture Case 5 (truncation) and Case 4 (criteria never matching).

## Hand-offs

| Request | Where it belongs |
|---|---|
| Create/enable a profile, associate an app (`APP_LIST`) | DataWedge profile and intent-output skill |
| Barcode or decoder plug-in parameters, scanner selection | DataWedge profile skill |
| `SOFT_SCAN_TRIGGER`, scanner enable/disable | DataWedge profile skill |
| ADF for `KEYSTROKE` or `IP` output | Out of scope — stop |
| BDF, or ADF for the RFID input source | Out of scope — stop, source unretrievable |
