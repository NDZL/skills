# AIDC Barcode — Settings
> Source: https://techdocs.zebra.com/ai-datacapture/latest/barcodedecoder/ — verified 2026-07-20. (Scaffold.)

- Model name passed to `Settings`: `"barcode-decoder"`.
- Symbology enable/disable: `settings.Symbology.<SYMBOLOGY>.enable(true|false)` — e.g. `UPCE1`, `CODE128`, `EAN13`, …
- Enable **only** the symbologies you need (better performance, fewer misreads).
- Result accessors: `Result.getvalue()` (decoded text), `Result.getSymbology()` (int id).

TODO: full symbology enum list; `Localizer` / `InferencerOptions` tuning (see /class/inferenceroptions/).
