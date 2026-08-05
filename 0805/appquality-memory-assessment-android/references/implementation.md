# Implementation

## Compatibility gate — resolve before assessing anything

Resolve every row. The authoritative platform-behaviour gate is in
[api-patterns.md](api-patterns.md); this gate covers the assessment workflow itself.

| Dimension | Supported scope | Unknown behaviour |
|---|---|---|
| Platform | Android only (ART, Gradle/AGP, `adb`) | Non-Android target → stop, out of scope |
| Android OS | 11 through 19 is the range Zebra publishes support for | If the fleet spread is unknown → **stop**, then continue with the version-independent rule subset only and say so |
| Device tier | 1 GB (WS50) through 8 GB (TC53/TC58); see the tier table below | Unknown tier → **stop**; ceiling is UNKNOWN and headroom cannot be computed |
| Enforced memory ceiling | Read from the device only | Not readable and not supplied → report scaling-only |
| Build system | Gradle with AGP; Kotlin and/or Java | Non-Gradle build → inventory and rules still apply; build-config rules do not |
| UI toolkit | XML Views, Jetpack Compose, or both | Detect which; it changes which rule families carry weight |
| Language | Kotlin, Java, or mixed | Object sizing applies to both |
| Shell access | Optional | Absent → static path only, all measured terms UNKNOWN |
| Network access | Never required | Not used at runtime |

**Reviewed:** 2026-08-04. Re-verify device and OS rows before relying on them; Zebra device
configurations and support levels move independently of this skill. Provenance for every figure is in
[sources.md](sources.md).

### Zebra RAM tiers

Quoted from Zebra specification sheets (see [sources.md](sources.md)). Several models ship multiple
configurations and vary by region, so **confirm the exact SKU** rather than trusting a model name.

| Device | RAM configurations |
|---|---|
| WS50 | 1 GB, shared with the kernel, launcher, and the Zebra software stack |
| WS501 | 3 GB |
| EC50 / EC55 | 3 GB/32 GB; 4 GB/64 GB |
| TC21 / TC26 | 4 GB RAM/64 GB Flash; 3 GB RAM/32 GB Flash |
| TC15 | 4 GB RAM/64 GB Flash |
| MC3300ax | 4 GB RAM/32 GB Flash |
| TC22 / TC27 | 6 GB RAM/64 GB UFS Flash; 8 GB RAM/128 GB UFS Flash |
| TC53 / TC58 | up to 8 GB RAM / 128 GB Flash |

**Design against the lowest tier in the fleet, not the newest device.** Physical RAM is also not the
app's budget: on a Zebra device the kernel, Android system services, the Zebra software stack
(DataWedge, MX, StageNow agent, OEMInfo, LifeGuard), the customer's EMM agent, and often a VPN client
are all resident first. Plan as one tenant among several.

## Prerequisites and setup checks

Nothing is required for the static path beyond read access to the project. Confirm before starting:

1. **The target directory.** Do not read outside it.
2. **Target device tier(s) and fleet Android version spread.** If unknown, apply the gate above.
3. **Whether a device is available and authorised** for measurement. If not, the assessment is
   scaling-only, which is a valid result that must be labelled.
4. **Largest production record counts** for the main data sets, if obtainable. This is the single
   input that converts a scaling law into a forecast. If unavailable, report crossing points in rows.

## Mode workflows

### `inventory`

Produce a map, not a judgement. Record:

- modules, and which are shipped to the device
- declared processes (`android:process`), services, and their start triggers
- top-level dependencies, and any that are heavyweight on a low-RAM target
- the data layer: database, network stack, serialisation library, paging or its absence
- long-lived collections and singletons
- bitmap and image entry points: camera capture, gallery, file, network, bundled assets
- lifecycle registrations: receivers, listeners, observers, scopes
- build configuration relevant to code footprint
- UI toolkit(s) in use, and `minSdk` / `targetSdk`

### `estimate`

1. Apply [rule-catalogue.md](rule-catalogue.md) to produce findings.
2. For every finding whose cost scales with data, derive bytes-per-record using
   [quantification.md](quantification.md). Show the arithmetic.
3. For transient peaks, derive from bitmap dimensions and payload sizes. Take the **maximum** of
   peaks that cannot co-occur; sum only those that genuinely overlap, and justify which.
4. If a device is available and authorised, follow [measurement.md](measurement.md) to obtain the
   baseline, the ceilings, and — where an iteration harness exists — the accumulation rate.
5. Compute headroom only when a ceiling was actually read. Otherwise report scaling-only.

### `plan`

Emit `memory-plan.md` at a developer-chosen path, ordered by **derived magnitude**, not by rule ID.
Each entry carries: file and line, rule ID, severity, the derived cost with its parameters, the fix
summary, and the verification that would confirm it. This artifact is what
`appquality-memory-migration-android` consumes — but that skill also accepts developer-stated steps,
so this file is an accelerator, never a dependency.

### `diagnose`

Start from the symptom and identify the breached ceiling using
[troubleshooting.md](troubleshooting.md). Do not propose fixes beyond naming the responsible rule
family.

## Manual fallbacks

| Script | What it accelerates | Text-first fallback | What stays unverified without it |
|---|---|---|---|
| `../scripts/scan_memory_rules.py` | Applies catalogue search signatures across the tree | Each rule in [rule-catalogue.md](rule-catalogue.md) lists its own signature; search by hand | Nothing in principle — only coverage completeness and speed |
| `../scripts/estimate_object_size.py` | Computes bytes-per-record from a declaration | Apply the byte table in [quantification.md](quantification.md); a worked example is included | Nothing; the arithmetic is fully manual |
| `../scripts/collect_device_memory.sh` | Batches the device reads | Run each command in [measurement.md](measurement.md) individually | Nothing; it is a convenience wrapper only |

Scripts never modify the target project. If a script cannot run, say which coverage was reduced.

## Verification and result format

Report in this order:

1. **Header** — device tier, OS, unit, ceiling and whether it was measured or is UNKNOWN.
2. **Headroom** — per process state, with the binding one named. Omit entirely if no ceiling is known.
3. **Costs now** — findings grouped by severity, ordered by derived magnitude within each group.
4. **Will cost** — the growth axes from [quantification.md](quantification.md), each with its own
   confidence.
5. **To close the model** — the specific missing terms and the exact command or business question that
   would resolve each.
6. **Not checked** — anything skipped, and why.
7. **Achieved validation level** — from the table in `SKILL.md`, with remaining checks.

Limitations to state every time: static analysis cannot measure a footprint; a derived projection is
a model with a stated tolerance; and no per-device threshold is published for any Zebra tier.

Ownership, licensing, and redistribution basis: [sources.md](sources.md).
