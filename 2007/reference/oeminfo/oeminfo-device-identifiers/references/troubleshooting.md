# OEMInfo — Troubleshooting
> Source: techdocs — verified 2026-07-20. (Scaffold.)

| Symptom | Cause | Fix |
|---|---|---|
| Null / empty cursor | permission or authorization missing | `com.zebra.provider.READ` + MX Access Manager grant |
| Empty right after boot | queried before `BOOT_COMPLETED` | query after boot |
| ANR / UI jank | queried on the main thread | move to a background thread |
| Works on a system app, not 3rd-party | app not authorized | grant via MX Access Manager |
