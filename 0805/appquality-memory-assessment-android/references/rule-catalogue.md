# Rule catalogue — detection signatures

Three tiers of memory knowledge, and the tiering decides who checks what:

| Tier | What it is | Who checks it | Verdict strength |
|---|---|---|---|
| **A** | Statically decidable, **a check already exists** | Android Lint, R8, AGP | Deterministic. Wire it up; do not reimplement it. |
| **B** | Statically decidable, **no existing check** | This skill | High-confidence finding. Where this catalogue earns its keep. |
| **C** | Not statically decidable | Device, per [measurement.md](measurement.md) | Assert the harness and the scaling law; take absolute values from measurement. |

Severity is tied to consequence, not taste:

| Severity | Meaning |
|---|---|
| **BLOCKER** | Can cause an untraceable memory-limit kill or `OutOfMemoryError` in normal field use |
| **HIGH** | Unbounded or per-record growth; fails at the largest customer, passes in development |
| **MEDIUM** | Fixed avoidable overhead, or memory code that cannot work |
| **LOW** | Hygiene; matters in aggregate or in hot paths only |

**Every rule has a mandatory false-positive clause.** A memory report that cries wolf is disabled
within a week and then catches nothing forever. When in doubt, downgrade to *justify this* rather than
*defect*.

---

## Tier A — verify these are switched on first

Cost nothing, catch real defects deterministically, and are already maintained. Report whether they
are enabled, not what they found.

**Build configuration:** `isMinifyEnabled = true`; `isShrinkResources = true`;
`proguard-android-optimize.txt` rather than the legacy file; **remove** `android.enableR8.fullMode =
false`; avoid `-dontoptimize`, `-dontshrink`, `-dontobfuscate`; avoid package-wide keep wildcards,
because poorly written keep rules prevent optimisation across large regions. Code footprint is
resident memory, not only download size.

**Existing Android Lint checks worth promoting to error:** `StaticFieldLeak`, `HandlerLeak`,
`Recycle`, `DrawAllocation`, `UseSparseArrays`, `UnusedResources`, `WifiManagerLeak`,
`WifiManagerPotentialLeak`, `CastingViewContextToActivity`, `CommitPrefEdits`, `CommitTransaction`,
`AutoDispose`, `BrokenIterator`. Compose: `AutoboxingStateCreation`, `AutoboxingStateValueProperty`,
`UnrememberedMutableState`, `MutableCollectionMutableState`, `ComposeRememberMissing`,
`RememberInComposition`, `RetainLeaksContext`.

**The trap in Tier A:** not one of these can detect an unbounded master-data cache or a whole-response
parse — the two most expensive defects in enterprise apps. Both are idiomatic, warning-free code. That
gap is why Tier B exists.

---

## Family `MEM-BUILD` — build and manifest

### MEM-BUILD-001 · BLOCKER · `android:largeHeap="true"`
- **Signature:** `android:largeHeap` in any manifest.
- **Why:** almost always added to silence an error rather than fix it. It lets the process grow until
  it destabilises a device shared with the vendor stack, and under Memory Limiter a larger heap reaches
  the cgroup limit *sooner* — converting a reproducible, stack-traced `OutOfMemoryError` into an
  **untraceable** kill. It makes diagnosis harder, the opposite of the author's intent.
- **Fix:** remove it; fix the allocation instead.
- **False positives:** none. Always report; accept only a documented, measured waiver.

### MEM-BUILD-002 · HIGH · R8 not fully enabled
- **Signature:** release build type with `isMinifyEnabled = false`; missing `isShrinkResources`;
  `android.enableR8.fullMode = false`; legacy ProGuard configuration file.
- **False positives:** debug and internal variants — assert only on release-shaped build types.

### MEM-BUILD-003 · MEDIUM · over-broad keep rules
- **Signature:** `-keep class …** { *; }`, `-dontoptimize`, `-dontshrink`, `-dontobfuscate`.
- **False positives:** a genuinely reflection-driven dependency may need a broad rule. Require a
  comment naming it and report as *needs justification*.

### MEM-BUILD-004 · LOW · redundant density buckets or oversized raster assets
- **Signature:** multiple `drawable-*dpi` buckets for a fixed-density fleet; images larger than the
  target screen; a full-HD splash asset.
- **False positives:** a genuinely multi-density fleet. Confirm the device list first.

### MEM-BUILD-005 · MEDIUM · `extractNativeLibs="true"` or unaligned native libraries
- **Signature:** the manifest flag; AGP below 8.5.1; hardcoded `4096` or page-size assumptions in
  native code.
