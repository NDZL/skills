name: appquality-memory-migration-android
description: "Apply approved memory-reduction changes to an existing Android application for Zebra enterprise devices, one staged reversible step at a time, then verify the result in the same unit it was measured. Executes only developer-approved steps: streaming JSON parsing into Room, bounded caches, sampled bitmap decoding, lifecycle leak fixes, R8 and manifest configuration, Android 14+ trim handling, and MemoryLimiter kill detection via ApplicationExitInfo. Consumes a memory-plan.md when one is present, and works from developer-stated steps alone when it is not. Use when a memory plan or assessment already exists, or when a specific memory defect is known and a code change is wanted. Do not use to discover or quantify a footprint first (hand off to appquality-memory-assessment-android), for APK or download size reduction, for DataWedge or scanner configuration, or for CPU, battery or jank work."
---

# appquality-memory-migration-android

## Release metadata

- Version: `1.0.0-beta.1`
- Stage: `Beta`

> **Beta:** This skill is available for early evaluation. Its workflows and behavioral contract
> may change before stable release. Validate generated output before production use. This status
> applies to the skill package—not to referenced Zebra products, SDKs, APIs, or models.

This skill **modifies code**. Every change is gated on explicit developer approval of that specific
step, applied as one reversible unit, and paired with the verification that would confirm it. Nothing
is applied in bulk, and no step is applied because it seemed obviously correct.

## Supported task modes

| Mode | Outcome |
|---|---|
| `explain` | Explain what a migration step does and what it costs, without touching files |
| `intake` | Read a `memory-plan.md`, or accept developer-stated steps, and produce an ordered approval list |
| `migrate` | Apply **one approved step**, reversibly, with its verification |
| `verify` | Re-measure after a step and report the before-and-after in the same unit |
| `validate` | Check that applied steps are internally consistent and that no step was applied unapproved |

`intake` → per-step approval → `migrate` → `verify` is the loop. It repeats once per step.

## Inspect → compatibility → implement → verify

1. **Inspect.** Read the plan if one exists, or the developer's stated steps. Read the code each step
   touches *before* proposing an edit. Establish that a before-measurement exists, or record that
   the improvement will be unverifiable.
2. **Compatibility.** Resolve `minSdk`, target device tier, and Android version range against the
   gate in [api-patterns.md](references/api-patterns.md). Several steps are version-gated and applying
   them outside their range is a regression, not an improvement.
3. **Implement.** Apply exactly one step from
   [migration-steps.md](references/migration-steps.md), using the matching pattern in
   `assets/code-patterns/`. Keep the change reversible and self-contained.
4. **Verify.** Re-measure per [usage-patterns.md](references/usage-patterns.md), compare in the same
   unit as the before-measurement, and state the achieved validation level.

## Local task routes

| Route | Read this | Pattern |
|---|---|---|
| Stream a whole-response parse into the database | [migration-steps.md](references/migration-steps.md) STEP-01 | `assets/code-patterns/streaming-json-to-room/` |
| Bound an unbounded cache, or move it to the database | STEP-02 | `assets/code-patterns/bounded-cache/` |
| Downsample a bitmap decode | STEP-03 | `assets/code-patterns/sampled-bitmap-decode/` |
| Fix a lifecycle leak | STEP-04 | `assets/code-patterns/lifecycle-scoped-registration/` |
| Correct pressure handling for Android 14+ | STEP-05 | `assets/code-patterns/trim-memory-handler/` |
| Detect memory-limit kills in the field | STEP-06 | `assets/code-patterns/exit-info-memory-limit/` |
| Cap an image loader cache and tier it by device | STEP-07 | `assets/code-patterns/tiered-image-loader/` |
| Enable R8 fully and narrow keep rules | STEP-08 | documented in `migration-steps.md`; build-file change only |
| Remove `largeHeap` | STEP-09 | documented in `migration-steps.md`; manifest change only |
| Add a memory regression gate | STEP-10 | `assets/code-patterns/memory-regression-gate/` |
| Verify a step on a device | [usage-patterns.md](references/usage-patterns.md) | `scripts/verify_memory_delta.sh` |
| Refuse an unsafe change | [anti-patterns.md](references/anti-patterns.md) | — |
| A step made things worse | [troubleshooting.md](references/troubleshooting.md) | — |

