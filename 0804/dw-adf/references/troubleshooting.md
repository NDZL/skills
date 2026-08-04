# Troubleshooting

Symptom-first. Scope for everything below: DataWedge 15.0 reviewed, `INTENT` output plug-in.

## First discriminator, always

Read both extras from the delivered scan intent in `ScanDataReceiver.onReceive`:

| Comparison | Meaning |
|---|---|
| `data_string` == `decode_data` | The rule **never fired** — go to S1 or S2 |
| `data_string` != `decode_data` but wrong | The rule **fired**, the action list is wrong — go to S3 |
| No intent at all | Not an ADF problem — the intent route is broken, go to S6 |

`com.symbol.datawedge.data_string` is ADF-processed; `com.symbol.datawedge.decode_data` is raw and
unmodified.

## S1 — `SET_CONFIG` reported success but no rule exists

- **Evidence to collect.** The `RESULT` value, the `RESULT_INFO` bundle contents, and a `GET_CONFIG` +
  `PROCESS_PLUGIN_NAME` readback.
- **Likely causes.** (a) A plain `GET_CONFIG` was used to check, which cannot read process plug-ins,
  so the rule exists but was not visible. (b) `OUTPUT_PLUGIN_NAME` was omitted, so the ADF config was
  never bound to intent output. (c) The command was sent while DataWedge was busy and was ignored —
  DataWedge does not queue API commands.
- **Corrective action.** Re-read with `PROCESS_PLUGIN_NAME` = [{`PLUGIN_NAME` = `ADF`,
  `OUTPUT_PLUGIN_NAME` = `INTENT`}]. Confirm the binding key is present. Re-send through
  `reconcileAdfState()` so the timeout path can retry.
- **Verification.** Fixture Case 7.
- **Escalate / stop.** Attempts exhausted with no result → report a blocking error; do not loop.

## S2 — Rule exists but never fires

- **Evidence.** The readback's `ADF_RULE` criteria: `string`, `string_pos`, `string_len`,
  `alldevices`, `DEVICES`, `DECODERS`, `enabled`.
- **Likely causes.** (a) `enabled` = `false`. (b) Criteria too narrow — `string_len` set to a length
  the real data never has, or `string_pos` off by one. (c) `alldecoders` = `false` with a `decoder`
  token that does not resolve on this DataWedge version. (d) `adf_enabled` = `false` in `PARAM_LIST`.
- **Corrective action.** Widen to `alldecoders` = `true` and empty criteria, confirm the rule fires at
  all, then narrow one field at a time. For symbology scoping, remember only `Australian Postal` is
  an attested token.
- **Verification.** Fixture Case 4, including its two non-matching control inputs.
- **Escalate / stop.** A symbology token that never resolves after readback → `unknown`; stop and
  verify the token against the DataWedge UI on the device.

## S2b — Readback confirms the rule, but data is still unchanged

**This is the most misleading failure in ADF: every layer reports success.**

- **Evidence.** The `PARAM_LIST` order from the readback — specifically, which rule name comes
  **first**, and whether any earlier rule has `string_len` = `0` with empty `string`.
- **Likely cause.** Rule shadowing. Evaluation is ordered and the first match wins. An unconditional
  rule ahead of yours matches everything, so yours never runs. The auto-created `Rule0` (no criteria,
  single `SEND_REMAINING`) is exactly that rule, and `RESET_CONFIG` = `false` appends behind it.
- **Corrective action.** Re-send with `RESET_CONFIG` = `true` and `PARAM_LIST` carrying every rule in
  order, most specific first, catch-all passthrough **last**. Re-supply the passthrough yourself —
  clearing `Rule0` leaves non-matching input with no rule. Then confirm order by readback.
- **Verification.** Readback shows your rule first. Confirmed working shape on DataWedge 15.0.73:
  `[Ean13ThirdFirstThenReversed, PassThroughEverythingElse]`.
- **See.** AP-11 in [anti-patterns.md](anti-patterns.md).

## S2c — `SET_CONFIG` returns SUCCESS but no rule was ever created

- **Evidence.** A readback showing only `Rule0`, plus `adf_enabled` reading `false` even though you
  set it.
- **Likely cause.** The **documented bundle shape**. `PARAM_LIST` sent as a Bundle wrapping an
  `ADF_RULE` bundle, with `adf_enabled` inside `PARAM_LIST`, is accepted and silently discarded on
  DataWedge 15.0.73.
- **Corrective action.** Use the device-verified shape: `adf_enabled` at plug-in level beside
  `PARAM_LIST`, and `PARAM_LIST` as an ordered `ParcelableArrayList` of rule bundles with no
  `ADF_RULE` wrapper. See [api-patterns.md](api-patterns.md).