- **Why (a), compatibility — the stronger reason:** on a 16 KB-page device, unaligned libraries mean
  the app will not work in future releases; backcompat mode is a warning-bearing reprieve. **The page
  size is the device's choice, not the app's, so complying is not a memory saving.**
- **Why (b), memory — second order:** `extractNativeLibs=false` keeps segments mapped from the archive
  as clean, file-backed, shareable pages rather than extracted private copies. Clean file-backed pages
  can be dropped; anonymous pages must be compressed.
- **False positives:** **pure-JVM projects with no native code — skip entirely.** If the only native
  code is inside a third-party archive you cannot recompile, route it as *raise with the vendor*.
- **Scope:** Android 15+.

---

## Family `MEM-BITMAP`

### MEM-BITMAP-001 · BLOCKER · decode without downsampling
- **Signature:** `BitmapFactory.decodeFile|decodeStream|decodeByteArray|decodeResource` where the
  enclosing function contains no `inSampleSize`, no `inJustDecodeBounds` bounds pass, and no
  `setTargetSampleSize`.
- **Why:** `bytes = width × height × bytesPerPixel`. A 12 MP image at 4000 × 3000 is **48 MB** at
  ARGB_8888; downsampled to display size it is ~3.5 MB — a 93 % reduction for no perceptible loss,
  because the screen cannot show 12 MP. Highest-magnitude single defect reachable from source.
- **Fix:** two-pass bounds-then-sample decode, or `ImageDecoder` with a target sample size and the
  low-memory policy; or delegate to an image library with an explicit target size.
- **False positives:** decoding a known-small bundled asset. Check the source's provenance — camera,
  gallery, file, and network input are never small.

### MEM-BITMAP-002 · HIGH · `ARGB_8888` for opaque content
- **Signature:** explicit `ARGB_8888`, or no configuration set on a photographic path.
- **Why:** exactly **twice** the memory of `RGB_565`.
- **False positives:** anything needing transparency — icons, overlays, masks, and gradient-heavy
  imagery where banding would show.

### MEM-BITMAP-003 · MEDIUM · bitmap retained past its consumer
- **Signature:** `Bitmap` in a field, `companion object`, `object`, or static; bitmaps added to a
  collection with no removal; no `recycle()` on a manually created bitmap.
- **False positives:** a deliberately cached, small, bounded set. Judge by bound and size, not by the
  presence of a field.

### MEM-BITMAP-004 · MEDIUM · full-resolution capture with no resize step
- **Signature:** image capture with no target resolution and no downscale before decode or upload.
- **False positives:** a genuine archival-resolution requirement — then require a streaming path.

---

## Family `MEM-CACHE`

### MEM-CACHE-001 · BLOCKER · unbounded in-memory cache
- **Signature:** a long-lived map or list in an `object`, `companion object`, singleton, or
  `Application` subclass, written from a loop or a query result, with **no eviction** — no bounded
  cache, no size cap, no clear on any lifecycle signal.
- **Why:** the defining enterprise memory defect. Master data grows with the *customer's* data, not
  the developer's. It passes every test on a small fixture and dies at the customer. Permanently
  resident, so it counts in **every** process state including the restrictive one. Cost it with
  [quantification.md](quantification.md) §2 and emit the projection table.
- **Fix:** a bounded cache with an explicitly computed size; or do not cache — query the database,
  which already has a tuned page cache backed by reclaimable file pages. **Letting the database be the
  cache converts anonymous bytes into clean file-backed bytes**, and is usually a net simplification.
- **False positives:** genuinely bounded, enumerable domain data — a 12-entry status lookup, a
  50-entry country list. **The test is: is the upper bound set by our code or by the customer's data?**
  Only the latter is a defect.

### MEM-CACHE-002 · HIGH · image cache left at default size
- **Signature:** an image loader configured with no explicit memory-cache size, or none configured at
  all while loading remote images.
- **Why:** defaults are a percentage of available heap, calibrated for consumer phones. On a shared
  3 GB device that percentage is memory the workflow needed.
- **False positives:** an app that loads no remote or large images.

### MEM-CACHE-003 · MEDIUM · cache never released under pressure
- **Signature:** a cache exists, but no pressure path clears it — or only a path built on levels that
  are no longer delivered (cross-check MEM-PRESSURE-001).
- **False positives:** caches whose rebuild cost exceeds the benefit *and* whose size is trivially
  bounded.

---

