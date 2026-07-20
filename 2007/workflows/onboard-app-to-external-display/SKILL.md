---
# LAYER 2 · workflow skill · COMBINES WSC config + framework launch · EXAMPLE
# (the manager's own example: "onboard an app to run on a small/external screen")
name: onboard-app-to-external-display
description: >
  End-to-end onboarding of an Android app to run on a Workstation Connect (WSC) external/dock
  display — generate the WSC managed configuration and wire the app to launch its activity on the
  external display with the right bounds. Use when a developer wants an existing app to come up and
  behave correctly on the WSC screen.
  SKIP when they only need the managed-config JSON (wsc-managed-config) or only the launch-on-display
  code (wsc-external-display) — this composes both into the onboarding task.
license: "Apache-2.0"
metadata:
  owner: "Workstation Connect team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [workstation connect, wsc, external display, dock, managed config, launch bounds]
version: 0.1.0
sdk-min: "WSC (current)"
sdk-tested: "WSC (current)"
---

# onboard-app-to-external-display  (workflow)

## Goal (one task)
Get an existing app to **launch and render correctly on the WSC external display** — configured and
wired end-to-end, not just one half.

## When to use / when NOT
**Use** for the whole onboarding. **Not** for a single piece — route config-only or launch-only to the
Layer-1 skills below.

## Composes (Layer-1 skills / features)
- `reference/wsc/wsc-managed-config` — the WSC managed-configuration JSON (configurationMode, shortcuts)
- `reference/wsc/wsc-external-display` — launching the activity on the external display (`ActivityOptions.setLaunchBounds`)

## Required inputs
Target device + dock/WSC version · the app package + entry activity · desired display/bounds behavior ·
StageNow/Access-Manager grants available? · min firmware.

## Golden path (end-to-end)
1. Produce the WSC managed-config for the app (per `wsc-managed-config`) → 2. Ensure host prerequisites
(StageNow grants, dev/freeform toggles, min firmware) → 3. Launch the entry activity on the external
display with correct bounds (per `wsc-external-display`) → 4. Verify it renders and receives input on
the WSC screen.

## References used
Exact managed-config keys and `ActivityOptions`/launch APIs are pulled verbatim from the two composed
Layer-1 skills' `references/`.

## Verification (evals/)
`workflow-evals.json` — generated config **validates + loads**, and the launch code **compiles** and
(device/manual gate) brings the activity up on the external display. Ground-truth: `samples/wsc-sample`.
Note: full display behavior is a **device/manual** gate; config + compile are automatable in CI.

## Anti-patterns
Shipping config without the launch wiring (or vice-versa) · assuming grants are present · hardcoding
bounds instead of querying the external display · ignoring min-firmware prerequisites.