**The only script is an optional accelerator with a documented manual fallback.**
`scripts/verify_memory_delta.sh` batches device reads for the before-and-after comparison; the manual
fallback is to run each command individually as listed in
[usage-patterns.md](references/usage-patterns.md). Every code change route works with no shell and no
network — the patterns are text in this package. Without a device, apply the step and report the
improvement as **unverified**, which is honest and still useful.

## Change safety contract

Non-negotiable, because this skill writes to a working codebase.

1. **One step per change.** Never batch. If two steps touch the same file, apply and verify them
   separately so a regression is attributable.
2. **Explicit approval per step**, naming the step and the files it will touch. Approval of the plan
   is **not** approval of every step in it.
3. **Reversible.** State how to undo the step before applying it. Prefer changes that are a clean
   revert.
4. **No opportunistic edits.** Do not reformat, rename, upgrade dependencies, or fix unrelated
   defects while in a file. That destroys attribution of the memory result.
5. **Behaviour preserved.** A memory fix that changes observable behaviour is a defect. Streaming a
   parse must produce the same stored data; bounding a cache must not change query results.
6. **Verification named up front.** If a step cannot be verified in this environment, say so before
   applying it, not afterwards.
7. **Never invent a number** to justify a step. Use the plan's derived figure, labelled as a model, or
   a measurement with its device tuple.

## Stop conditions

Stop and report rather than proceed when:

- **No approval exists for the specific step.** A general instruction to improve memory is not
  step-level approval.
- **No plan and no stated step.** Ask what to change, or hand off to
  `appquality-memory-assessment-android` to discover it. Do not begin assessing.
- **`minSdk` or device tier is unknown and the step is version-gated** — STEP-05 and STEP-06 in
  particular. Applying these outside their range removes working code.
- **The step requires a behaviour decision** the developer has not made, such as which fields a
  narrowed projection must keep, or what cache size is acceptable.
- **A before-measurement does not exist** and the developer wants a verified improvement. Establish
  the baseline first, or agree the result will be unverified.
- **The step is a refactor rather than a fix** — if the change cannot be completed and verified in one
  sitting, it is architectural work and needs its own plan.
- **The request is for assessment, APK size, scanner configuration, or CPU, battery, or jank work.**
  Those are different capabilities.
- **The change would touch vendor-owned material** such as a third-party archive. Route it to the
  vendor.

## Completion criteria and validation result

Complete a step when: the developer approved that specific step; the edit is applied and reversible;
behaviour is preserved; the verification named up front was either run or explicitly reported as not
run; and the before-and-after are stated in the same unit.

Always close with the **achieved validation level** and what remains:

| Level | What it means here |
|---|---|
| Inspection | The edit was applied and read back. **No memory improvement is demonstrated at this level.** |
| Build and behavior | The project builds, and tests covering the changed path pass |
| Device | Before-and-after measured on the declared Zebra device in the same unit and process state |
| Production review | Accountable owners reviewed the change for security, licensing, and deployment |

**Applying a memory fix is not the same as reducing memory.** Only the Device level demonstrates a
reduction; anything less is a plausible change with an unmeasured effect, and must be reported that
way.

## Sources and ownership

Official sources, review dates, and derivation scope: [sources.md](references/sources.md).

- Skill owner: **UNASSIGNED — required before release.**
- Product owner: **UNASSIGNED — required before release.**
- Documentation owner: **UNASSIGNED — required before release.**
- Bundled material is original to this skill. Licensing basis proposed as Apache-2.0 and **not yet
  confirmed by an accountable owner.**
