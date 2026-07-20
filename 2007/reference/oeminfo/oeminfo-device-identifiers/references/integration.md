# OEMInfo — Integration (the one query mechanic)
> Source: https://techdocs.zebra.com/oeminfo/consume/ — verified 2026-07-20. (Scaffold.)

Every attribute is read the **same** way — only the URI changes (see `attributes.md`).

```java
Uri uri = Uri.parse("content://oem_info/oem.zebra.secure/build_serial");
ContentResolver cr = getContentResolver();
Cursor cursor = cr.query(uri, null, null, null, null);
if (cursor != null && cursor.moveToFirst()) {
    String value = cursor.getString(0);
    cursor.close();
}
```

Rules: always query on a **background thread** (never the main/UI thread); only after `BOOT_COMPLETED`.
