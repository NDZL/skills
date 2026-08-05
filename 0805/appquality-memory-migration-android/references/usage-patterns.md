# Usage patterns — applying and verifying one step

Each route below states the developer outcome, prerequisites and version scope, the exact bundled code
location, how to apply it, the expected result, verification, limitations, provenance, achieved
validation level, owner, and licensing basis. Common fields are stated once here rather than repeated
ten times.

**Common to every route**

- **Provenance:** derived from the sources recorded in [sources.md](sources.md). Step rationale and
  version gates in [api-patterns.md](api-patterns.md) and
  [migration-steps.md](migration-steps.md).
- **Owner:** **UNASSIGNED — required before release.**
- **Licensing basis:** bundled patterns are original to this skill; Apache-2.0 **proposed, not
  confirmed.**
- **Achieved validation level per route:** `Inspection` after the edit; `Build and behavior` after a
  clean build with tests over the changed path; `Device` only after a before-and-after measurement in
  the same unit and process state. **Applying a fix is not reducing memory — only the Device level
  demonstrates a reduction.**
- **Universal prerequisite:** step-level developer approval. Plan approval is not step approval.

---

## The verification procedure

Run this around **every** code step. It is the same shape each time, which is what makes deltas
comparable.

**Before the edit**

```
PKG=<application-id>
PID=$(adb shell pidof -s $PKG)
adb shell "grep -E 'RssAnon|RssFile|VmSwap' /proc/$PID/status"
adb shell dumpsys meminfo $PKG | head -20
```

Record the tuple: `{device, ram tier, os build, unit, process state, scenario, value}`.

**Drive the same scenario** that the step is meant to improve — the sync for STEP-01, the image screen
for STEP-03, the repeated workflow for STEP-04.

**Sample the binding state.** For anything that runs in a service or worker, press Home so the process
is not visible, then sample again. That is the tighter ceiling and usually the one that matters.

**After the edit**, repeat identically: same device, same OS build, same scenario, same state, same
unit.

`../scripts/verify_memory_delta.sh` batches this; the manual fallback is the commands above. Without a
device, report the change as applied and the improvement as **unmeasured**.

**Rules:** never compare across units; never compare across devices; discard warm-up iterations;
a single sample is not a measurement for anything accumulating.

---

## Route STEP-01 — stream a whole-response parse

- **Outcome:** peak memory during sync becomes proportional to the batch size instead of the response
  size.
- **Prerequisites:** a streaming-capable parser already in the dependency set (adding one needs
  approval); the payload scales with customer data. Version scope: none.
- **Code location:** `../assets/code-patterns/streaming-json-to-room/StreamingSync.kt`
- **Apply:** replace the read-to-string-then-parse call with the streaming read; write in batches
  inside a transaction; keep the existing dispatcher.
- **Expected result:** peak `RssAnon` during sync drops substantially; steady state changes little.
- **Verification:** the procedure above, scenario = full sync, state = not-visible.
- **Limitations:** changes failure timing — a partial parse can leave partial data, which the
  transaction must make all-or-nothing. Does not help if the payload is small.
- **Hand-off:** if the sync needs restructuring rather than a parser swap, that is architectural work
  and needs its own plan.

## Route STEP-02 — bound a cache or move it to the database

- **Outcome:** a permanently resident structure that scaled with customer data is bounded, or replaced
  by database queries.
- **Prerequisites:** the developer decides the cache size, or that the database is queried directly.
  Version scope: none.
- **Code location:** `../assets/code-patterns/bounded-cache/BoundedItemCache.kt`
- **Apply:** replace the unbounded map. Check first for code that depended on the cache holding
  everything — an iteration over all values, or a size check.
- **Expected result:** steady-state `RssAnon` drops by roughly the plan's derived per-row cost times
  the real record count. Moving to the database also shifts bytes from anonymous to clean file-backed,
  which the kernel can drop for free.
- **Verification:** steady state after the data-heavy screen, plus the derived figure for comparison.
- **Limitations:** lookup latency may rise. Correctness must not change.

## Route STEP-03 — downsample a bitmap decode

- **Outcome:** images are decoded at display size rather than source size.
- **Prerequisites:** know the target display dimensions. `ImageDecoder` needs API 28+; otherwise use
  the sampled `BitmapFactory` path in the same pattern.
- **Code location:** `../assets/code-patterns/sampled-bitmap-decode/SampledDecode.kt`
- **Apply:** add the bounds pass, compute the sample size, decode sampled; use `RGB_565` only where
  transparency is not needed.
- **Expected result:** a 12 MP decode falls from roughly 48 MB to roughly 3.5 MB at display size.
- **Verification:** peak during the image operation; the graphics and native lines of the breakdown.
- **Limitations:** downstream code assuming full-resolution dimensions will need adjusting; that
  adjustment is part of this step and must not change visible quality at the displayed size.

