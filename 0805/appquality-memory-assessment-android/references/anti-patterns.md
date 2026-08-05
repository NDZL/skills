# Anti-patterns — unsafe assessment practices

These are ways an *assessment* goes wrong. They are in scope because a confidently wrong memory
report is worse than no report: it redirects engineering effort and destroys trust in every later
number. Unsafe *code* patterns belong to the rule catalogue and to the migration skill.

Each entry states the tempting design, its exposure condition, the invariant it violates, the
observable failure, the supported alternative, the protecting evaluation, and its scope.

---

## AP-01 · Inventing a threshold

- **Tempting design:** quoting a memory limit for a device tier, e.g. "the limit on a 3 GB device is
  about 400 MB", to make headroom computable.
- **Exposure:** any assessment where no device was available, or where the vendor configuration was
  not read.
- **Invariant violated:** provenance. No published per-device memory threshold exists for any Zebra
  RAM tier; the enforced values live in the device's vendor partition and are the vendor's to set.
- **Observable failure:** headroom is computed against a fabricated ceiling, so every band and
  forecast derived from it is wrong by an unknown factor, while looking precise.
- **Alternative:** report the ceiling as UNKNOWN and emit the command that reads it. Present
  scaling-only results, which are still useful.
- **Protecting evaluation:** `UNSUP-001`, `OUT-EST-002`.
- **Scope:** all versions; acute on Android 17+ where a real enforced limit exists to be read.

## AP-02 · Mixing units

- **Tempting design:** comparing a Java heap figure, or a `dumpsys meminfo` TOTAL PSS figure, against
  an Android 17 cgroup ceiling.
- **Exposure:** any report that quotes one number and one limit without naming both units.
- **Invariant violated:** unit consistency. Java heap governs `OutOfMemoryError`; cgroup anon+swap
  governs `MemoryLimiter:AnonSwap`; PSS is proportional attribution and enforces nothing.
- **Observable failure:** an app declared safe at "40 % of the limit" is killed in the field, because
  the measured quantity was never the enforced one.
- **Alternative:** state unit and ceiling together on every number. Track `RssAnon` for Memory
  Limiter, Java heap for `OutOfMemoryError`.
- **Protecting evaluation:** `UNIT-001`.
- **Scope:** Memory Limiter comparisons are Android 17+; the PSS-versus-RSS distinction is universal.

## AP-03 · Reporting the flattering peak

- **Tempting design:** measuring peak usage while the app is in the foreground on the newest device
  in the fleet.
- **Exposure:** any app that syncs, downloads, or transforms data from a foreground service or a
  background worker.
- **Invariant violated:** the binding constraint must be the one reported. Not-visible processes
  receive the *more restrictive* limit, and the lowest fleet tier has the smallest ceiling.
- **Observable failure:** green headroom in the report; untraceable kills in the warehouse during
  overnight sync.
- **Alternative:** measure the not-visible peak against the not-visible ceiling on the lowest tier,
  and name the binding state explicitly.
- **Protecting evaluation:** `OUT-EST-001`.
- **Scope:** state-dependent limits are Android 17+; the fleet-tier point is universal.

## AP-04 · Precision theatre

- **Tempting design:** presenting a derived model figure as a measurement, e.g. "uses 33 MB" instead
  of "≈166 B/row model, uncalibrated; 33 MB at 200 000 rows".
- **Exposure:** any static-only assessment.
- **Invariant violated:** claims must be labelled by kind — measured, derived, or invented.
- **Observable failure:** an engineer measures, finds 60 MB, and discards the whole report as
  unreliable — including the parts that were correct.
- **Alternative:** label models as models, show parameters, state tolerance and the likely direction
  of error, and name the calibration that would tighten it.
- **Protecting evaluation:** `FIXTURE-001`, `OUT-EST-002`.
- **Scope:** all.

## AP-05 · Flagging bounded data as unbounded

- **Tempting design:** reporting every long-lived collection as an unbounded-cache defect.
- **Exposure:** codebases with legitimate small lookup tables — status codes, country lists, unit
  conversions.
- **Invariant violated:** precision. The test is whether the upper bound is set by *our code* or by
  *the customer's data*; only the latter is a defect.
- **Observable failure:** a noisy report; the team disables the check and the real unbounded cache
  survives.
- **Alternative:** apply the bound test per finding, and report genuinely ambiguous cases as
  "justify this" rather than as defects.
- **Protecting evaluation:** `NEG-FP-001`.
- **Scope:** all.

## AP-06 · Applying a version-gated rule outside its version

- **Tempting design:** flagging legacy `onTrimMemory` branches as dead code without checking
  `minSdk`, or attributing a kill to Memory Limiter on Android 13.
- **Exposure:** any Zebra fleet, which commonly spans Android 11 through 19 simultaneously.
- **Invariant violated:** every rule carries a version scope.
- **Observable failure:** a developer removes branches that are live on the Android 12 devices still
  in service, losing real pressure handling.
- **Alternative:** read `minSdk` and the fleet OS spread first; gate each rule; state the gate in the
  finding.
- **Protecting evaluation:** `NEG-FP-002`.
- **Scope:** all.

## AP-07 · Editing the target project

- **Tempting design:** fixing an obvious one-line defect while assessing, because it is quicker.
- **Exposure:** any assessment run where the agent also has write access.
- **Invariant violated:** this capability is read-only. Assessment and mutation have different risk
  postures and different approval requirements, which is why they are separate skills.
- **Observable failure:** an unreviewed change lands with no plan entry, no approval, and no
  before/after measurement, so its effect cannot be attributed.
- **Alternative:** record it in `memory-plan.md` and hand off to
  `appquality-memory-migration-android`.
- **Protecting evaluation:** `READONLY-001`.
- **Scope:** all.

## AP-08 · Treating a vendor-owned finding as the developer's defect

- **Tempting design:** reporting unaligned native libraries or memory use inside a third-party
  archive as an action item for this codebase.
- **Exposure:** projects depending on device-vendor or device-management archives that contain native
  code the developer cannot recompile.
- **Invariant violated:** findings must be routed to whoever can act on them.
- **Observable failure:** an unfixable item sits at the top of the backlog and the report loses
  credibility.
- **Alternative:** route it as "raise with the vendor", separately from the developer's fix list.
- **Protecting evaluation:** `UNSUP-001`.
- **Scope:** all; 16 KB alignment specifically from Android 15+.

---

Symptom-first guidance: [troubleshooting.md](troubleshooting.md). Detection signatures:
[rule-catalogue.md](rule-catalogue.md). Provenance: [sources.md](sources.md).
