# Migration steps — the executable catalogue

Ten steps. Each is a change that can be applied and verified **in one sitting**; anything larger is
architectural work needing its own plan, and this catalogue says so rather than pretending otherwise.

Each step states: what it changes, when it applies, the expected effect and which ceiling it targets,
the reversal, the verification, and what must not change.

Ordering guidance: apply in **derived-magnitude order** from the plan, not in step-number order.
STEP-01 and STEP-02 are usually the largest wins because their cost scales with customer data.

| Step | Change | Targets | Version gate |
|---|---|---|---|
| STEP-01 | Stream a whole-response parse into the database | anon → file-backed | none |
| STEP-02 | Bound a cache, or move it to the database | anon → file-backed | none |
| STEP-03 | Downsample a bitmap decode | Java heap + anon | `ImageDecoder` API 28+ |
| STEP-04 | Scope a lifecycle registration | accumulation | none |
| STEP-05 | Rewrite pressure handling to the delivered levels | pressure response | **fleet floor** |
| STEP-06 | Detect memory-limit kills | observability | **API 30+** |
| STEP-07 | Cap and tier an image loader cache | anon | none |
| STEP-08 | Enable R8 fully, narrow keep rules | code footprint | AGP |
| STEP-09 | Remove `largeHeap` | diagnosability | none |
| STEP-10 | Add a memory regression gate | future growth | none |

---

## STEP-01 · Stream a whole-response parse into the database

- **Changes:** the sync or download path. Replaces read-body-to-string then parse with a streaming
  parse writing batches inside a transaction.
- **Applies when:** the payload scales with customer data. **Not** for a login response, a
  configuration blob, or a single record — those are false positives.
- **Expected effect:** peak becomes proportional to the batch rather than the response. Removes the
  simultaneous residency of the raw string, the parser's intermediate structures, the transfer
  objects, and often a second mapped graph. Targets anonymous memory and the memory-limit ceiling.
- **Pattern:** `../assets/code-patterns/streaming-json-to-room/`
- **Must not change:** the stored result. The same rows, the same values. This is the step most likely
  to alter behaviour accidentally.
- **Reversal:** revert the commit. No schema change unless a batch index was added, which should be a
  separate step.
- **Verify:** `RssAnon` peak during the sync, in the not-visible state, before and after.
- **Watch for:** failure timing. A partial parse can leave partial data — wrap batches in a
  transaction so failure is all-or-nothing. Keep the work off the main thread using the dispatcher
  already in use.

## STEP-02 · Bound a cache, or move it to the database

- **Changes:** an unbounded long-lived collection. Either a bounded cache with an explicitly computed
  size, or removal in favour of querying the database.
- **Applies when:** the upper bound comes from **customer data**, not from code. A seven-entry status
  lookup is not this step.
- **Expected effect:** removes a permanently resident anonymous structure whose size scales with the
  customer. **Moving it to the database also converts anonymous bytes into clean file-backed bytes**,
  which the kernel can drop for free — usually a bigger win than shrinking the map, and often a net
  simplification.
- **Pattern:** `../assets/code-patterns/bounded-cache/`
- **Must not change:** lookup results. Check first whether any code depended on the cache holding
  *everything* — an iteration over all values, or a size check.
- **Reversal:** revert the commit.
- **Verify:** steady-state `RssAnon`, and the derived per-row cost from the plan multiplied by the real
  record count.
- **Decision needed from the developer:** the cache size, or that the database is queried directly.
  Do not choose a size unilaterally.

## STEP-03 · Downsample a bitmap decode

- **Changes:** a decode call. Adds a bounds pass and a sampled decode, and `RGB_565` where
  transparency is not needed.
- **Applies when:** the image source is camera, gallery, file, or network. **Not** for a known-small
  bundled asset.
- **Expected effect:** a 12 MP image at ARGB_8888 is 48 MB; at display size roughly 3.5 MB. Targets
  both the Java heap and anonymous memory.
- **Pattern:** `../assets/code-patterns/sampled-bitmap-decode/`
- **Must not change:** visible image quality at the size actually displayed. Keep ARGB_8888 wherever
  alpha is genuinely used.
- **Reversal:** revert the commit.
- **Verify:** peak during the image operation; graphics or native lines in the memory breakdown.
- **Watch for:** code downstream that assumed the full-resolution dimensions.

## STEP-04 · Scope a lifecycle registration

- **Changes:** moves a registration from a create method to a start method and adds the matching
  removal in the stop method; or converts to a lifecycle-aware observer, or a disposable effect in
  Compose.
- **Applies when:** a registration has no symmetric removal. Weight scanner and device-management
  receivers highest — a receiver registered once and never removed holds the activity for the life of
  a shift-long process.
- **Expected effect:** removes accumulation. Targets the session-growth axis, not steady state.
- **Pattern:** `../assets/code-patterns/lifecycle-scoped-registration/`
- **Must not change:** whether events are received while the screen is actually in use. **This is the
  step most likely to break behaviour:** if the app legitimately needs events while stopped, scoping to
  start and stop will drop them. Confirm the requirement before applying.
- **Reversal:** revert the commit.
- **Verify:** repeat the workflow 20 to 50 times and confirm the `RssAnon` slope flattens; confirm the
  activity count in the memory breakdown returns to the number actually open.
