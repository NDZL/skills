---
name: appquality-memory-assessment-android
description: "Assess and quantify an Android application runtime memory (RAM) footprint for Zebra enterprise devices from 1 GB wearables to 8 GB handhelds, producing a prioritised memory-plan.md report without editing any code. Inventories the codebase, derives per-record byte costs and scaling laws by static analysis, optionally measures RssAnon, cgroup limits and Java heap on a connected device, then reports headroom and growth forecasts. Use when an app is killed with no stack trace (Android 17 MemoryLimiter:AnonSwap), throws OutOfMemoryError, degrades across a shift, must fit a low-RAM device tier, or needs a memory baseline, budget, headroom figure or growth forecast. Do not use to apply fixes or edit code (hand off to appquality-memory-migration-android), for APK or download size, for DataWedge or scanner configuration, for responsive multi-form-factor layout work, or for CPU, battery or jank analysis."
---

# appquality-memory-assessment-android

## Release metadata

- Version: `1.0.0-beta.1`
- Stage: `Beta`

> **Beta:** This skill is available for early evaluation. Its workflows and behavioral contract
> may change before stable release. Validate generated output before production use. This status
> applies to the skill package—not to referenced Zebra products, SDKs, APIs, or models.

This skill is **read-only**. It never edits, formats, or refactors a file in the target project. Its
only output is a report, plus a `memory-plan.md` artifact written where the developer asks for one.
Applying changes is a separate capability with a different risk posture: hand off to
`appquality-memory-migration-android`.

## Supported task modes

| Mode | Outcome |
|---|---|
| `explain` | Explain a memory concept, ceiling, unit, or rule without inspecting a project |
| `inventory` | Map the codebase: modules, dependencies, processes, assets, data layer, caches, entry points |
| `estimate` | Derive per-record byte costs, transient peaks, and scaling laws statically; optionally measure on a connected device |
| `plan` | Emit a prioritised, quantified `memory-plan.md` ordered by derived magnitude |
| `diagnose` | Given a symptom — silent kill, OutOfMemoryError, shift-long degradation — identify which ceiling was breached |

`inventory` → `estimate` → `plan` is the normal sequence. Each mode is independently usable.

## Inspect → compatibility → implement → verify

1. **Inspect.** Read the project: Gradle files, manifests, data classes, repositories, caches, bitmap
   paths, lifecycle registrations, service and process declarations. Record what is present, not what
   is expected. Confirm the target path before reading anything outside it.
2. **Compatibility.** Resolve the target device tier and Android version range against the
   compatibility gate in [implementation.md](references/implementation.md). **An unknown device tier
   or fleet OS spread is a stop condition, not a default.**
3. **Implement.** Run the selected mode using [rule-catalogue.md](references/rule-catalogue.md) for
   detection and [quantification.md](references/quantification.md) for costing. Use
   [measurement.md](references/measurement.md) only when a device is available and the developer
   authorises it.
4. **Verify.** Separate what was derived from what was measured, name every term that remains
   unknown, and state the achieved validation level with all remaining checks.

## Local task routes

| Route | Read this | Optional script | Manual fallback |
|---|---|---|---|
| Detect a memory defect | [rule-catalogue.md](references/rule-catalogue.md) | `scripts/scan_memory_rules.py` | Each rule lists its own search signature; apply them by hand |
| Cost a data class or a cache | [quantification.md](references/quantification.md) | `scripts/estimate_object_size.py` | The byte table and worked example are hand-computable |
| Read ceilings and counters from a device | [measurement.md](references/measurement.md) | `scripts/collect_device_memory.sh` | Every command is listed individually to run by hand |
| Decide which ceiling and unit apply | [api-patterns.md](references/api-patterns.md) | — | — |
| Diagnose an observed symptom | [troubleshooting.md](references/troubleshooting.md) | — | — |
| Avoid an unsafe assessment practice | [anti-patterns.md](references/anti-patterns.md) | — | — |

**Every script is an optional accelerator with a documented manual fallback.** The complete static
path works with no shell and no network: read the catalogue, apply the signatures, compute costs from
the byte table in `quantification.md`. Scripts only make that faster and repeatable. The device
routes in `measurement.md` require a shell and a connected device by nature; without them the
assessment is still valid as scaling-only, and must say so.

## Quantification contract

This skill states numbers, so the rules governing them are not optional.

1. **Never state a quantity without either a measurement or a shown derivation.** Three kinds of
   claim: *measured* (device evidence, attributed to a device/OS/scenario tuple), *derived* (a
   source-derived cost function with its parameters visible, labelled a model), and *invented*
   (forbidden).
2. **Never mix units.** Java heap belongs to `OutOfMemoryError`; cgroup anon+swap belongs to
   `MemoryLimiter:AnonSwap`; PSS is proportional attribution and is **not** an enforcement unit.
   A cross-unit comparison is worse than reporting no number. See
   [api-patterns.md](references/api-patterns.md).
3. **Never invent a threshold.** No published per-device memory limit exists for any Zebra RAM tier;
   the enforced values live on the device. Report unknown and emit the command that reads it.
4. **Report headroom, not absolute MB**, because MB is not comparable across a 1 GB and an 8 GB
   device. Always name the binding process state and the binding device tier.
5. **Label every model as a model**, with parameters and calibration state visible, and state the
   likely direction of its error.

## Stop conditions

Stop and report rather than guess when:

- **The target device tier or fleet Android version spread is unknown.** These set the ceiling and
  gate roughly a third of the catalogue. Record the unknown explicitly, then continue only with the
  version-independent subset and say that is what happened.
- **No enforced ceiling can be read and none was supplied.** Emit `estimate` output as scaling-only
  and mark headroom UNKNOWN. Never substitute a figure from another device or from the example
  configuration published for a 14 GB phone.
- **A fix is requested.** This skill does not modify code. Hand off to
  `appquality-memory-migration-android`.
- **The request is about APK or download size**, DataWedge or scanner configuration, responsive
  multi-form-factor layout, or CPU, battery or jank. Those are different capabilities.
- **Native code is present but not inspectable**, or the only native code sits inside a third-party
  archive. Report it as vendor-owned, not as a defect in this codebase.
- **A production record count is needed to close a forecast and none is available.** Emit the scaling
  law and the crossing point expressed in rows; never assume a record count.
- **The developer declines device access.** Proceed statically and mark all measured terms unknown.

## Completion criteria and validation result

Complete when the selected mode produced its contracted output; every quantitative claim is either
attributed to a measurement or shown as a derivation; every unknown term is named rather than filled
in; and the report identifies the binding constraint.

Always close with the **achieved validation level** and what remains:

| Level | What it means here |
|---|---|
| Inspection | Files read, rules applied, costs derived. **The default, and often the only level reached.** |
| Build and behavior | A build ran, or an instrumented iteration protocol produced a leak rate |
| Device | Counters and ceilings read from the declared Zebra device under its real software load |
| Production review | Accountable owners reviewed the plan for security, licensing, and deployment |

Never imply device evidence from a static run. A derived projection is not a measurement, and saying
so plainly is what makes the derivation trustworthy.

## Sources and ownership

Official sources, review dates, and derivation scope: [sources.md](references/sources.md).

- Skill owner: **UNASSIGNED — required before release.**
- Product owner: **UNASSIGNED — required before release.**
- Documentation owner: **UNASSIGNED — required before release.**
- Bundled material is original to this skill. Licensing basis proposed as Apache-2.0 and **not yet
  confirmed by an accountable owner.**