## Family `MEM-DATA`

### MEM-DATA-001 · BLOCKER · whole-response deserialisation
- **Signature:** reading an entire response body to a string or byte array, then parsing from that
  string; or an accumulating output stream buffering a whole download.
- **Why:** peak resident memory holds the raw string **plus** the parser's intermediate structures
  **plus** the transfer objects **plus** frequently a second mapped entity graph, all live at once. The
  *multiplicity* is structural and certain even where the magnitude varies.
- **Fix:** stream. Parse from a source or stream API and write to the database in batched
  transactions as you go. Peak becomes proportional to the batch, not the response. This is the
  highest-leverage refactor in most enterprise sync code.
- **False positives:** small bounded responses — a login, a configuration blob, a single record. Gate
  on whether the payload scales with customer data.

### MEM-DATA-002 · HIGH · unpaged full-table query
- **Signature:** a query returning a full list with no limit and no paging source; cursor iteration
  accumulating into a list; `SELECT *` on a wide table.
- **Why:** materialises every row *and* every column. A 5 000-row list behind a screen showing eight
  rows is 5 000 objects with no reader.
- **False positives:** a query with a genuine small bound.

### MEM-DATA-003 · MEDIUM · over-wide projection
- **Signature:** `SELECT *`; transfer objects with many unread fields; embedded types pulling unused
  columns.
- **False positives:** a detail screen that genuinely needs every column.

### MEM-DATA-004 · MEDIUM · unclosed cursor or closeable
- **Signature:** a query or stream opened without a scoped-use construct or a `finally` close.
- **Why:** cursor windows are real off-heap buffers; leaked ones accumulate.
- **False positives:** cursors deliberately owned by an adapter or loader that closes them — verify the
  owner exists.

### MEM-DATA-005 · LOW · boxing and copying in hot paths
- **Signature:** integer-keyed hash maps; list-copying chains inside loops over large data; string
  concatenation in a loop.
- **False positives:** small collections and cold code. **Do not report this on a ten-element list** —
  it is the easiest way to make the report feel like noise.

### MEM-DATA-006 · LOW · verbose generated code
- **Signature:** full, non-lite generated serialisation code on the client.
- **Why:** verbose generated code increases the code footprint held in memory.
- **False positives:** shared server modules — but not on-device.

---

## Family `MEM-LIFECYCLE` — leaks

### MEM-LIFECYCLE-001 · BLOCKER · registration without a matching unregistration
- **Signature:** `registerReceiver`, `addListener`, `addObserver`, `setCallback`, or a subscription
  with no symmetric removal in the mirror lifecycle method. **Weight scanner and device-management
  receivers highest.**
- **Why:** the signature enterprise leak. A receiver registered in `onCreate` and never removed holds
  the activity — with its view tree, adapter, and bitmaps — for the life of a process that lives a
  whole shift. Note that the device vendor's documented remedy for a leaking application is to
  uninstall it.
- **Fix:** register in `onStart`, remove in `onStop`; or use a lifecycle-aware observer; in Compose,
  a disposable effect with cleanup.
- **False positives:** registration genuinely scoped to the application lifetime using the application
  context and capturing no activity — verify which context is captured.

### MEM-LIFECYCLE-002 · HIGH · view binding not cleared
- **Signature:** a binding field with no assignment to null in the view-destroyed method.
- **False positives:** a property delegate that already handles it — check for the delegate first.

### MEM-LIFECYCLE-003 · BLOCKER · context, activity, or view reachable from a static
- **Signature:** a `companion object`, `object`, or static field typed as a context, activity, view,
  or fragment; a context passed into a view model constructor; a composition-local context handed to a
  view model.
- **Why:** permanent retention. Largely covered by Tier A, but **the view-model and Compose variants
  are exactly the ones existing checks miss.**
- **Fix:** use the application context where one is genuinely needed; otherwise inject, or expose state
  through a flow rather than passing a context inward.
- **False positives:** the application object held statically is conventional and safe.

### MEM-LIFECYCLE-004 · HIGH · unscoped coroutine or unmanaged thread
- **Signature:** a global-scope launch; a scope created in a UI class with no cancellation; a bare
  thread start; an executor field never shut down; timer tasks.
- **False positives:** a deliberately application-scoped supervisor owned by the application object.

### MEM-LIFECYCLE-005 · MEDIUM · non-static inner class holding its outer instance
- **Signature:** an inner handler, task, runnable, receiver, or callback declared as a non-static inner
  class and retained beyond the outer scope.
