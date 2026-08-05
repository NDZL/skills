# Troubleshooting — when a step does not land

Symptom-first. Each entry gives the evidence to collect, the likely cause, the corrective action,
verification, and when to stop or escalate.

---

## T1 · The measured delta is zero, or the wrong sign

**Evidence:** both tuples — device, RAM tier, OS build, unit, process state, scenario — for the before
and after samples, side by side.

**Likely causes, in order of frequency**

| Cause | Check |
|---|---|
| Different unit before and after | Was one Java heap and the other RSS? That delta is meaningless — anti-pattern AP-06 |
| Different process state | Was the before sample interactive and the after sample not-visible, or vice versa? |
| Different scenario | Did the same workload run both times? |
| Warm-up not discarded | A first-run sample includes compilation and lazy initialisation |
| The step targets a different ceiling | STEP-05 and STEP-06 change kill probability and visibility, not footprint. A zero delta is the **expected** result |
| Single sample of an accumulating quantity | One reading cannot show a slope |

**Corrective action:** re-measure with the procedure in [usage-patterns.md](usage-patterns.md), holding
everything constant except the change.

**Stop condition:** if the step was STEP-05, STEP-06, or STEP-10, stop looking for a footprint delta —
report the outcome those steps actually deliver.

---

## T2 · Memory improved but behaviour broke

**Evidence:** what changed functionally, and which step was applied.

**Likely causes by step**

| Step | Typical break |
|---|---|
| STEP-02 | Code depended on the cache holding *everything* — an iteration over all values, or a size check |
| STEP-03 | Downstream code assumed full-resolution dimensions |
| STEP-04 | The app legitimately needed events while stopped, and scoping dropped them |
| STEP-01 | A partial parse left partial data because batches were not in a transaction |
| STEP-08 | A reflection-dependent path lost its keep rule |

**Corrective action:** revert the step — it is a single reversible unit, which is why the contract
requires that. Then re-propose with the behaviour requirement stated, and pick the variant that
preserves it.

**Escalate when:** preserving behaviour and reducing memory genuinely conflict. That is a product
decision, not an engineering one, and belongs to the developer.

---

## T3 · The build fails after STEP-08

**Evidence:** the build output, and whether the failure is at build time or first run.

**Interpretation:** build-time failures are usually a missing keep rule for a reflected class.
Run-time failures after a clean build are the dangerous case, because R8 removed something only
reflection referenced.

**Corrective action:** restore the previous wildcard, confirm the build, then narrow it one class at a
time, validating between each. Record why every remaining wildcard stays.

**Stop condition:** if a narrowed rule cannot be validated by a test or a manual pass, **keep the
wildcard.** A broken release is worse than a larger one.

---

## T4 · The app now fails sooner after removing `largeHeap`

**Evidence:** the order in which steps were applied.

**Interpretation:** STEP-09 was applied before the allocation fix it was masking — anti-pattern AP-02.
Removing the flag restores diagnosability; it does not reduce demand.

**Corrective action:** restore the attribute, apply STEP-01, STEP-02, or STEP-03 first, verify the
reduction, then remove it again.

---

## T5 · Kills continue after the plan is fully applied

**Evidence:** re-read the exit reasons; re-measure in the **not-visible** state against the
*not-visible* ceiling; confirm which tier the failing devices are.

**Likely causes**

- Verification used the interactive peak and the generous ceiling, so the binding constraint was never
  addressed — the heavy work still runs under the tighter limit.
- The failing devices are a lower tier than the one measured.
- The remaining consumer is another resident process, not this app.
- The dominant term was accumulation, and no step targeted it.

**Corrective action:** re-verify in the binding state on the binding tier. If this app is not the
largest consumer, report that rather than optimising further.

**Escalate when:** the device-level total is dominated by other resident software. That is a fleet
configuration matter.

---

## T6 · A step turns out to be larger than described

**Evidence:** the files the step actually requires versus the files proposed.

**Interpretation:** it is architectural work, not a fix — anti-pattern AP-08.

**Corrective action:** **stop and revert.** Report what the step really requires and let the developer
decide whether to schedule it. Do not finish a redesign under a fix-level approval.

---

## T7 · No device is available to verify anything

**Evidence:** none obtainable.

**Interpretation:** not a failure. Code steps still apply, and the reasoning behind them is unchanged.

**Corrective action:** apply approved steps, report each as applied with the improvement
**unmeasured**, and record the exact commands that would verify them later. Cap the achieved
validation level at `Build and behavior`.

**Stop condition:** if the developer requires a *verified* improvement, stop before applying and say
that a device is needed. Do not apply and then describe an expected figure as achieved.

---

Version gates: [api-patterns.md](api-patterns.md). Step recipes and stop conditions:
[migration-steps.md](migration-steps.md). Changes to refuse:
[anti-patterns.md](anti-patterns.md).
