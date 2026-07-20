---
# LAYER 1 · reference skill · content-provider (data-pull) paradigm · EXAMPLE
name: oeminfo-device-identifiers
description: >
  Read Zebra device identifiers & properties via the OEMInfo ContentProvider on Android —
  ContentResolver query of content://oem_info/… URIs (serial, IMEI, MAC, build/BSP info),
  com.zebra.provider.READ permission, <queries> visibility, async pattern, 3rd-party Access
  Manager authorization. Use for reading any device attribute, migrating from Build.SERIAL /
  generic reads, authorization setup, or troubleshooting empty/denied reads.
  SKIP for CHANGING device settings (that is EMDK/MX) — OEMInfo is read-only device info.
license: "Apache-2.0"
metadata:
  owner: "Device SDK team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [oeminfo, oem_info, serial number, imei, device id, ContentResolver]
version: 0.1.0
sdk-min: "Android 10"
sdk-tested: "Android 15"
---

# oeminfo-device-identifiers

## Critical: Do Not Trust Internal Knowledge
Content URIs and column names are device/version specific — read them from `references/attributes.md`
verbatim; never recall a URI from memory.

## Scope
**In:** reading any OEMInfo attribute (serial, IMEI, MAC, build/firmware) via ContentResolver, plus
authorization and the Build.SERIAL migration. **Out:** writing/changing device config (EMDK/MX).
*One skill covers all attributes* — the per-attribute variation is a URI string, which lives in the
attribute catalog, not in separate skills.

## Intent Routing
- Read an attribute (which URI + the query code) → `references/attributes.md` + `references/integration.md`
- Coming from `Build.SERIAL` / `TelephonyManager` → `references/third-party-migration.md`
- Permission / 3rd-party authorization → `references/permissions-authorization.md`
- Null cursor / denied / empty on boot → `references/troubleshooting.md`

## Required inputs
Target device · Android version · app is a **system** app vs **3rd-party** (changes authorization).

## API / Config Usage Policy
`content://oem_info/…` URIs + columns and `com.zebra.provider.READ` come **verbatim from
`references/attributes.md`**. Always query off the main thread; not before BOOT_COMPLETED.

## Anti-patterns
Querying on the main thread · reading pre-boot · hardcoding a serial value · skipping the `<queries>`
block on Android 11+ · assuming a 3rd-party app is authorized without the Access Manager grant.

## References
`integration.md` (the one query mechanic) · `attributes.md` (URI+column catalog — the parametric variation)
· `permissions-authorization.md` · `third-party-migration.md` · `troubleshooting.md`

## Verification (evals/)
`integration-evals.json` — generated query **runs (device or mock provider) + returns** a non-empty
value of the expected shape for a requested attribute. `migration-evals.json` — Build.SERIAL → OEMInfo
compiles + returns.