- **Verification.** Readback lists your rule name in `PARAM_LIST`.

## S3 — Data arrives truncated

- **Evidence.** The ordered `ACTIONS` list from the readback and the raw `decode_data` length.
- **Likely cause.** The action list has no terminal send action, so everything after the cursor is
  discarded. See AP-7 in [anti-patterns.md](anti-patterns.md).
- **Corrective action.** Append `SEND_REMAINING`. `AdfRule.emitsRemainder` warns about this at
  construction.
- **Verification.** Fixture Case 5 reproduces the truncation deliberately.

## S4 — Rules disappeared after a config push

- **Evidence.** The `RESET_CONFIG` value actually sent, and which component sent the last push.
- **Likely causes.** (a) `RESET_CONFIG` = `true` (the default) cleared the existing configuration.
  (b) `CONFIG_MODE` = `OVERWRITE`. (c) A second owner in the same app pushed a competing config —
  AP-2.
- **Corrective action.** Re-send the surviving rules with `RESET_CONFIG` = `false` and `CONFIG_MODE` =
  `UPDATE`. Collapse to one owner per profile.
- **Verification.** Readback lists every expected rule name.
- **Escalate / stop.** If rules were created by another team or by MX, stop and coordinate before
  re-pushing — you may not know their full content.

## S5 — App hangs in "applying", no error

- **Evidence.** Whether a result was ever received; whether the receiver was registered before the
  result was broadcast; the in-flight `COMMAND_IDENTIFIER`.
- **Likely causes.** (a) One-sided retry — AP-4. (b) Receiver registered after the result was
  broadcast, so the result was never observed. (c) `COMMAND_IDENTIFIER` mismatch caused a valid result
  to be dropped. (d) `SEND_RESULT` omitted, so DataWedge sent nothing.
- **Corrective action.** Confirm `reconcileAdfState()` is reachable from all four edges
  (`onHostStart`, result, readback, timeout). Confirm `SEND_RESULT` = `LAST_RESULT` is set. Log every
  dropped token.
- **Verification.** Force both completion orders — see the completion-order table in
  [implementation.md](implementation.md).

## S6 — No scan intent arrives at all

- **Evidence.** Whether scans arrived **before** ADF was added; `intent_output_enabled`,
  `intent_action`, `intent_delivery`; the host's registered filter.
- **Likely causes.** Intent output disabled; `intent_action` mismatch between profile and receiver;
  wrong `intent_delivery` for the host component type; profile not associated with the app; on Android
  13+ a dynamic receiver registered without `RECEIVER_EXPORTED`.
- **Corrective action.** This is not an ADF fault. Restore the baseline first — see AP-10 — then hand
  profile and intent-output work to the DataWedge profile skill.
- **Escalate / stop.** Profile association problems (`APP_ALREADY_ASSOCIATED`, `OPERATION_NOT_ALLOWED`
  on Profile0 or a protected profile) are out of this skill's capability. Hand off.

## Result-code quick reference

| Code | Action |
|---|---|
| `RESULT_ACTION_RESULT_CODE_EMPTY_RULE_NAME` | Set a non-blank `ADF_RULE.name`. **Terminal** — do not retry |
| `PLUGIN_BUNDLE_INVALID` | Plug-in bundle incomplete — check `adf_enabled` is present at plug-in level and `PARAM_LIST` holds at least one rule bundle |
| `PARAMETER_INVALID` | A key or value is malformed. Booleans must be the **strings** `"true"` / `"false"` |
| `PLUGIN_NOT_SUPPORTED` | Wrong `PLUGIN_NAME` / `OUTPUT_PLUGIN_NAME` combination. **Terminal** |
| `PROFILE_NOT_FOUND` | Profile missing — use `CONFIG_MODE` = `CREATE_IF_NOT_EXIST`, or hand off profile creation |
| `PROFILE_NAME_EMPTY` | Set `PROFILE_NAME` |
| `APP_ALREADY_ASSOCIATED` | Association belongs to the profile skill. **Terminal** here |
| `OPERATION_NOT_ALLOWED` | Protected profile or Profile0 association. **Terminal** |
| `BUNDLE_EMPTY` | The `SET_CONFIG` extra carried no bundle |
| `UNLICENSED_FEATURE` | License-gated. **Terminal** — stop, do not retry |

## What cannot be diagnosed from this skill

- Anything about BDF or the RFID input source: the supporting article was unretrievable, so no
  behavior is claimed. Stop.
- Rule ordering between multiple rules: `unknown`. Observe by readback and report what you see rather
  than asserting a documented order.
- Behavior on DataWedge versions other than 15.0, on any specific Android version, or on any specific
  device model: `unknown — verify on target`.
