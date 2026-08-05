# Implementation — applying a memory step safely

## Compatibility gate

The authoritative platform-behaviour gate is in [api-patterns.md](api-patterns.md). This gate covers
the migration workflow itself.

| Dimension | Supported scope | Unknown behaviour |
|---|---|---|
| Platform | Android only (ART, Gradle/AGP) | Non-Android target → stop |
| Android OS | 11 through 19 | Version-gated steps stop; version-independent steps continue |
| `minSdk` | Must be known before STEP-05 or STEP-06 | **Stop.** Applying these below their floor removes working code |
| Device tier | 1 GB through 8 GB | Unknown → steps still apply; verification cannot compute headroom |
| Build system | Gradle with AGP | Non-Gradle → STEP-08 does not apply |
| Language | Kotlin, Java, or mixed | Patterns are given in Kotlin; Java equivalents are described |
| UI toolkit | XML Views, Compose, or both | Affects STEP-04 cleanup mechanics |
| Shell access | Optional | Absent → code changes still apply; improvements are unverified |
| Network access | Never required | Not used |

**Reviewed:** 2026-08-04. Provenance: [sources.md](sources.md).

## Prerequisites

1. **An approved step.** Either an entry in a `memory-plan.md`, or a developer-stated defect. Without
   one, stop — discovery belongs to `appquality-memory-assessment-android`.
2. **`minSdk` and the fleet Android version spread.** Required before any version-gated step.
3. **A before-measurement**, if a verified improvement is wanted. Without it the step can still be
   applied, but the result is unverifiable and must be labelled so.
4. **Version control, or an explicit acknowledgement that there is none.** Reversibility is part of
   the contract; without version control, state the manual undo before editing.

## The migration loop

Once per step. Never batched.

```
intake  ->  propose ONE step  ->  developer approves THAT step  ->  apply  ->  verify  ->  report
   ^                                                                                        |
   +----------------------------------- next step ------------------------------------------+
```

### 1. Intake

If a plan exists, read it and preserve its ordering — a plan from the assessment skill is already
ordered by derived magnitude. If there is no plan, take the developer's stated steps and order them
yourself by expected magnitude, saying that the ordering is your judgement rather than a derived
figure.

Produce an approval list: step identifier, target files, expected effect, reversal method, and the
verification that would confirm it. **A plan is not an approval.** The developer approves steps one
at a time.

### 2. Propose one step

State, before editing anything:

- the step identifier from [migration-steps.md](migration-steps.md);
- every file it will touch;
- the expected effect, quoted from the plan's derived figure and labelled a model, or from a
  measurement with its device tuple;
- how to reverse it;
- the verification that will be run, or that verification is unavailable here.

### 3. Apply

Use the bundled pattern for that step. Rules:

- **Nothing else changes.** No reformatting, no renames, no dependency upgrades, no unrelated fixes.
  Opportunistic edits destroy the attribution of the memory result, which is the whole point of the
  exercise.
- **Behaviour is preserved.** Streaming a parse must store the same data. Bounding a cache must not
  change query results. Narrowing a projection must not drop a field something reads.
- **Keep it a clean revert.** Prefer additive changes and small localised edits.
- If applying the step reveals that it is larger than described — an architectural change rather than
  a fix — **stop, revert, and report.** Do not finish a refactor that was approved as a fix.

### 4. Verify

Follow [usage-patterns.md](usage-patterns.md) for the step's verification. Two obligations:

- **Same unit, same state, same device** as the before-measurement. A before figure in Java heap and
  an after figure in RSS prove nothing.
- **Report honestly when unverified.** If there is no device, say the change is applied and the
  improvement is unmeasured. Do not describe an expected improvement as an achieved one.

### 5. Report

Per step: what changed, the reversal method, the before-and-after with units, the achieved validation
level, and what remains unverified.

## Existing project integration

Every step here targets an existing codebase; there is no new-project route. Integration concerns:

| Concern | Handling |
|---|---|
| Build files | STEP-08 and STEP-09 touch Gradle and the manifest only. Apply and verify separately from code steps. |
| Database schema | STEP-01 and STEP-02 may add a table or index. Migration scripts are the developer's decision; propose, do not assume. |
| Dependencies | Some steps need a library already present (a streaming parser, an image loader). **Do not add a dependency without approval** — a new dependency is itself memory. |
| Threading | Streaming and batched writes must stay off the main thread. Preserve the existing dispatcher choice rather than introducing one. |
| Cleanup | STEP-04 adds unregistration paths; make sure they cannot run twice and cannot throw during teardown. |
| Error handling | Streaming changes failure timing: a partial parse can leave partial data. Wrap batched writes in a transaction so failure is all-or-nothing. |

## Manual fallbacks

| Script | Accelerates | Text-first fallback | Unverified without it |
|---|---|---|---|
| `../scripts/verify_memory_delta.sh` | Before-and-after device reads for one step | Run each command individually as listed in [usage-patterns.md](usage-patterns.md) | Nothing conceptually; it is a convenience wrapper. Without a device, the improvement itself stays unmeasured. |

All code patterns are plain text in this package and need no tooling to apply.

## Verification and result format

```
STEP-02 · bounded cache · ItemRepository.kt
  approved by developer: yes (step-level)
  files touched:        ItemRepository.kt
  behaviour preserved:  yes -- same lookup results, now database-backed
  reversal:             revert the single commit; no schema change
  before:  RssAnon 214 MB  {TC26, 3GB, A16, not-visible, full-sync}
  after:   RssAnon 121 MB  {TC26, 3GB, A16, not-visible, full-sync}
  delta:   -93 MB in the binding state
  achieved validation level: Device
  remaining: production review; other plan steps not yet applied
```

If no device was available, the last three lines instead read:

```
  after:   NOT MEASURED -- no device available
  achieved validation level: Build and behavior
  remaining: device verification; the improvement is expected but unproven
```

State every time: applying a memory fix is not the same as reducing memory. Only device measurement
demonstrates a reduction.

Ownership, licensing, and redistribution basis: [sources.md](sources.md).
