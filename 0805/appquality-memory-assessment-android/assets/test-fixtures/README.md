# Test fixtures

Synthetic inputs with expected results, used by `evals/evals.json`. All original to this skill; no
customer or third-party code.

## `sizing/` — makes the cost model falsifiable

| Fixture | Expected result | Protects |
|---|---|---|
| `sizing/Item.kt` + `sizing/expected.json` | `166` bytes per row with a `HashMap` container | `FIXTURE-001` |

Without this pair the quantification is unfalsifiable, which would be a worse failure than not
quantifying at all. Verify with:

```
python ../../scripts/estimate_object_size.py --file sizing/Item.kt --container hashmap --json
```

`expected.json` records the field-by-field arithmetic, so a change in the model shows up as a
specific diff rather than a changed total. The recorded value is a **model** figure: the point of the
fixture is that the model stays consistent with its documented byte table, not that 166 is a measured
truth.

## `rules/positive/` — must be reported

| Fixture | Expected finding |
|---|---|
| `rules/positive/UnboundedMasterDataCache.kt` | `MEM-CACHE-001` BLOCKER, with a projection table |

## `rules/negative/` — must NOT be reported

Precision is the whole product. A memory report that cries wolf is disabled within a week and then
catches nothing forever, so these fixtures matter as much as the positive ones.

| Fixture | Must not be flagged | Protects |
|---|---|---|
| `rules/negative/BoundedStatusLookup.kt` | Not `MEM-CACHE-001` — the bound comes from code, not from customer data | `NEG-FP-001` |
| `rules/negative/LegacyTrimHandlerPreA14.kt` | Not `MEM-PRESSURE-001` — with `minSdk` below 34 these branches are live | `NEG-FP-002` |

## Running the scanner against the fixture tree

```
python ../../scripts/scan_memory_rules.py --project rules --min-sdk 24
```

With `--min-sdk 24` the trim-level candidate must appear under `suppressed_by_version_gate`, not
under `findings`. With `--min-sdk 34` it must appear as a finding. That flip is the eval.

Note that the scanner reports **candidates**; `BoundedStatusLookup.kt` will match the
`MEM-CACHE-001` search signature by design, and the assessment must then *reject* it after reading
the surrounding code. The fixture therefore tests the judgement, not the regular expression.
