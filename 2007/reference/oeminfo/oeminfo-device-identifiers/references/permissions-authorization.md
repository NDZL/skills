# OEMInfo — Permissions & authorization
> Source: https://techdocs.zebra.com/oeminfo/consume/ — verified 2026-07-20. Verbatim.

Manifest permission:
```xml
<uses-permission android:name="com.zebra.provider.READ" />
```

Android 11+ package visibility:
```xml
<queries>
    <package android:name="com.zebra.zebracontentprovider" />
</queries>
```

**3rd-party apps** must be granted access via the **MX Access Manager** (e.g. EMDK ProfileManager or StageNow). Android 10+ restricts device identifiers (serial / IMEI) to authorized apps — hence the grant.
