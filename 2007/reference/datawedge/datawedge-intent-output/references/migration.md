# DataWedge Intent Output — Migration / version notes
> Source: techdocs DataWedge 11 → 15 — verified 2026-07-20. (Scaffold.)

- **Android 13+**: receivers must declare exported state (`RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED`).
- **Android 14**: intent-delivery latency / ordered-broadcast behavior — don't assume synchronous receipt.
- Prefer `SET_CONFIG` over legacy profile cloning.

TODO: DataWedge-version-specific config-key changes.
