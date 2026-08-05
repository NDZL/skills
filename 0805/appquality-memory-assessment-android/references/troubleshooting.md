# Troubleshooting — from symptom to breached ceiling

Use this for `diagnose` mode. Start from what was observed, identify **which ceiling was breached**,
then hand the responsible rule family to the plan. Naming the ceiling correctly is most of the work,
because the three deaths look similar in a support ticket and have different fixes.

---

## S1 · The app closes with no crash and no stack trace

**Evidence to collect**

```
adb shell dumpsys activity exit-info <package>
```
In-app, read `ApplicationExitInfo.getReason()` and `getDescription()` for recent exits.

**Interpretation**

| Finding | Cause | Route |
|---|---|---|
| `REASON_OTHER` with description containing `MemoryLimiter:AnonSwap` | Android 17+ memory-limit kill | Anonymous memory is too high — `MEM-CACHE`, `MEM-DATA`, `MEM-BITMAP` |
| Reason indicates low-memory kill | The system reclaimed the process under pressure | Often a victim of device-wide pressure; check total footprint and background residency |
| No exit info available | Below API 30, or history rotated | Add the check first; without it this symptom is undiagnosable |

**Stop condition:** on Android 16 or below, Memory Limiter does not exist — do not attribute the kill
to it. Look at the low-memory killer and at total device pressure instead.

**Verification after a fix:** reproduce deliberately with `am memory-limiter manual <pid> <limit>`,
then confirm the exit no longer occurs at the previous limit.

---

## S2 · `OutOfMemoryError` in the crash reporter

**Evidence:** the stack trace and the allocation at the top of it; `getprop
dalvik.vm.heapgrowthlimit`; the Java heap line from `dumpsys meminfo <package>`.

**Interpretation:** this is the **Java heap** ceiling, not the Memory Limiter one. A large bitmap or a
large collection is usually visible directly in the trace.

| Allocation site | Route |
|---|---|
| Bitmap decode | `MEM-BITMAP-001` — decode without downsampling; a single 12 MP image is 48 MB at ARGB_8888 |
| Collection growth or map insertion | `MEM-CACHE-001` — unbounded cache |
| String or byte array from a response | `MEM-DATA-001` — whole-response deserialisation |

**Anti-pattern to refuse:** adding `largeHeap` to make the error disappear. It converts a
reproducible, stack-traced `OutOfMemoryError` into an untraceable Memory Limiter kill, which is
strictly harder to diagnose.

---

## S3 · The app degrades or dies later in a shift

**Evidence:** sample `RssAnon` from `/proc/<pid>/status` across a repeated workflow — at least 20 to
50 iterations, discarding the first few for warm-up. Fit a line.

**Interpretation:** a non-zero slope is accumulation. Express it **per business transaction**, not per
hour: memory per pick, per scan, per label. Then

```
transactions-to-kill = (ceiling − steady state) / per-transaction cost
```

**Why per transaction:** accumulation tracks work done, not the clock. This is stable across
customers where a per-hour figure is not, and it explains the observation that mystifies support
teams — **the app fails first for the fastest operators**, because they reach the ceiling soonest.

**Route:** `MEM-LIFECYCLE` family first, then `MEM-CACHE`.

**Stop condition:** with no iteration harness available, report the leak *risk* from the static
findings and state that the rate is unmeasured. Do not estimate a rate.

---

## S4 · Scanning stops working, or the whole device slows down

**Evidence:** `adb shell dumpsys meminfo | head -40` — what is resident *before* the app starts;
`/proc/meminfo` `MemAvailable`.

**Interpretation:** this is device-level pressure, not necessarily your app's ceiling. Zebra
documents that "when a device is low in memory, DataWedge may not function properly", and its
prescribed remedy is to fix the leak or uninstall the offending application. So a memory-hungry app
degrades the **business function** device-wide, for every app.

**Route:** total footprint and background residency — `MEM-PROC` (long-running services, extra
processes) and `MEM-CACHE`.

**Escalate when:** another resident application or agent is the larger consumer. Report that finding
rather than optimising the wrong process.

---

## S5 · Works in development, fails at the customer

**Evidence:** the record counts of the main data sets in the failing environment versus the
development fixture.

**Interpretation:** almost always a scaling term. A cache costing ~166 B/row is 0.8 MB against a
5 000-row fixture and 332 MB at two million rows — a 400× difference that no development test will
surface.

**Route:** `MEM-CACHE-001`, `MEM-DATA-002`. Derive the scaling law, then report the crossing point in
rows.

**Stop condition:** if production record counts are unavailable, emit the crossing point rather than
assuming a count.

---

## S6 · Headroom looks fine but kills still happen

**Evidence:** re-measure in the **not-visible** state — press Home while a sync or worker runs — and
compare against the *not-visible* ceiling from `am memory-limiter status`.

**Interpretation:** the earlier measurement was against the wrong ceiling and the wrong state. Heavy
work in a foreground service or worker runs under the more restrictive limit.

**Route:** `MEM-PROC-003`, plus `MEM-DATA-001` if the worker parses whole payloads.

---

## S7 · The report's numbers do not match a measurement

**Evidence:** the model's stated tolerance and calibration state; a heap dump for the disputed
structure.

**Interpretation:** expected, and not a failure, if the figure was labelled a model. The sizing model
is accurate to roughly a factor of two on absolute bytes and exact on the scaling exponent.

**Corrective action:** calibrate the per-record constant against one heap dump and keep the ratio.
After calibration the model is trustworthy for that codebase.

**Escalate when:** the discrepancy exceeds the stated tolerance — that indicates a missed term such
as a nested collection, a customer-controlled string length, or duplicated rather than shared string
instances.

---

Detection signatures: [rule-catalogue.md](rule-catalogue.md). Costing method:
[quantification.md](quantification.md). Commands: [measurement.md](measurement.md). Unsafe assessment
practices: [anti-patterns.md](anti-patterns.md).
