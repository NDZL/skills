# Test fixtures

Synthetic before-and-after pairs and refusal cases, used by `evals/evals.json`. All original to this
skill; no customer or third-party code.

The interesting fixtures here are the ones where the correct action is **to refuse or to reorder**,
not to apply. A migration skill that only proves it can edit code has not been tested on the part
that matters.

## `step-01-streaming/` — a normal apply-and-verify pair

| File | Role |
|---|---|
| `before.kt` | Whole-response parse: raw string, parser structures, and two object graphs live at once |
| `after.kt` | Streaming parse writing batches in a transaction |

Expected: `after.kt` stores exactly the same rows as `before.kt`. Behaviour preservation is the
assertion, not the memory figure — the memory figure needs a device.

## `step-05-trim-gate/` — the version-gate refusal

| File | Role |
|---|---|
| `MixedFleetApp.kt` | Legacy trim branches, with `minSdk 24` declared in the header comment |

Expected: the skill **refuses to delete the legacy branches** and either keeps both shapes or stops.
Deleting them would remove working pressure handling from the Android 12 and 13 devices in the fleet,
making those devices *more* likely to be killed — a memory step that increases kills. Protects
`GATE-001` and anti-pattern AP-07.

## `step-09-ordering/` — the sequencing refusal

| File | Role |
|---|---|
| `AndroidManifest.xml` | Manifest carrying `android:largeHeap="true"` |
| `UnfixedDecode.kt` | The unsampled decode that the flag is currently masking |

Expected: the skill **refuses to remove `largeHeap` first** and sequences the decode fix ahead of it.
Removing the flag while the allocation is unfixed makes the app fail sooner, and the migration gets
blamed for a regression it merely exposed. Protects `ORDER-001` and anti-pattern AP-02.

## `step-02-behaviour/` — the behaviour-preservation refusal

| File | Role |
|---|---|
| `CacheWithFullScan.kt` | An unbounded cache whose caller iterates over **all** values |

Expected: the skill notices that the caller depends on the cache holding everything, and does **not**
silently bound it. Bounding it here would produce correct-looking memory numbers and a functional
regression — the worst trade available in an enterprise workflow. Protects `BEHAVIOUR-001` and
anti-pattern AP-09.

## Verifying

These fixtures are read, not built. This package has **not** been compiled in this authoring pass, so
the achieved validation level for all bundled material is `Inspection`. Assertions are about the
skill's decisions — apply, refuse, or reorder — rather than about compiler output.
