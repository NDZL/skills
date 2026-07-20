---
name: zebra-skills-router
description: >
  Use when the user is working with a Zebra device or SDK (scanning, data capture, device info,
  configuration) but hasn't landed on a specific product or skill — or wants to combine several.
  Routes to the right REFERENCE skill (learn/use one API/feature) or WORKFLOW skill (do an
  end-to-end task), and names what each workflow composes. The entry point for the zebra-skills catalog.
license: "Apache-2.0"
metadata:
  owner: "Developer Experience team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [zebra, datawedge, emdk, ai datacapture, oeminfo, enterprise browser, nfc-vas, wsc, scanning, router]
version: 0.1.0
---

# Zebra Skills — Router (advisor)

## When this applies
The request mentions Zebra hardware/SDKs but is **unscoped** ("how do I scan on a Zebra device?", "get the serial number", "build a proof-of-delivery app"), or **spans multiple features**. Use this to pick the product, then hand off to a specific skill. If the user already named a specific skill's domain, defer to that skill.

## Step 1 — pick the product
| The user wants to… | Product | Reference area |
|---|---|---|
| Scan via the OS scanning service (profiles, intents) | **DataWedge** | `reference/datawedge/` |
| On-device AI camera scanning (barcode, text/OCR, product) | **AI DataCapture (AI Suite)** | `reference/aidc/` |
| Read device identifiers/properties (serial, IMEI, MAC) | **OEMInfo** | `reference/oeminfo/` |
| Apply device config / MX features programmatically | **EMDK** | `reference/emdk/` *(planned)* |
| Scan inside a locked-down web app / kiosk | **Enterprise Browser** | `reference/enterprise-browser/` *(planned)* |
| Read a mobile wallet pass (NFC) | **NFC-VAS** | `reference/nfc-vas/` *(planned)* |
| Run/onboard an app on a Workstation Connect dock/display | **Workstation Connect (WSC)** | `reference/wsc/` *(planned)* |

## Step 2 — learn an API/feature (Layer 1) or do a task (Layer 2)?
- **Learn/use ONE API/feature** → a **reference** skill (Layer 1). Route to the product's reference skill.
- **Accomplish an end-to-end task** → a **workflow** skill (Layer 2). Route there; it *composes* the reference skills.

## Reference catalog (Layer 1)
| Skill | Product | Does |
|---|---|---|
| `reference/aidc/aidc-decode-barcodes` | AI DataCapture | decode barcodes (BarcodeDecoder / CameraX) |
| `reference/datawedge/datawedge-intent-output` | DataWedge | receive scans via Intent Output |
| `reference/oeminfo/oeminfo-device-identifiers` | OEMInfo | read serial/IMEI/MAC via ContentProvider |
| *(more per product — planned)* | | |

## Workflow catalog (Layer 2)
| Skill | Task | Composes |
|---|---|---|
| `workflows/capture-proof-of-delivery` | PoD: barcode + OCR + photo | `aidc-decode-barcodes` + `aidc-recognize-text` |
| `workflows/onboard-app-to-external-display` | app onto a WSC display | `wsc-managed-config` + `wsc-external-display` |
| `workflows/receive-scans-into-app` | scans → app, end-to-end | `datawedge-intent-output` |

## Composing when no workflow exists yet
If the task isn't a shipped workflow, name the 2–3 **reference** skills that cover it and hand off to each in order (the lightweight "task recipe" pattern). Flag it as a candidate new workflow skill.

## Routing rules
- Route to the **most specific** skill — don't answer product detail here; hand off.
- Prefer a **workflow** skill when the user describes a *task*; prefer a **reference** skill when they name an *API/feature*.
- If the product/skill doesn't exist yet, say so and point to the closest reference skill + official docs — **do not invent APIs**.