- **False positives:** short-lived inner classes that never outlive their outer instance.

---

## Family `MEM-PROC` — processes and services

### MEM-PROC-001 · HIGH · long-running or always-on service
- **Signature:** a service that restarts indefinitely, is started from boot, or never stops itself; a
  foreground service kept alive across a whole shift for periodic work.
- **Why:** platform guidance is blunt — leaving unnecessary services running is one of the worst
  memory-management mistakes an app can make. It also keeps the whole process, with all its caches,
  resident and countable.
- **Fix:** deferrable work scheduling. Keep a foreground service only for genuinely continuous,
  user-visible work.
- **False positives:** legitimately continuous work — an active session, a persistent scanner or
  wireless connection. Then MEM-PROC-003 applies instead.

### MEM-PROC-002 · MEDIUM · extra processes
- **Signature:** a process attribute on any component.
- **Why:** each process is a separate runtime with its own class loading and heap — a fixed permanent
  overhead. Magnitude must be measured; the existence of the overhead is certain. Under Memory Limiter
  each process is also limited separately, which is occasionally deliberate.
- **False positives:** deliberate isolation, an embedded browser sandbox, or a 32-bit native
  dependency. Report as *justify this*.

### MEM-PROC-003 · HIGH · heavy work while not visible
- **Signature:** a bulk sync, full download, or large in-memory transform started from a foreground
  service or background worker — that is, while the UI is hidden.
- **Why:** foreground services and workers sit in the **not visible** bucket, which receives the *more
  restrictive* limit. The app gets its **tightest** budget at its **heaviest** allocation. A sync that
  passes interactively can be killed, untraceably, when backgrounded.
- **Fix:** stream and batch; keep worker peak allocation flat; measure the not-visible limit
  specifically.
- **False positives:** genuinely small background tasks.
- **Scope:** Android 17+ for the limit asymmetry.

---

## Family `MEM-PRESSURE`

### MEM-PRESSURE-001 · MEDIUM · pressure handling built on levels that no longer fire
- **Signature:** branching on `TRIM_MEMORY_RUNNING_LOW`, `TRIM_MEMORY_RUNNING_CRITICAL`,
  `TRIM_MEMORY_RUNNING_MODERATE`, `TRIM_MEMORY_MODERATE`, `TRIM_MEMORY_COMPLETE`; or an
  `onLowMemory()` override as the only pressure handling.
- **Why:** **dead code.** Not delivered since Android 14, deprecated in Android 15. Only
  `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND` still fire. High-yield precisely because the
  code *looks* diligent and releases nothing, so reviewers skip it.
- **Fix:** handle only the two delivered levels, using a threshold comparison rather than equality
  matching.
- **False positives:** **an app whose `minSdk` and fleet include Android 13 or below — the branches are
  live on those devices.** Check `minSdk` and the real fleet spread before reporting. On a Zebra fleet
  this is a genuine consideration.

### MEM-PRESSURE-002 · MEDIUM · no pressure handling at all
- **Signature:** caches or bitmaps present, no pressure callback implemented anywhere.
- **False positives:** an app with genuinely nothing releasable.

### MEM-PRESSURE-003 · MEDIUM · no device tiering
- **Signature:** no reference to `isLowRamDevice`, `memoryClass`, or `largeMemoryClass` in an app
  spanning a 1 GB and a 6–8 GB target.
- **Why:** one binary across that range with fixed cache sizes is either wasteful at the top or fatal
  at the bottom. Tier from what the device reports, never from a model string.
- **False positives:** a single-SKU deployment. Confirm the device list.

---

## Family `MEM-STATE` — surviving the kill

### MEM-STATE-001 · HIGH · workflow state held only in memory
- **Signature:** multi-step workflow state in plain view-model fields or a singleton, with no saved
  state handle and no durable write per step.
- **Why:** you cannot guarantee you will not be killed — Memory Limiter, the low-memory killer, and a
  hardware Home button all say otherwise. **Assume every backgrounding is a process death; then it is
  never a bug.** The business failure — an operator losing 40 minutes of scans — is far more expensive
  than the technical one.
- **Fix:** saved state for small UI state, which survives process death where a plain field does not;
  durable storage written **per step**, not at workflow end.
- **False positives:** genuinely transient state such as a scroll position or a filter string.

### MEM-STATE-002 · MEDIUM · oversized saved instance state
- **Signature:** lists, bitmaps, or large parcelables written to the saved-state bundle.
- **Why:** the transaction has a hard size limit, and it doubles peak at the worst moment.
- **False positives:** small primitives and short strings.

