# Quantification — deriving current and future footprint

The governing rule: **never state a quantity without either a measurement or a shown derivation.**
Deriving is not inventing. A source tree does not reveal your footprint, but it very often fully
determines your **scaling function** — and a scaling law plus one measurement is a forecast.

## 1. The footprint model

```
F(n, t)  =  B  +  Σ (nᵢ × sᵢ)  +  max(P₁ … P_k)  +  L × t
            │      │                │                 │
            │      │                │                 └─ accumulation: rate × work done
            │      │                └─ transient peaks: take the MAX; they rarely coincide
            │      └─ scaling terms: record count × bytes-per-record
            └─ fixed baseline: ART runtime, code, resources, framework, view tree
```

| Term | Source-derivable? | How obtained |
|---|---|---|
| `B` baseline | **No** | Measure once per device tier ([measurement.md](measurement.md)) |
| `sᵢ` bytes per record | **Yes, fully** | §2 below, from the declaration |
| `nᵢ` record count | **No — it is the customer's** | Ask, or read the largest production data set |
| `Pⱼ` transient peaks | **Mostly** | Bitmap dimensions × config; payload size × parse multiplier |
| `L` accumulation rate | **No** | Iteration protocol ([measurement.md](measurement.md)) |

So static analysis derives the two terms that **scale**, and needs two measurements (`B`, `L`) plus
one business input (`nᵢ`) to close the model. That is a small, achievable ask.

**On `max()` versus `Σ` for peaks:** a 48 MB bitmap decode and a 30 MB payload parse that never occur
together do **not** sum. Summing them is how memory reports become alarmist and get ignored.
Conversely, if they *can* overlap — a photo upload during a background sync — they do sum, and that
overlap is a source-derivable fact about the app's concurrency. Justify which you used.

## 2. Deriving `sᵢ` — object sizing

A data class's memory cost is fully determined by its declaration. Model assumptions:

| Element | Bytes |
|---|---|
| Object header | 8 (class pointer + lock word) |
| Array header | 12, then padded |
| Object reference | 4 |
| `boolean`, `byte` | 1 |
| `char`, `short` | 2 |
| `int`, `float` | 4 |
| `long`, `double` | 8 |
| Object alignment | round up to a multiple of 8 |
| `String`, Latin-1 / ASCII | ≈ 16 + 1 per character, then aligned |
| `String`, non-Latin-1 | ≈ 16 + 2 per character, then aligned |
| Boxed `Integer`/`Long` versus primitive | ≈ 16 plus a 4-byte reference, versus 4 or 8 — the autoboxing tax |
| `HashMap` entry overhead | ≈ 30 (node, aligned, plus table slot and load-factor slack) |
| `ArrayList` slot overhead | 4 per slot, plus up to 50 % growth slack |

### Worked example

```kotlin
data class Item(
    val sku: String,          // ~24 chars, ASCII
    val description: String,  // ~40 chars, ASCII
    val price: Double,
    val qtyOnHand: Int,
    val locationId: Long,
    val active: Boolean,
)
val cache = HashMap<String, Item>()   // keyed by sku; the key object is shared, not duplicated
```

| Component | Arithmetic | Bytes |
|---|---|---|
| `Item` instance | 8 + 4 + 4 + 8 + 4 + 8 + 1 = 37 → align 8 | 40 |
| `sku` string | 16 + 24 | 40 |
| `description` string | 16 + 40 | 56 |
| `HashMap` entry overhead | node 24 + slot 4 + slack ≈ 30 | 30 |
| **Per row** | | **≈ 166 B** |

### The projection — what the developer actually needs

| Rows | Derived footprint | Reading |
|---|---|---|
| 5 000 (development fixture) | **0.8 MB** | invisible — *this is why it passes in development* |
| 50 000 | 8.3 MB | tolerable |
| 200 000 | **33 MB** | material on a 3 GB device |
| 1 000 000 | **166 MB** | fatal on every Zebra tier |
| 2 000 000 | **332 MB** | fatal, and *anonymous* — compressible but never free |

Emit this table per cache found, plus the **crossing point**: the record count at which the structure
exceeds measured headroom. A crossing point survives a changing business; a single figure does not.

### Precision discipline

- Accurate to roughly **±2×** on absolute bytes, and **exact on the scaling exponent**. The exponent
  forecasts; the constant is what you calibrate.
- **Calibrate once against a real heap dump** and keep the ratio. After that the model is genuinely
  trustworthy for that codebase.
- Write `≈166 B/row (model; uncalibrated)`, never `166 B/row`.
- Traps that break the arithmetic: shared or interned strings (do not double-count), `copy()` creating
  transient duplicates, nested collections (recurse), lazy fields, and above all **string lengths that
  the customer controls**. A description field 40 characters long in the fixture and 400 in production
  moves the whole table by 10×. **Derive string lengths from database schema column widths, not from
  test data** — that is fully static and much closer to production.

## 3. The five growth axes — what "future footprint" means

"Future" is ambiguous, and each axis needs a different instrument.

