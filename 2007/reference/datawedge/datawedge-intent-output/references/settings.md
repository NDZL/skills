# DataWedge Intent Output — Config keys & extras
> Source: https://techdocs.zebra.com/datawedge/latest/guide/output/intent/ — verified 2026-07-20. Verbatim.

## Intent Output config keys
- `intent_action` — the action string DataWedge emits (you define it; your receiver filters on it).
- `intent_category` — optional category.
- `intent_delivery` — delivery method: **startActivity** | **startService / startForegroundService** | **Broadcast**.
  Broadcast sets `Intent.FLAG_RECEIVER_FOREGROUND`.

## Intent extras (verbatim)
- `com.symbol.datawedge.data_string` — decoded barcode text.
- `com.symbol.datawedge.label_type` — symbology / label type.
- `com.symbol.datawedge.decode_data` — raw data (`ArrayList<byte[]>`).
- also: `com.symbol.datawedge.source`, `.decoded_mode`, `.tokenized_data` (GS1-parsed).
