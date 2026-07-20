# DataWedge Intent Output — Integration (golden path)
> Source: https://techdocs.zebra.com/datawedge/latest/guide/output/intent/ + /guide/api/setconfig/ — DataWedge 15.0, verified 2026-07-20. (Scaffold.)

## 1. Receiver
```kotlin
class ScanReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != "com.zebra.demo.SCAN") return
        val data = intent.getStringExtra("com.symbol.datawedge.data_string")
        val type = intent.getStringExtra("com.symbol.datawedge.label_type")
        // hand off to the app
    }
}
```

## 2. Register the receiver (Android 13+ must declare exported state)
```kotlin
ContextCompat.registerReceiver(
    ctx, ScanReceiver(),
    IntentFilter("com.zebra.demo.SCAN"),
    ContextCompat.RECEIVER_EXPORTED
)
```

## 3. Point a DataWedge profile's Intent Output at your action (SET_CONFIG)
Configure the **INTENT** output plugin with `intent_action = "com.zebra.demo.SCAN"` and `intent_delivery = Broadcast`, and associate the profile to your app.
TODO: full `com.symbol.datawedge.api.SET_CONFIG` bundle snippet (see /guide/api/setconfig/).
