# Anti-patterns — changes to refuse

A skill that writes to a working codebase needs a clear list of things it will not do. Each entry
states the tempting change, its exposure condition, the invariant it violates, the observable failure,
the supported alternative, the protecting evaluation, and its scope.

---

## AP-01 · Adding `largeHeap` to make an error go away

- **Tempting change:** set `android:largeHeap="true"` when `OutOfMemoryError` appears.
- **Exposure:** any project hitting the Java heap ceiling, especially with bitmap or bulk-data work.
- **Invariant violated:** a fix must address the allocation, not raise the ceiling. On Android 17 a
  larger heap reaches the cgroup limit **sooner**.
- **Observable failure:** a reproducible, stack-traced error becomes an untraceable
  `MemoryLimiter:AnonSwap` kill with no crash report at all. Strictly harder to diagnose than what it
  replaced.
- **Alternative:** STEP-01, STEP-02, or STEP-03. Then STEP-09 removes the flag.
- **Protecting evaluation:** `ANTI-001`.
- **Scope:** all versions; acute on Android 17+.

## AP-02 · Removing `largeHeap` before fixing the allocation

- **Tempting change:** apply STEP-09 first because it is a one-line change.
- **Exposure:** a project where `largeHeap` is currently masking a real allocation defect.
- **Invariant violated:** step ordering. STEP-09 restores diagnosability; it does not reduce demand.
- **Observable failure:** the app fails sooner and more often, and the migration is blamed for a
  regression it merely exposed.
- **Alternative:** sequence STEP-09 after the allocation fix, and say why when proposing it.
- **Protecting evaluation:** `ORDER-001`.
- **Scope:** all.

## AP-03 · Applying steps in bulk

- **Tempting change:** apply the whole plan in one pass because the developer approved the plan.
- **Exposure:** any plan with more than one entry; strongest when the developer sounds impatient.
- **Invariant violated:** one step per change, with step-level approval. Plan approval is not step
  approval.
- **Observable failure:** memory changes by an unknown amount for unknown reasons; a behaviour
  regression cannot be attributed to a step; reverting means losing every improvement together.
- **Alternative:** the migration loop — propose one, approve one, apply one, verify one.
- **Protecting evaluation:** `APPROVAL-001`.
- **Scope:** all.

## AP-04 · Opportunistic edits while in a file

- **Tempting change:** reformat, rename, upgrade a dependency, or fix an unrelated defect while
  applying a step.
- **Exposure:** every code step.
- **Invariant violated:** attribution. The step's effect must be isolated.
- **Observable failure:** the diff is unreviewable, the measured delta cannot be attributed, and an
  unrelated regression is introduced under a memory heading. A dependency upgrade is itself memory.
- **Alternative:** apply only the step. Record anything else as a separate proposal.
- **Protecting evaluation:** `SCOPE-001`.
- **Scope:** all.

## AP-05 · Reporting an expected improvement as an achieved one

- **Tempting change:** state that the step reduced memory by the plan's derived figure after applying
  it without measuring.
- **Exposure:** any environment without a device — the common case.
- **Invariant violated:** claims are labelled by kind. A derived projection is not a measurement.
- **Observable failure:** the team believes a problem is solved, ships, and the field kills continue.
  Every later number from this skill is then distrusted.
- **Alternative:** report the change as applied and the improvement as unmeasured, and name the
  measurement that would confirm it.
- **Protecting evaluation:** `VERIFY-001`.
- **Scope:** all.

## AP-06 · Verifying in a different unit from the baseline

- **Tempting change:** take a before figure from one source and an after figure from another — Java
  heap before, RSS after, or PSS against a cgroup ceiling.
- **Exposure:** any verification where the before and after were collected differently.
- **Invariant violated:** unit consistency. The three ceilings are measured in incompatible units.
- **Observable failure:** a fabricated delta, sometimes with the wrong sign, presented as proof.
- **Alternative:** same unit, same state, same device, same scenario. `RssAnon` for memory-limit work.
- **Protecting evaluation:** `VERIFY-001`, `ANTI-002`.
- **Scope:** all; Memory Limiter comparisons are Android 17+.

## AP-07 · Deleting legacy pressure branches without checking the fleet floor

- **Tempting change:** apply STEP-05 by removing the legacy trim branches because they are deprecated.
- **Exposure:** any fleet with a floor below Android 14 — normal for a Zebra estate spanning Android 11
  to 19.
- **Invariant violated:** every version-gated step must be gated in fact, not just in documentation.
- **Observable failure:** working pressure handling is deleted for part of the fleet, so those devices
  release nothing and become more likely to be killed. A memory step that *increases* kills.
- **Alternative:** read `minSdk` and the fleet spread first. Below 14, keep both shapes.
- **Protecting evaluation:** `GATE-001`.
- **Scope:** Android 14 and 15 behaviour changes.

## AP-08 · Finishing a refactor that was approved as a fix

- **Tempting change:** discovering mid-edit that the step needs architectural change, and completing it
  anyway because stopping feels wasteful.
- **Exposure:** STEP-01 and STEP-02 in codebases where the data layer is entangled with the UI.
- **Invariant violated:** a step must be completable and verifiable in one sitting; approval covered a
  fix, not a redesign.
- **Observable failure:** a large unreviewed change lands under a small approval, and cannot be cleanly
  reverted.
- **Alternative:** stop, revert, report what the step actually requires, and let the developer decide.
- **Protecting evaluation:** `SCOPE-001`.
- **Scope:** all.

## AP-09 · Changing behaviour to save memory

- **Tempting change:** narrow a projection by dropping a field something reads; bound a cache the code
  expected to hold everything; downsample an image whose full resolution is needed downstream.
- **Exposure:** STEP-02, STEP-03, and the projection part of data work.
- **Invariant violated:** behaviour preservation. A memory fix that changes results is a defect.
- **Observable failure:** correct-looking memory numbers and a functional regression — the worst
  possible trade in an enterprise workflow.
- **Alternative:** check for dependencies on completeness or resolution before applying; where a real
  behaviour decision exists, ask.
- **Protecting evaluation:** `BEHAVIOUR-001`.
- **Scope:** all.

## AP-10 · Adding a dependency to save memory

- **Tempting change:** introduce a streaming parser, an image library, or a caching library that the
  project does not already have.
- **Exposure:** STEP-01, STEP-03, STEP-07 where the needed library is absent.
- **Invariant violated:** a dependency is itself memory — archive bytes, classes to load, and often an
  uninvited startup initialiser.
- **Observable failure:** the step's saving is partly or wholly offset, and the developer never agreed
  to the new dependency.
- **Alternative:** prefer platform APIs already available; if a library is genuinely required, propose
  it as its own approval with its own cost stated.
- **Protecting evaluation:** `APPROVAL-001`.
- **Scope:** all.

---

Step recipes and their stop conditions: [migration-steps.md](migration-steps.md). Version gates:
[api-patterns.md](api-patterns.md). What to do when a step made things worse:
[troubleshooting.md](troubleshooting.md).