## Route STEP-04 — scope a lifecycle registration

- **Outcome:** accumulation across a shift stops.
- **Prerequisites:** **confirm whether the app legitimately needs these events while stopped.** Version
  scope: none.
- **Code location:** `../assets/code-patterns/lifecycle-scoped-registration/ScopedReceiver.kt`
- **Apply:** move registration to the start method, add removal to the stop method, make removal
  idempotent.
- **Expected result:** the `RssAnon` slope across repeated workflows flattens; the activity count in
  the breakdown returns to the number actually open.
- **Verification:** repeat the workflow 20 to 50 times, discard the first few, fit a line. Express the
  result **per business transaction** — per pick, per scan — not per hour.
- **Limitations:** **this is the step most likely to change behaviour.** If events are needed while
  stopped, this scoping drops them; use a lifecycle-aware alternative instead.

## Route STEP-05 — rewrite pressure handling

- **Outcome:** pressure handling that actually runs.
- **Prerequisites:** **the fleet floor must be known and be Android 14 or higher.** Otherwise keep both
  shapes.
- **Code location:** `../assets/code-patterns/trim-memory-handler/AppTrimHandler.kt`
- **Apply:** replace the legacy dispatch with threshold comparisons; preserve the existing release
  logic unchanged.
- **Expected result:** the release path is invoked on backgrounding. Modest but free.
- **Verification:** instrument the release path; background the app; confirm invocation.
- **Limitations:** no measurable steady-state change; it reduces kill probability rather than footprint.

## Route STEP-06 — detect memory-limit kills

- **Outcome:** visibility into an otherwise invisible failure.
- **Prerequisites:** API 30+ for exit info; the description string is Android 17+.
- **Code location:** `../assets/code-patterns/exit-info-memory-limit/MemoryLimitKillReporter.kt`
- **Apply:** read at startup off the main thread; de-duplicate by timestamp; report to telemetry.
- **Expected result:** **no memory reduction.** This buys observability. Report it as such.
- **Verification:** force a kill with the memory-limiter manual override, relaunch, confirm exactly one
  event.
- **Limitations:** historical only; unavailable below API 30.

## Route STEP-07 — cap and tier an image loader cache

- **Outcome:** the image cache stops taking a phone-sized share of a constrained heap.
- **Prerequisites:** the developer chooses the size per tier. Version scope: none.
- **Code location:** `../assets/code-patterns/tiered-image-loader/TieredImageLoader.kt`
- **Apply:** set explicit memory and disk cache sizes, tiered on what the device reports.
- **Expected result:** steady-state `RssAnon` drops after image-heavy browsing.
- **Verification:** steady state on the image screen, before and after.
- **Limitations:** more cache misses; images still available.

## Route STEP-08 — enable R8 fully

- **Outcome:** a smaller compiled codebase, and so less resident code memory.
- **Prerequisites:** a release build type; every reflection-dependent path identified. Needs AGP.
- **Code location:** build files only — no pattern.
- **Apply:** enable minification and resource shrinking, use the optimising configuration, remove the
  full-mode opt-out, narrow wildcards to specific classes with a recorded reason for each.
- **Expected result:** reduced code footprint.
- **Verification:** **a clean release build, then exercise every reflection-dependent path.** This is
  the step most likely to fail at runtime rather than at build time.
- **Limitations / stop condition:** if a narrowed wildcard cannot be validated, keep it and record why.
  A broken release is worse than a larger one.

## Route STEP-09 — remove `largeHeap`

- **Outcome:** failures become diagnosable again.
- **Prerequisites:** **the masked allocation must already be fixed.** Sequence after STEP-01, 02, or 03.
- **Code location:** manifest only — no pattern.
- **Apply:** delete the attribute; change nothing else.
- **Expected result:** errors regain stack traces instead of appearing as silent kills.
- **Verification:** run the previously failing workflow after the allocation fix.
- **Stop condition:** removing it first makes the app fail sooner. Ordering is the point of this step.

## Route STEP-10 — add a memory regression gate

- **Outcome:** growth between releases becomes observable.
- **Prerequisites:** a device or emulator available to CI. Version scope: none.
- **Code location:** `../assets/code-patterns/memory-regression-gate/MemoryBenchmark.kt`
- **Apply:** add the benchmark and commit a baseline alongside it.
- **Expected result:** no reduction now; future regressions fail the build.
- **Verification:** the gate fails on a deliberately introduced regression and passes on an unchanged
  tree.
- **Limitations:** absolute values are not portable across devices; **only deltas on one device are a
  valid signal.** Emulator CI must be labelled as not validating headroom.