- **Watch for:** removal running twice, or throwing during teardown. Make it idempotent.

## STEP-05 · Rewrite pressure handling to the delivered levels

- **Changes:** replaces a `when` block over legacy trim constants with threshold comparisons on the two
  levels still delivered.
- **Applies when:** **the fleet floor is Android 14 or higher.** Below that the legacy branches are
  live and doing real work.
- **Expected effect:** pressure handling that actually runs on modern devices. Modest but free.
- **Pattern:** `../assets/code-patterns/trim-memory-handler/`
- **Must not change:** what gets released. Preserve the existing release logic; only the dispatch
  changes.
- **Reversal:** revert the commit.
- **Verify:** instrument the release path and confirm it is invoked when the app is backgrounded.
- **Stop condition:** if `minSdk` or the fleet spread is unknown, **stop.** Removing these branches
  from a fleet that includes Android 12 or 13 deletes working code. When the floor is below 14, keep
  both shapes rather than choosing.

## STEP-06 · Detect memory-limit kills

- **Changes:** adds an exit-reason inspection at startup and reports memory-limit kills to telemetry.
- **Applies when:** API 30 or higher is available. Guard the block.
- **Expected effect:** no memory reduction at all — this step buys **visibility** into a failure that
  is otherwise completely invisible, because these kills carry no stack trace. Report it as
  observability, never as a saving.
- **Pattern:** `../assets/code-patterns/exit-info-memory-limit/`
- **Must not change:** startup latency. Read off the main thread.
- **Reversal:** revert the commit.
- **Verify:** force a kill with the memory-limiter manual override, relaunch, and confirm the event is
  reported once.
- **Watch for:** double-counting. De-duplicate by timestamp.

## STEP-07 · Cap and tier an image loader cache

- **Changes:** sets an explicit memory-cache size, tiered on what the device reports, and caps the disk
  cache.
- **Applies when:** an image loader is configured with defaults, which are a percentage of available
  heap calibrated for consumer phones.
- **Expected effect:** reclaims a share of heap the workflow needed. Targets anonymous memory.
- **Pattern:** `../assets/code-patterns/tiered-image-loader/`
- **Must not change:** which images are available; only how many stay cached.
- **Reversal:** revert the commit.
- **Verify:** steady-state `RssAnon` after browsing an image-heavy screen.
- **Decision needed:** the size per tier. Propose, do not assume.

## STEP-08 · Enable R8 fully and narrow keep rules

- **Changes:** build configuration only. Enables minification and resource shrinking, switches to the
  optimising configuration file, removes the full-mode opt-out, and narrows package-wide keep
  wildcards to specific classes.
- **Applies when:** a release build type has these disabled. **Not** for debug variants.
- **Expected effect:** code footprint is resident memory, not only download size.
- **Pattern:** none needed — build-file change. Narrow each wildcard to the specific reflected classes
  and record why each remains.
- **Must not change:** runtime behaviour. **This is the step most likely to break the build or fail at
  runtime**, because reflection-driven dependencies need their keep rules. Apply it alone, build, and
  run the app before believing it.
- **Reversal:** revert the build files.
- **Verify:** a clean release build, then exercise every reflection-dependent path — serialisation,
  vendor libraries, dependency injection.
- **Stop condition:** if narrowing a wildcard cannot be validated by a test or a manual pass, keep the
  wildcard and record the reason. A broken release is worse than a larger one.

## STEP-09 · Remove `largeHeap`

- **Changes:** deletes the manifest attribute.
- **Applies when:** it is present. It almost always was added to silence an error rather than fix one.
- **Expected effect:** restores diagnosability. On Android 17 a larger heap reaches the cgroup limit
  **sooner**, so `largeHeap` converts a reproducible, stack-traced error into an untraceable kill.
- **Pattern:** none needed — manifest change.
- **Must not change:** anything else in the manifest.
- **Reversal:** restore the attribute.
- **Verify:** the app runs the previously failing workflow **after** the allocation fix that justifies
  removal.
- **Stop condition:** **do not remove it before fixing the allocation it was masking.** Sequence it
  after STEP-01, STEP-02, or STEP-03, or the app will simply fail sooner. This ordering matters more
  than the step itself.

## STEP-10 · Add a memory regression gate

- **Changes:** adds a benchmark that records a memory metric in continuous integration, plus a
  committed baseline.
- **Applies when:** growth between releases is currently unobservable — the normal state.
- **Expected effect:** no reduction now; prevents silent future growth. Track RSS, not PSS: RSS is
  better for tracking allocation changes and is cheaper to compute.
- **Pattern:** `../assets/code-patterns/memory-regression-gate/`
- **Must not change:** existing test behaviour or build duration materially.
- **Reversal:** remove the benchmark module and the baseline file.
- **Verify:** the gate fails on a deliberately introduced regression, and passes on an unchanged tree.
- **Watch for:** device variance. Absolute values are not portable across devices; only **deltas on one
  device** are a valid signal. Emulator CI is acceptable for deltas, and must be labelled as not
  validating headroom.

---

Platform behaviour and version gates: [api-patterns.md](api-patterns.md). Per-step verification
procedure: [usage-patterns.md](usage-patterns.md). Changes to refuse:
[anti-patterns.md](anti-patterns.md).
