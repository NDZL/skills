# OEMInfo — Migrating off Build.SERIAL / generic reads
> Source: techdocs + Zebra developer portal — verified 2026-07-20. (Scaffold.)

`Build.SERIAL` and `TelephonyManager.getImei()` are restricted on Android 10+ (return `"unknown"` / throw). Replace with an OEMInfo query (`integration.md` + `attributes.md`) + `com.zebra.provider.READ` + authorization.

| Legacy call | OEMInfo replacement |
|---|---|
| `Build.SERIAL` | `content://oem_info/oem.zebra.secure/build_serial` |
| `TelephonyManager.getImei()` | `content://oem_info/wan/imei` |
