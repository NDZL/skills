# DataWedge Intent Output — Troubleshooting
> Source: techdocs DataWedge — verified 2026-07-20. (Scaffold.)

| Symptom | Cause | Fix |
|---|---|---|
| No scans arrive | receiver action ≠ profile `intent_action` | make them identical |
| Crash on Android 13+ | receiver missing exported flag | `RECEIVER_EXPORTED` at register |
| Scans go to the wrong app | profile not associated to your app | associate the app in the profile |
| Only works in foreground | Broadcast delivery | DataWedge sets `FLAG_RECEIVER_FOREGROUND` on Broadcast |
