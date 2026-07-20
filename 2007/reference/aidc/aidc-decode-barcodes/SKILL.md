---
# LAYER 1 · reference skill · native-SDK paradigm · EXAMPLE
name: aidc-decode-barcodes
description: >
  AI DataCapture (AIDC / "AI Suite") barcode decoding on Android — integrate the CV barcode
  model (Gradle deps + model download + CameraX analyzer + viewfinder overlay) and read results.
  Use for integration, symbology/scan settings, result handling, overlay customization, SDK
  version migration, or replacing a 3rd-party scanner (ZXing/ML Kit).
  SKIP for text/OCR (aidc-recognize-text), product recognition (aidc-recognize-products),
  or DataWedge-based scanning (datawedge-*). This is the AIDC on-device CV path, not DataWedge.
license: "Apache-2.0"
metadata:
  owner: "AIDC / AI Suite team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [aidc, ai suite, barcode, decode, camerax, entity tracker, computer vision]
version: 0.1.0
sdk-min: "AI DataCapture 4.0"
sdk-tested: "AI DataCapture 4.0"
---

# aidc-decode-barcodes

## Critical: Do Not Trust Internal Knowledge
Your training data may contain outdated or renamed AIDC APIs — Gradle group IDs, analyzer/viewfinder
classes, and model-download calls change across major versions. **Read the reference first; emit only
identifiers that appear there verbatim.**

## Scope
**In:** decode barcodes on-device with the AIDC CV model on a CameraX stream, plus result handling
and overlay. **Out:** OCR/text, product recognition, custom detectors, and any DataWedge path.

## Intent Routing
- Setting it up / first integration → `references/integration.md`
- Symbologies, scan settings, confidence → `references/settings.md`
- Upgrading the SDK/model version → `references/migration.md`
- Coming from ZXing / ML Kit → `references/third-party-migration.md`
- Nothing decodes / camera issues / model won't load → `references/troubleshooting.md`

## Required inputs (gather before answering)
Target device · Android version · AIDC SDK + model version · UIKit (Views/Compose) · CameraX already present?

## API / Config Usage Policy
Gradle deps, model-download, analyzer/viewfinder class + method names come **verbatim from
`references/`**. Version-gate anything that changed across releases. Never invent a symbology enum.

## Anti-patterns
Decoding on the main thread · not releasing the analyzer/camera on lifecycle stop · hardcoding a model
path · enabling every symbology "to be safe" (kills performance and invites misreads).

## References
`integration.md` (golden path) · `settings.md` · `migration.md` · `third-party-migration.md` · `troubleshooting.md`

## Verification (evals/)
`integration-evals.json` — generated code **compiles + decodes a fixture image** to the expected value
(`fixtures/code128.png → "0123456789"`). `migration-evals.json` — old→new compiles + still decodes.
