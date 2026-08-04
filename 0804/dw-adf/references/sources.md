# Sources, scope, ownership, and redistribution

**Reviewed:** 2026-08-04

## Official sources

| Source ID | Official URL | Product/version scope | Reviewed | Derived guidance |
|---|---|---|---|---|
| `dw15-setconfig-adf` | `https://techdocs.zebra.com/datawedge/15-0/guide/api/setconfig/` | DataWedge 15.0 | 2026-08-04 | **Nominal source of truth, but SUPERSEDED BY DEVICE EVIDENCE on the bundle shape** — see "Device evidence" below; the documented shape returns `SUCCESS` and creates no rule. ADF processing parameters: `adf_enabled`, `ADF_RULE` keys, `ACTIONS` (`type`, `action_param_1`–`action_param_3`), `DEVICES`, `DECODERS`, `LABEL_IDS`; the full action table and parameters; `PLUGIN_NAME` `ADF`; `OUTPUT_PLUGIN_NAME` for ADF/BDF; `CONFIG_MODE`; `RESET_CONFIG` merge-vs-clear; `SEND_RESULT`; `COMMAND_IDENTIFIER`; result codes; the automatic `Rule0` / `SEND_REMAINING` note; intent output parameters |
| `dw15-getconfig` | `https://techdocs.zebra.com/datawedge/15-0/guide/api/getconfig/` | DataWedge 15.0 | 2026-08-04 | `PROCESS_PLUGIN_NAME` is required to read ADF/BDF; the verbatim readback sample reproduced in `api-patterns.md`; the statement that *"ADF_RULE bundles contain Action, Device, Decoder and Label_ID sub-bundles"* — correct that those are children of the rule, but **misleading about the wrapper**: device readback shows no `ADF_RULE` key, only rule bundles inside an ordered `PARAM_LIST` list; `com.symbol.datawedge.api.RESULT_GET_CONFIG` |
| `dw15-resultinfo` | `https://techdocs.zebra.com/datawedge/15-0/guide/api/resultinfo/` | DataWedge 15.0; `SEND_RESULT` enum noted for 7.1+ | 2026-08-04 | `com.symbol.datawedge.api.RESULT_ACTION`; result keys `RESULT`, `COMMAND`, `COMMAND_IDENTIFIER`, `RESULT_INFO`, `RESULT_LIST`; `SEND_RESULT` values `NONE` / `LAST_RESULT` / `COMPLETE_RESULT` for DataWedge 7.1+ and `TRUE` / `FALSE` below 7.1; the `RESULT_INFO` read pattern |
| `dw15-api-overview` | `https://techdocs.zebra.com/datawedge/15-0/guide/api/overview/` | DataWedge 15.0 | 2026-08-04 | `com.symbol.datawedge.api.ACTION`; result receiver must filter `RESULT_ACTION` + `android.intent.category.DEFAULT`; DataWedge does **not** queue API commands and may ignore them while busy, with delay code recommended before critical commands |
| `dw15-intent-output` | `https://techdocs.zebra.com/datawedge/15-0/guide/output/intent/` | DataWedge 15.0 | 2026-08-04 | Scan-data extras `com.symbol.datawedge.data_string`, `com.symbol.datawedge.label_type`, and `com.symbol.datawedge.decode_data` (raw, unmodified); intent delivery modes |
| `dw14-adf-process` | `https://techdocs.zebra.com/datawedge/14-0/guide/process/adf/` | DataWedge 14.0 — **concept and limits only** | 2026-08-04 | Rule/criteria/action model; actions execute top to bottom; ADF applies to Keystroke, IP, and Intent output; ADF cannot select which scanner was used; control characters 1–31 (except ENTER/TAB) cannot be sent via `SEND_CHAR`; maximum `DELAY` 120 seconds; ~50 ms pause recommended after ENTER/LINE FEED/TAB |
| `zebra-support-bdf-adf-rfid` | `https://support.zebra.com/article/Configure-BDF-and-ADF-Settings-for-RFID-Input-Plugin-in-DataWedge` | RFID input plug-in | 2026-08-04 | **Cited but unretrievable.** The URL returned only the support-portal shell during authoring, so **no facts were derived from it**. RFID-source ADF and BDF are therefore recorded as `unknown` stop conditions, not as documented behavior |

Source preference order followed: version-specific Zebra TechDocs first; the 14.0 process guide is
used only for the concept model and stated limits, never for API key names; no external or
community sources were used.

## Version scope

| Dimension | Declared |
|---|---|
| Reviewed product version | DataWedge 15.0 |
| Corroborating version | DataWedge 14.0 (concept/limits only) |
| Cross-version note | `SEND_RESULT` enum documented for DataWedge 7.1+ |
| Minimum version for ADF via `SET_CONFIG` | `unknown` — not stated in any reviewed source |
| Platform | Android host app using the DataWedge Intent API |
| Android OS versions | `unknown — verify on target` |
| Zebra device models | `unknown — verify on target` |
| Output plug-in | `INTENT` only |
| Input source | `BARCODE` in scope; `MSR`, `RFID`, `SERIAL`, `VOICE` documented but unverified |

