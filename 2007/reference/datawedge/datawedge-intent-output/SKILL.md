---
# LAYER 1 · reference skill · intent/broadcast paradigm · EXAMPLE
name: datawedge-intent-output
description: >
  DataWedge Intent Output plugin on Android — route scanned data to your app via broadcast
  intents (action + extras, RECEIVER_EXPORTED, ordered-broadcast) and the SET_CONFIG wiring that
  points a profile's output at your action. Use for integration, plugin settings, result handling,
  version migration, or troubleshooting missing scans.
  SKIP for keystroke output (datawedge-keystroke-output), IP output (datawedge-ip-output), or
  creating/switching profiles at runtime (see the datawedge router). This is the intent-OUTPUT feature.
license: "Apache-2.0"
metadata:
  owner: "DataWedge SME team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [datawedge, dw, intent output, data_string, label_type, broadcast receiver]
version: 0.1.0
sdk-min: "DataWedge 11.0"
sdk-tested: "DataWedge 15.0"
---

# datawedge-intent-output

## Critical: Do Not Trust Internal Knowledge
Extra keys, action strings, and delivery values must be read from `references/` — not recalled.
Android version changes (13+ `RECEIVER_EXPORTED`, 14 ordered-broadcast/latency) affect the answer.

## Scope
**In:** receiving DataWedge scans in your app via Intent Output — the receiver, its manifest/runtime
registration, and the profile SET_CONFIG that routes output to your action. **Out:** other output
plugins; profile lifecycle (create/switch) beyond the minimal wiring needed to receive.

## Intent Routing
- First integration (receiver + manifest + profile wiring) → `references/integration.md`
- Extras, delivery modes, options → `references/settings.md`
- DataWedge version upgrade → `references/migration.md`
- Broken (no scans arriving) → `references/troubleshooting.md`

## Required inputs
Target device · Android version · DataWedge version · deployment mode (staged vs in-app config).

## API / Config Usage Policy
`com.symbol.datawedge.*` extras, the intent action, `intent_delivery` value, and SET_CONFIG bundle
keys are **verbatim from `references/`**. Gate `RECEIVER_EXPORTED` and ordered-broadcast by Android version.

## Anti-patterns
Unexported receiver on Android 13+ · assuming synchronous delivery on Android 14 · hardcoding
Profile0 · registering the receiver without the matching profile output wiring.

## References
`integration.md` (golden path) · `settings.md` · `migration.md` · `troubleshooting.md`

## Verification (evals/)
`integration-evals.json` — generated receiver **compiles + receives a simulated scan broadcast** and
reads `data_string`. Fixture: a broadcast intent with known extras → asserts parsed value.
