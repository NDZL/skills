# AIDC Barcode — Troubleshooting
> Source: techdocs AI Data Capture 4.0 — verified 2026-07-20. (Scaffold.)

| Symptom | Likely cause | Fix |
|---|---|---|
| Model won't load / init fails | Maven repo or AAR missing; `noCompress` not set | add `emc-mvn-ext` repo; `noCompress += ["tar","tar.crypt"]` |
| Nothing decodes | needed symbology not enabled | `settings.Symbology.<X>.enable(true)` |
| Build fails on the AAR | compile/target SDK < 34 | raise to 34+ |
| Camera preview black | CAMERA permission not granted | request at runtime |

TODO: add real error strings / log tags.