---

## Family `MEM-COMPOSE`

Most Compose memory rules are Tier A. Beyond those:

### MEM-COMPOSE-001 · HIGH · whole data set hoisted into composition state
- **Signature:** a mutable state list or state holding a full query or network result rather than a
  paged source.
- **Why:** lazy layouts virtualise *composables*, not your **data**. A large list held in state is
  fully resident regardless of how few rows are drawn.
- **False positives:** small bounded lists.

### MEM-COMPOSE-002 · MEDIUM · missing stable keys in lazy lists
- **Signature:** list items with no stable key.
- **False positives:** static, never-reordered lists.

---

## Family `MEM-DEP` — dependencies are memory

### MEM-DEP-001 · MEDIUM · reflection-based dependency injection
- **Signature:** a runtime-reflection container instead of a compile-time one.
- **False positives:** small service-locator patterns with no reflection.

### MEM-DEP-002 · MEDIUM · unnecessary heavyweight dependencies
- **Signature:** an embedded browser on a low-RAM target; analytics or crash libraries spawning their
  own thread pools; mobile-services libraries on a device that lacks them, which cannot initialise at
  all; multiple overlapping image or HTTP stacks.
- **False positives:** business-required components. Report as *justify*, not *remove*. Verify service
  availability for the specific SKU rather than assuming either way.

### MEM-DEP-003 · LOW · unwanted startup initialisers
- **Signature:** transitive initialisation providers never asked for.
- **False positives:** initialisers that are actually required.

### MEM-DEP-004 · LOW · gratuitous abstraction layers
- **Signature:** deep wrapper hierarchies with a single implementation and no seam value.
- **Why:** a larger compiled codebase directly increases resident memory.
- **False positives:** abstractions carrying real testing or platform-variation value. **Be
  conservative — informational by default**, because this rule is easily abused into bad architecture
  advice.

---

## Family `MEM-OBS` — observability

### MEM-OBS-001 · HIGH · no memory-limit kill detection
- **Signature:** no exit-info inspection anywhere in the codebase.
- **Why:** **without this you are blind to the exact failure this whole effort is about.** Memory-limit
  kills carry no stack trace; the only signal is the exit reason plus a description containing
  `MemoryLimiter:AnonSwap`. Every crash dashboard shows nothing at all.
- **Fix:** on startup, read historical process exit reasons, inspect reason and description, and report
  memory-limit kills as a first-class telemetry event.
- **False positives:** none worth honouring on a fleet app. Below API 30 the mechanism is unavailable —
  report as unsupported rather than as a defect.

### MEM-OBS-002 · MEDIUM · no leak detection in debug builds
- **Signature:** no debug-only leak-detection dependency.
- **False positives:** teams using an equivalent heap-analysis pipeline.

### MEM-OBS-003 · LOW · no profiling triggers
- **Signature:** no profiling-trigger registration on an app targeting Android 15+.
- **Why:** the out-of-memory trigger yields a heap dump at the moment of failure, and the anomaly
  trigger delivers one *just before* the system terminates the app — real-user forensics for the
  untraceable kill.
- **False positives:** fleets entirely below Android 15. Advisory only.

### MEM-OBS-004 · HIGH · no recorded baseline or budget
- **Signature:** no committed baseline artifact — no recorded device/OS/unit/state/scenario/value
  tuple, and no stated headroom target.
- **Why:** **without a baseline every other number is unanchored.** Headroom cannot be computed,
  regression cannot be detected, and the model's `B` term is unmeasurable from source by definition.
- **Fix:** run [measurement.md](measurement.md) once per device tier and commit the tuples.
- **False positives:** none. **Report this first, ahead of any BLOCKER**, because it gates the
  credibility of the whole report.

### MEM-OBS-005 · MEDIUM · no memory regression gate
- **Signature:** no memory metric in continuous integration; no stored baseline to compare against.
- **Why:** growth between releases is otherwise completely unobservable — you learn about it from field
  kills, months later, with no stack trace. Track RSS, not PSS.
- **False positives:** an equivalent field-telemetry pipeline already trending per release. Emulator
  CI is acceptable *for deltas only* — say so rather than implying it validates headroom.

---

Costing method: [quantification.md](quantification.md). Ceilings and units:
[api-patterns.md](api-patterns.md). Unsafe assessment practices:
[anti-patterns.md](anti-patterns.md). Provenance: [sources.md](sources.md).
