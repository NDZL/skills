# ADF rule fixtures: input, rule, expected output

Repeatable cases for the `INTENT` output plug-in. Use them to check a rule you built and to confirm
a transformation on a device.

- **Owner:** Nicola DZL for testing purposes
- **Reviewed:** 2026-08-04 against DataWedge 15.0 action semantics
- **Redistribution:** original fixtures authored for the `dw-adf` skill. The inputs are synthetic
  strings, not customer data, and carry no third-party rights.
- **Achieved validation level:** `inspection`. Expected outputs are **derived from the documented
  action semantics**, not observed on a device. Treat a mismatch on hardware as new evidence about
  the device, and correct the fixture.

Where to observe the actual value: `com.symbol.datawedge.data_string` in the delivered intent, read
by `ScanDataReceiver.onReceive`. `com.symbol.datawedge.decode_data` carries the raw, unprocessed
data — comparing the two is the fastest way to tell whether a rule fired at all.

## Case 1 — trim leading zeros

Machine-readable bundle tree: [trim-leading-zeros.json](trim-leading-zeros.json)

| Field | Value |
|---|---|
| Rule name | `TrimLeadingZeros` |
| Criteria | `alldevices` = `false`; `DEVICES` = [`BARCODE`, `alldecoders` = `true`] |
| Actions | 1. `TRIM_LEFT_ZEROS` 2. `SEND_REMAINING` |
| Input | `000000123456` |
| **Expected output** | `123456` |
| Input (no leading zeros) | `123456` |
| **Expected output** | `123456` — unchanged, rule is a no-op on this input |

## Case 2 — strip hyphens with REPLACE_STRING

Machine-readable bundle tree: [replace-string.json](replace-string.json)

| Field | Value |
|---|---|
| Rule name | `StripHyphens` |
| Actions | 1. `REPLACE_STRING` (`action_param_1` = `-`, `action_param_2` = empty) 2. `SEND_REMAINING` |
| Input | `ABC-123-XYZ` |
| **Expected output** | `ABC123XYZ` |

## Case 3 — add a fixed prefix

| Field | Value |
|---|---|
| Rule name | `PrefixItem` |
| Actions | 1. `SEND_STRING` (`action_param_1` = `ITEM:`) 2. `SEND_REMAINING` |
| Input | `123456` |
| **Expected output** | `ITEM:123456` |

`SEND_STRING` emits literal text and does not consume input, so the cursor is still at the start when
`SEND_REMAINING` runs.

## Case 4 — criteria-scoped rule with a non-matching control input

| Field | Value |
|---|---|
| Rule name | `Strip99Prefix` |
| Criteria | `string` = `99`, `string_pos` = `0`, `string_len` = `7` |
| Actions | 1. `SKIP_AHEAD` (`action_param_1` = `2`) 2. `SEND_REMAINING` |
| Input (matching) | `9912345` |
| **Expected output** | `12345` |
| Input (non-matching prefix) | `8812345` |
| **Expected output** | `8812345` — criteria not met, rule does not fire |
| Input (matching prefix, wrong length) | `991234567` |
| **Expected output** | `991234567` — `string_len` = `7` not met, rule does not fire |

The two control inputs are the point of this case: a rule that transforms *everything* is not
criteria-scoped, it is just an unconditional rule that happens to match.

## Case 5 — negative case: missing terminal send action

| Field | Value |
|---|---|
| Rule name | `TruncatesSilently` |
| Actions | 1. `SEND_NEXT` (`action_param_1` = `4`) — **no terminal send** |
| Input | `123456789` |
| **Expected output** | `1234` — everything after the cursor is discarded |

This is the expected behavior, not a DataWedge defect. It is the most common cause of "ADF ate my
data". A rule must end with an action that emits the remainder unless truncation is intended. See
[../../../references/anti-patterns.md](../../../references/anti-patterns.md).

## Case 6 — decoder-scoped rule using the only attested symbology token

| Field | Value |
|---|---|
| Rule name | `AusPostalOnly` |
| Criteria | `alldevices` = `false`; `DEVICES` = [`BARCODE`, `alldecoders` = `false`]; `DECODERS` = [`BARCODE`, `Australian Postal`] |
| Actions | 1. `SEND_REMAINING` |
| Input from an Australian Postal label | passthrough |
| **Expected output** | unchanged payload |
| Input from any other symbology | rule does not fire |

`Australian Postal` is the **only** `decoder` string attested verbatim in the reviewed source. For
any other symbology, send the rule, then confirm the token survived a `GET_CONFIG` +
`PROCESS_PLUGIN_NAME` readback before trusting it.

## Case 8 — DEVICE-VERIFIED: positional reorder with reversal (EAN-13)

The only case in this file confirmed on hardware: Zebra TC701, Android 15, DataWedge 15.0.73,
2026-08-04.

| Field | Value |
|---|---|
| Rule name | `Ean13ThirdFirstThenReversed` |
| Criteria | `alldevices` = `true`, no `DEVICES` list, `string_len` = `13` |
| Output positions | `[3, 1, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4]` |
| Actions | 35 total: per position `SKIP_TO_START`, `SKIP_AHEAD n-1` (omitted for position 1), `SEND_NEXT 1`, each with `action_index` |
| Terminal send | **None** — truncation is intended |
| Input | `8055002861991` (LABEL-TYPE-EAN13) |
| **Confirmed output** | **`581991682005`** (12 chars) |
| Rule order required | `[Ean13ThirdFirstThenReversed, PassThroughEverythingElse]` with `RESET_CONFIG` = `true` |

Observed logcat line:

```text
ADF_VALIDATION: SCAN|n=1|len=12|payload=581991682005|label=LABEL-TYPE-EAN13
```

**Negative control from the same session.** With `RESET_CONFIG` = `false`, the readback was
`[Rule0, Ean13ThirdFirstThenReversed]` and the identical scan arrived as `8055002861991` — completely
unmodified, with `SET_CONFIG` reporting `SUCCESS` and the readback confirming the rule existed. Use
this as the regression case for AP-11: rule present and verified is **not** rule effective.

This case also demonstrates that ADF has no reverse action: reversal is unrolled one character at a
time, so the action count scales with barcode length and the length must be fixed.

## Case 7 — readback verification fixture

| Field | Value |
|---|---|
| Action | Send `GET_CONFIG` with `PROCESS_PLUGIN_NAME` = [{`PLUGIN_NAME` = `ADF`, `OUTPUT_PLUGIN_NAME` = `INTENT`}] |
| Expected result extra | `com.symbol.datawedge.api.RESULT_GET_CONFIG` |
| Expected contents | `PROFILE_NAME`, `PROFILE_ENABLED`, and a `PLUGIN_CONFIG` entry whose `PARAM_LIST.ADF_RULE.name` equals the rule sent |
| **Pass condition** | `AdfIntentOutputConfigurator.readbackMatchesRequestedRule` returns `true` |
| **Fail condition** | Rule absent → the configurator clears its token and re-applies; it must not report verified |

A plain `GET_CONFIG` without `PROCESS_PLUGIN_NAME` cannot read process plug-ins and will not return
ADF content. An empty ADF section in that case means "wrong query", not "no rule".