## Active product-specific guides

**None.** No maintained DataWedge product-specific implementation guide exists in this authoring
context. Nothing in scope ratifies `dw` as the approved offering prefix or `adf` as the approved
capability token — the skill name was supplied by the requesting developer and is recorded as a
**missing product decision** to be resolved before release review. The cross-product Zebra Skill
Authoring Standard governs in the meantime. No historical plan, deleted file, or unverified
reference was treated as authority.

## Ownership

- Skill owner: `Nicola DZL for testing purposes`
- Product owner: `Nicola DZL for testing purposes`
- Documentation owner: `Nicola DZL for testing purposes`

Recorded verbatim as supplied. The owner value states it is for testing purposes, so treat accountable
product, documentation, and release-review ownership as **not yet assigned** for release purposes.

## Bundled material

| Item | Version / derivation | Owner | License / redistribution |
|---|---|---|---|
| `assets/code-patterns/adf-intent-output/AdfIntentOutputConfigurator.kt` | Original, authored 2026-08-04 for this skill; key names derived from `dw15-setconfig-adf`, `dw15-getconfig`, `dw15-resultinfo` | Nicola DZL for testing purposes | Original skill content; redistributable with the skill. Not derived from Zebra sample code |
| `assets/code-patterns/adf-intent-output/DwResultReceiver.kt` | Original, authored 2026-08-04; extras from `dw15-intent-output` and `dw15-resultinfo` | Nicola DZL for testing purposes | Original skill content |
| `assets/code-patterns/adf-intent-output/manifest-and-wiring.md` | Original, authored 2026-08-04 | Nicola DZL for testing purposes | Original skill content |
| `assets/test-fixtures/adf-rule-cases/*` | Original synthetic inputs; expected outputs derived from documented action semantics in `dw15-setconfig-adf` and `dw14-adf-process` | Nicola DZL for testing purposes | Original skill content; synthetic data, no third-party rights |

The one verbatim third-party excerpt in this skill is the Java `GET_CONFIG` sample quoted in
`api-patterns.md`, reproduced as a short documentation quotation from `dw15-getconfig` and attributed
to Zebra. No Zebra binaries, proprietary SDKs, restricted models, credentials, or signing material are
bundled.

## Device evidence

| Item | Value |
|---|---|
| Device | Zebra **TC701** (`TC701L`, `TCX01LD`) |
| OS | Android **15**, SDK **35** |
| DataWedge | **15.0.73** |
| Date | 2026-08-04 |
| Toolchain | Gradle 8.13, AGP 8.5.2, Kotlin 1.9.24, JDK 17 (Android Studio JBR) |
| Build | `BUILD SUCCESSFUL`, debug APK installed via `adb` |
| End-to-end result | EAN-13 `8055002861991` (13 chars) delivered as `581991682005` (12 chars), matching the predicted position map `[3,1,13,12,11,10,9,8,7,6,5,4]` |
| Rule stored | 35 actions, sequential `action_index` 0–34, `string_len=13`, `adf_enabled=true` |
| Rule order confirmed | `[Ean13ThirdFirstThenReversed, PassThroughEverythingElse]` |

### Documentation discrepancies found on device

The published `SET_CONFIG` reference (`dw15-setconfig-adf`) is **wrong or incomplete** on seven
points, recorded in [api-patterns.md](api-patterns.md) under "Device-verified corrections":
`PARAM_LIST` structure, `adf_enabled` placement, the undocumented `action_index`, the `device_id`
vocabulary, multi-rule support and ordering semantics, `RESULT_INFO` on success, and the decoder
token vocabulary. Building the documented shape returns `RESULT=SUCCESS` while creating no rule.

Where documentation and device disagree, **this skill follows the device** and labels the fact as
device-verified with the exact version. Anyone on a different DataWedge version must re-verify.

## Achieved validation level

**`device`** for: the ADF bundle write shape, rule ordering and shadowing behaviour, success
detection, the readback shape and parsing, and the end-to-end transformation — all exercised on the
TC701 / DataWedge 15.0.73 configuration above.

**`inspection`** for everything else, including: the complete ADF action table beyond the cursor and
send actions actually used, `KEYSTROKE`/`IP` output behaviour (out of scope), `MSR`/`RFID`/`SERIAL`/
`VOICE` input sources, UDI `LABEL_IDS`, `intent_component_info`, `intent_use_content_provider`, and
every result code other than the success path.

**Not performed:** automated test suite, any other DataWedge version, any other Android version or
device model, and production review (security, licensing, deployment, operations). Device evidence is
not production approval.