### Axis 1 — Data growth (`n`)
The customer's records, not yours. `F = B + n × s`. Evaluate at the largest production data set, not
the fixture. **Output:** the projection table plus the crossing point in rows.
*Derivable:* the function yes; `n_max` is one business question.

### Axis 2 — Session growth (`t`): the "dies after lunch" axis
`F(t) = F₀ + L × t`, so `time-to-kill = (C − F₀) / L`.

| Quantity | Example |
|---|---|
| Ceiling `C` (not-visible, read from device) | 512 MB |
| Steady state `F₀` after warm-up | 180 MB |
| Headroom | 332 MB |
| Accumulation 12 MB/h | → 27.7 h — survives a 10 h shift |
| Accumulation 40 MB/h | → **8.3 h — dies before shift end** |

**Measure per business transaction, not per hour.** Accumulation tracks work done, not the clock:
`0.4 MB per pick × 800 picks = 320 MB`. This is stable across customers, native to operations, and it
explains why the app fails first for the **fastest** operators.
*Derivable:* the risk yes (any lifecycle or cache finding predicts non-zero `L`); the rate no.

### Axis 3 — Release growth (`v`)
Footprint per commit against a stored baseline. Track **RSS**, which Android's guidance says "is
better for tracking changes in memory allocation" and is cheaper to compute. Absolute values are not
portable across devices; **deltas on one device are the valid signal.** Gate on regression beyond a
stated percentage.
*Derivable:* no — but the *absence of a gate* is, and is itself a finding.

### Axis 4 — Platform growth: the ceiling arrives uninvited
The app can be unchanged and start dying because the ceiling changed: Memory Limiter switches on as
devices reach Android 17; 16 KB pages shift available memory; each OS version's framework grows.
**Output:** overlay the fleet upgrade schedule on headroom — *"62 % of the fleet reaches Android 17 by
Q2; at current headroom, 18 % of devices land in the red band."*
*Derivable:* the version gates yes; the fleet schedule is a business input.

### Axis 5 — Constraint change: the denominator moves
`C` is not constant over a fleet's life. As memory prices keep low-RAM devices in service and delay
refreshes, the fleet-weighted median ceiling stalls or falls while axes 1–4 push `F` up. **Evaluate
headroom against the worst tier you must support, weighted by fleet share** — for most estates a 3 GB
handheld, never the newest device on a developer's desk.

| Axis | Grows with | Static? | Instrument | Headline |
|---|---|---|---|---|
| 1 Data | customer records | function: yes | §2 model + `n_max` | crossing point in rows |
| 2 Session | work done | risk: yes; rate: no | Δ`RssAnon` per transaction | transactions-to-kill |
| 3 Release | commits | gate absence only | RSS delta in CI | % regression vs baseline |
| 4 Platform | OS upgrades | version gates: yes | fleet schedule × headroom | % of fleet in red band |
| 5 Constraint | procurement | no | fleet mix | headroom on the binding tier |

## 4. Headroom — the number to lead with

```
H = (C_binding − P_binding) / C_binding
```

`P_binding` is peak usage **in the process state whose ceiling is tightest** — very often the
not-visible state, because that is where sync workers run. Reporting only the interactive peak
flatters the app exactly where it is weakest.

| Band | Headroom | Reading |
|---|---|---|
| Green | > 50 % | Comfortable; hold it with a regression gate |
| Amber | 25–50 % | Fits today; axes 1–2 will close it |
| Orange | 10–25 % | Fails under adverse conditions — big customer, long shift, crowded device |
| Red | < 10 % | Expect field kills; treat as a live incident |

Report both states and name the binding one:
*"TC26 (3 GB): visible 61 % green · not-visible 14 % orange — binding constraint is the sync worker."*

## 5. Honesty rules

1. Every number carries its unit **and** its ceiling.
2. Never mix units — see [api-patterns.md](api-patterns.md).
3. Label models as models, with parameters and calibration state.
4. Attribute measurements to a device / OS build / scenario tuple.
5. State the likely direction of a model's error.
6. Distinguish `max` from `Σ` on peaks, and justify the choice.
7. Do not compound two axes without saying so; errors multiply too.
8. Emit crossing points, not just values.

## 6. What stays undecidable from source

| Cannot be determined | Honest statement |
|---|---|
| `B`, and so absolute footprint | "Baseline not measured; the model gives scaling only." |
| `L` accumulation rate | "Leak risk present; rate needs the iteration protocol." |
| `nᵢ` record counts | "Scaling derived; supply the largest count to close the forecast." |
| `C` enforced ceiling | "Unknown — read it from the device." |
| Whether the app leaks in practice | "No leak-detection harness present." |
| Whether kills occur in the field | "No exit-info inspection present." |
| Real string lengths | "Derived from schema column widths; confirm against production." |

> A linter cannot measure a footprint. It can derive the function that produces one, identify which
> term dominates, and name the measurement that would close the gap. Doing that — and never inventing
> the missing term — is both honest and genuinely useful.
