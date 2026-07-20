---
# LAYER 2 · workflow skill · COMBINES multiple AIDC features · EXAMPLE (flagship)
name: capture-proof-of-delivery
description: >
  End-to-end proof-of-delivery capture on Android with AI DataCapture — scan the package barcode,
  OCR the shipping label/address, and capture a confirmation photo, producing one PoD record.
  Use when a developer wants the whole PoD flow, not a single capture primitive.
  SKIP when they only need one piece — decode a barcode (aidc-decode-barcodes), OCR text
  (aidc-recognize-text), or plain camera capture. This composes those into a task.
license: "Apache-2.0"
metadata:
  owner: "AIDC / AI Suite team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [proof of delivery, pod, aidc, barcode, ocr, photo, workflow]
version: 0.1.0
sdk-min: "AI DataCapture 4.0"
sdk-tested: "AI DataCapture 4.0"
---

# capture-proof-of-delivery  (workflow)

## Goal (one task)
Produce a single **PoD record** = { scanned tracking barcode, OCR'd address/label fields, confirmation
photo } from one guided capture flow.

## When to use / when NOT
**Use** for the full PoD task. **Not** for a single capability — route those to the Layer-1 skills below.

## Composes (Layer-1 skills / features)
- `reference/aidc/aidc-decode-barcodes` — the tracking barcode
- `reference/aidc/aidc-recognize-text` — the address/label OCR
- Camera photo capture (AIDC/CameraX still capture)

## Required inputs
Target device · Android version · AIDC SDK + model versions · which fields to OCR · storage/upload target.

## Golden path (end-to-end)
1. Init AIDC + CameraX once (shared setup) → 2. Decode the tracking barcode (per `aidc-decode-barcodes`)
→ 3. OCR the label fields (per `aidc-recognize-text`) → 4. Capture the confirmation photo →
5. Assemble the PoD record and hand off (persist/upload). One screen, one lifecycle, one teardown.

## References used
Pulls the exact primitives from the composed Layer-1 skills' `references/` (barcode analyzer, text
recognizer, camera capture) — nothing retyped here.

## Verification (evals/)
`workflow-evals.json` — generated flow **compiles + runs against the AIDC PoD demo app**, feeding a
fixture (barcode image + label image) and asserting the PoD record contains the expected tracking
number + parsed address fields + a non-empty photo. Ground-truth: `samples/aidc-sample` (PoD demo).

## Anti-patterns
Re-initializing AIDC per step · blocking the UI thread during OCR · leaking the camera between steps ·
duplicating barcode/OCR setup instead of composing the Layer-1 recipes.
