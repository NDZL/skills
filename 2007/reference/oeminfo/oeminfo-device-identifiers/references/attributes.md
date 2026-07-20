# OEMInfo — Attribute catalog (URIs)  ← the parametric variation lives HERE
> Source: https://techdocs.zebra.com/oeminfo/consume/ — verified 2026-07-20. Verbatim URIs.

| Attribute | Content URI |
|---|---|
| Serial number | `content://oem_info/oem.zebra.secure/build_serial` |
| Wi-Fi MAC | `content://oem_info/oem.zebra.secure/wifi_mac` |
| Bluetooth MAC | `content://oem_info/oem.zebra.secure/bt_mac` |
| IMEI | `content://oem_info/wan/imei` |

URI shape: `content://<authority=oem_info>/<provider=category>/<key>`.
TODO: add remaining categories (build / firmware / OS-update) from techdocs.
