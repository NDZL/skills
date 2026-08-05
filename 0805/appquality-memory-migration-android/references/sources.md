# Sources, scope, ownership, and redistribution

**Reviewed:** 2026-08-04. Re-verify the platform-behaviour and device rows before each release;
Android behaviour and Zebra device configurations move independently of this skill.

**Active product-specific guide:** **none.** No maintained Zebra product-specific authoring or memory
guide was present in the authoring context, so none is claimed as authority. The offering prefix
`appquality-` is **proposed vocabulary, not approved vocabulary**, and is recorded here as an open
decision rather than an established fact.

## Platform sources — primary

| ID | Official URL | Version scope | Reviewed | What it establishes here |
|---|---|---|---|---|
| A1 | https://developer.android.com/topic/performance/memory | Android 14–17 | 2026-08-04 | Only `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND` are still delivered; legacy levels undelivered from Android 14 and deprecated in Android 15 — STEP-05, AP-07. Services guidance, sparse containers, lite generated code, compile-time injection. R8 configuration — STEP-08 |
| A2 | https://developer.android.com/blog/posts/prioritizing-memory-efficiency-essential-steps-for-android-17 | Android 17 | 2026-08-04 | Memory-limit kills carry no stack trace; the `MemoryLimiter:AnonSwap` description string — STEP-06. The threshold-comparison trim shape — STEP-05. Image practices including the halved cost of `RGB_565` — STEP-03. R8 configuration and its reported outcome — STEP-08 |
| A3 | https://developer.android.com/about/versions/17/behavior-changes-all | Android 17 | 2026-08-04 | Exit reason and description for kill detection — STEP-06. The memory-limiter shell subcommands used to force a kill during verification |
| A4 | https://source.android.com/docs/core/perf/memory-limiter | Android 17+ | 2026-08-04 | cgroup v2 mechanism; both file-backed and anonymous memory counted; clean pages evicted while anonymous pages are swapped; visible versus not-visible limits — the binding-state rule in usage-patterns.md; vendor-owned configuration |
| A5 | https://developer.android.com/about/versions/17/features | Android 17 | 2026-08-04 | Profiling triggers, referenced as an optional extension of STEP-06 |
| A6 | https://developer.android.com/guide/practices/page-sizes | Android 15+ | 2026-08-04 | 16 KB page size is device-selected; AGP 8.5.1 alignment default — recorded in the compatibility gate |
| A9 | https://googlesamples.github.io/android-custom-lint-rules/checks/index.md.html | Current | 2026-08-04 | Existing lint checks that make several steps unnecessary to hand-write |
| A10 | https://developer.android.com/topic/performance/memory-management | Current | 2026-08-04 | RSS, PSS and USS definitions; RSS is better for tracking allocation changes — the verification unit rule and STEP-10; clean cached pages dropped while anonymous pages are compressed into zRAM — the mechanism behind STEP-01 and STEP-02 |
| A11 | https://developer.android.com/reference/android/os/Debug.MemoryInfo | API 23+ | 2026-08-04 | In-app memory statistics, optional for instrumented verification |
| A12 | https://developer.android.com/jetpack/androidx/releases/benchmark | Current | 2026-08-04 | Benchmark harness — STEP-10 |

## Zebra sources — primary

| ID | Official URL | Version scope | Reviewed | What it establishes here |
|---|---|---|---|---|
| Z1 | https://techdocs.zebra.com/datawedge/15-0/guide/programmers-guides/usage-notes/ | DataWedge 15.0 | 2026-08-04 | Low device memory can stop scanning working, and the documented remedy is to fix the leak or uninstall the offending application — the business rationale for STEP-04 |
| Z2 | https://www.zebra.com/android-versions | Current | 2026-08-04 | Per-device Android commitments including 16, 18 and 19; Android 17 is not listed per device, so per-SKU Memory Limiter availability is unknown — compatibility gate |
| Z3 | https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc22-tc27.html | TC22/TC27 | 2026-08-04 | 6 GB and 8 GB configurations — device tiering in STEP-07 |
| Z6 | https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc21-tc26.html | TC21/TC26 | 2026-08-04 | 3 GB and 4 GB configurations — device tiering in STEP-07 |
| Z9 | https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/ | WS50 | 2026-08-04 | 1 GB shared among the kernel, launcher, vendor stack and other services — the low tier that STEP-07 tiering must serve |

Fuller device tier data is recorded in the assessment capability, which owns tier resolution. This
skill needs only enough to tier a cache and to know which fleet floor gates a step.

## Derivation boundary

Derived rather than quoted, and labelled in place:

- **The step catalogue, its ordering rules, and its stop conditions** — this skill's engineering
  judgement built on the sources above.
- **Bitmap byte figures** — `width × height × bytesPerPixel` arithmetic.
- **The anonymous-versus-clean-file-backed rationale** in `api-patterns.md` §2 — an inference from the
  reclaim mechanics quoted in A4 and A10. The mechanics are quoted; the synthesis is this skill's, and
  it is the stated reason STEP-01 and STEP-02 outperform simply shrinking a structure.
- **The change safety contract** — one step per change, step-level approval, reversibility, behaviour
  preservation. Not vendor guidance; a risk posture chosen because this capability writes to a working
  codebase.
- **The ordering constraint that STEP-09 follows an allocation fix** — derived from A2 plus the cgroup
  mechanism in A4.

## Known gaps — state these rather than filling them

1. **No published memory threshold exists for any Zebra RAM tier.** Verification ceilings must be read
   from the device; this skill never supplies one.
2. **Whether Zebra ships a valid memory-limiter configuration** is unverified. If it is absent or
   invalid the mechanism is disabled on that device, which changes what STEP-06 will ever observe.
3. **Which Zebra SKUs receive Android 17 or later, and when**, is unverified.
4. **No bundled pattern has been compiled against a real project** in this authoring pass. Patterns are
   reviewed for correctness by inspection only — see the achieved validation level below.
5. Expected effect sizes for each step are **derived**, not benchmarked on Zebra hardware.

## Ownership

- Skill owner: **UNASSIGNED — required before release.**
- Product owner: **UNASSIGNED — required before release.**
- Documentation owner: **UNASSIGNED — required before release.**

Recorded as unresolved rather than invented. These block release, not authoring.

## Bundled material

| Item | Version | Derivation | License / redistribution basis |
|---|---|---|---|
| `../assets/code-patterns/` (8 patterns) | 1.0.0 | Original to this skill; use only platform APIs and widely available library shapes | Apache-2.0 **proposed, not confirmed** |
| `../assets/test-fixtures/` | 1.0.0 | Original synthetic before-and-after pairs | Apache-2.0 **proposed, not confirmed** |
| `../scripts/verify_memory_delta.sh` | 1.0.0 | Original; wraps documented platform shell commands (A3, A4, A10) | Apache-2.0 **proposed, not confirmed** |

No credentials, signing material, proprietary binaries, restricted models, or third-party code are
bundled. Patterns name library shapes generically rather than pinning versions, because a pinned
version would go stale independently of this skill.

## Achieved validation level for bundled material

**Inspection.** Patterns were authored and reviewed as text. They have **not** been compiled, run, or
measured on a device in this authoring pass. Any claim beyond `Inspection` for this package would be
unsupported.
