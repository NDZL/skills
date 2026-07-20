# AIDC Barcode — Migration (3.x → 4.x)
> Source: techdocs AI Data Capture 4.0 — verified 2026-07-20. (Scaffold.)

- 4.0 Maven coordinates: SDK `com.zebra.ai.sdk.vision:AI-Data-Capture-SDK`, model `com.zebra.ai.models.vision:barcode-decoder` (from the `emc-mvn-ext` repo).
- compile/target SDK must be **34+ (Android 14)**.
- Models delivered as **AAR** — remember `noCompress += ["tar", "tar.crypt"]`.

TODO: enumerate renamed classes/APIs and `Entity`/`Result` changes 3.x → 4.x.
