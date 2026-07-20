---
# LAYER 2 · workflow skill · COMBINES DataWedge profile config + intent output · EXAMPLE
name: receive-scans-into-app
description: >
  End-to-end path to get DataWedge barcode scans flowing into an Android app — create/associate the
  DataWedge profile, enable Intent Output, and implement the receiver that consumes the scans. Use
  when a developer wants "scans show up in my app" working from scratch.
  SKIP when they only need the Intent Output feature details (datawedge-intent-output) or only profile
  configuration — this composes profile setup + intent output + receiver into the task.
license: "Apache-2.0"
metadata:
  owner: "DataWedge SME team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [datawedge, receive scans, intent output, profile, broadcast receiver, workflow]
version: 0.1.0
sdk-min: "DataWedge 11.0"
sdk-tested: "DataWedge 15.0"
---

# receive-scans-into-app  (workflow)

## Goal (one task)
From nothing to **scans arriving in the app**: a profile associated to the app, Intent Output enabled,
and a working receiver — end-to-end.

## When to use / when NOT
**Use** for the full "get scans into my app" task. **Not** for feature detail only (route to
`datawedge-intent-output`) or profile config in isolation.

## Composes (Layer-1 skills / features)
- DataWedge profile create/associate + SET_CONFIG (config feature)
- `reference/datawedge/datawedge-intent-output` — enabling Intent Output + the receiver

## Required inputs
Target device · Android version · DataWedge version · app package/activity to associate · deployment
mode (staged config vs in-app SET_CONFIG).

## Golden path (end-to-end)
1. Create + associate a DataWedge profile to the app (SET_CONFIG) → 2. Enable the Intent Output plugin
pointed at your action (per `datawedge-intent-output`) → 3. Register the receiver (manifest/runtime,
`RECEIVER_EXPORTED` per Android version) → 4. Confirm a scan arrives and `data_string` is read.

## References used
Profile bundle keys and intent extras/action are pulled verbatim from the composed Layer-1 `references/`.

## Verification (evals/)
`workflow-evals.json` — generated profile config **validates**, receiver **compiles + receives** a
simulated scan broadcast and reads the expected value. Ground-truth: `samples/datawedge-sample`.

## Anti-patterns
Receiver without the matching profile output (or vice-versa) · unexported receiver on Android 13+ ·
associating to the wrong package · assuming Profile0 · not handling Android 14 ordered-broadcast/latency.
