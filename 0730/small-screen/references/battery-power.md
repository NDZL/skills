# Battery and power — an 80 mA average budget

> **Provenance.** Zebra battery capacities, the 10-hour figure and the power-practice bullets are
> quoted verbatim from the WS50 Programmer's Guide and spec sheets (`device-matrix.md` §2.3).
> Wear OS thresholds, the impact table and the tooling commands are from **W1** below. **The mA
> figures in §1.2 are arithmetic on the quoted capacities, not measured values** — measure your own
> app (§7).
>
> **Sources for this file** (full register: `device-matrix.md` §7):
> - **Z1** WS50 Programmer's Guide — `"Set screen brightness to the minimum level for effective
>   use"`, `"Set a short screen timeout interval (10-15 seconds)"`, `"Set the device to wake only
>   when touching the scan trigger or display"`, `"Use 'Full Black'"`, and the Doze instruction:
>   **don't disable or whitelist apps from battery optimization**
>   https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/
> - **Z2** WS50 spec sheet — `"Standard battery: 800 mAh"` / `"High-capacity battery: 1300 mAh"`,
>   `"Battery life: Up to 10 hours continuous operation"`
>   https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws50.html
> - **Z4** WS501 spec sheet — `"5 Wh; 1300 mAh; PowerPrecision; Hot Swap"`
>   https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws501.html
> - **W1** Conserve power and battery — **the primary Wear source for this file.** The impact table,
>   **4.44 %/h** and **3.2 %/h**, tiles/complications **≥ 2 h**, Data Layer discipline, wakelock
>   rules, Health Services, `ExerciseClient` "every minute or two", every `dumpsys` command here,
>   Battery Historian / Power Profiler / Perfetto guidance
>   https://developer.android.com/training/wearables/apps/power
> - **W13** Excessive battery usage (App quality) — corroborates the %/hour framing
>   https://developer.android.com/topic/performance/vitals/excessive-battery-usage
> - Battery Historian — https://github.com/google/battery-historian
> - Perfetto UI — https://ui.perfetto.dev

---

## 1. The budget, in numbers you can hold in your head

### 1.1 What Zebra states

- `"Standard battery: 800 mAh Li-Ion PowerPrecision (wrist)"`
- `"High-capacity battery: 1300 mAh Li-Ion PowerPrecision (converged)"`
- `"Battery life: Up to 10 hours continuous operation"`
- WS501: `"5 Wh; 1300 mAh; PowerPrecision; Hot Swap"`

### 1.2 Turn that into an average current budget

A full shift is the requirement. Dividing capacity by shift length gives the average draw the
*whole device* may sustain:

| Device / battery | Shift target | **Average current budget** |
|---|---|---|
| **WS50, 800 mAh (wrist)** | 10 h | **~80 mA** ← plan against this |
| WS50 / WS501, 1300 mAh | 10 h | ~130 mA |
| WS501, 1300 mAh, **hot-swap** | shift, with a battery change | ~130 mA, restartable |

**~80 mA is the number.** And it is not yours alone — it covers the display, the radios, the Zebra
stack, the scanner and the OS. Your app's share of it is a fraction.

For comparison, the display alone at high brightness, or an active Wi-Fi transmit burst, can each
exceed that entire budget while they run. **Which is the whole point: on this device, power
management is about *duty cycle* — how often and how long things are on — far more than about
efficient code.**

### 1.3 Wear OS states it as a percentage instead

Google gives explicit thresholds:

- Above **4.44 % per hour**, the watch **will not last a full day**
- **Target under 3.2 % per hour**

Note the different design point: a Wear OS watch must survive a ~16-hour waking day and cannot be
hot-swapped; a Zebra wearable must survive a shift and (on WS501) can have its battery swapped.
Same physics, different acceptance criterion.

---

## 2. The screen is the single biggest lever

Google's impact table ranks **screen on / interactive mode** as **High**, second only to network.
Zebra's own recommendations are all screen-directed:

- `"Set screen brightness to the minimum level for effective use"`
- `"Set a short screen timeout interval (10-15 seconds)"`
- `"Set the device to wake only when touching the scan trigger or display"`

A **10–15 second timeout** is aggressive by phone standards and correct here. It also has a design
consequence that is easy to miss:

> **Any task that takes a user longer than ~15 seconds to complete will have the screen time out
> mid-task.** If your screen requires reading a paragraph, comparing three values, or typing, the
> display dies while they are working. This is not a settings problem; it is a *workflow* problem,
> and the fix is `screen-layout.md` §6 — one decision per screen, resolvable in a glance.

### 2.1 Never hold the screen on

```kotlin
// ✗ Never. This defeats the timeout and is the top cause of failing a shift.
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

If a genuine case exists — a long guided procedure where the user's hands are occupied — scope it
to the exact screen and remove it the moment the reason ends:

```kotlin
override fun onResume() {
    super.onResume()
    if (step.requiresHandsFreeReading) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

override fun onPause() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // always, unconditionally
    super.onPause()
}
```

Verify it is really off — Google's suggested check is to disable always-on display in system
settings and confirm the screen goes off within the timeout.

### 2.2 Don't fight the brightness setting

Reading the ambient light sensor and boosting brightness "for legibility" undoes the largest saving
available. Solve legibility with **contrast and type size** (`screen-layout.md` §4, §10), which are
free, rather than with photons, which are not.

---

## 3. AMOLED: true black is a real, physical saving

The panel is AMOLED (`device-matrix.md` §2.1). On AMOLED, **a black pixel is an unpowered pixel** —
this is not a theme preference, it is a hardware property, and it is the reason Zebra says
`"Use 'Full Black'"` to maximize battery operation.

| Design choice | Power effect on AMOLED |
|---|---|
| `#FF000000` background | pixels **off** — no draw |
| Dark grey `#FF121212` background | pixels **on**, dimly — measurably worse than black |
| Large white surfaces | worst case, every subpixel at full drive |
| Saturated blue | least-efficient subpixel; also ages fastest |

Concrete rules:

- **`#FF000000`, not "dark grey".** A Material "dark theme" surface colour is not black. Override it
  (`screen-layout.md` §10).
- **Black window background, black surfaces, light text.** Invert the phone instinct: content is
  luminous marks on an unlit field.
- **Minimise lit area.** A filled 60 dp button lights 14 400 px; an outlined one lights its border.
  On a screen you look at all shift, that adds up.
- **Avoid large blue fills**, per Zebra's guidance and AMOLED subpixel efficiency both.

⚠ `device-matrix.md` §4.3 records that Zebra's source says "LCD panel" while specifying an AMOLED
display. The recommendation is right either way, but its *mechanism* — and therefore the size of
the win — is AMOLED's.

---

## 4. Do less, less often

The device can only reach deep idle if you leave it alone. **Ten small wakeups cost far more than
one batched wakeup doing the same total work**, because each one pays the cost of leaving and
re-entering idle.

### 4.1 Batch and defer

Google's Wear guidance, which applies equally to a Zebra wearable:

- **Defer work until the device is charging** — especially syncing and database maintenance.
- **Schedule prefetch for when charging and on Wi-Fi.**
- **Batch related operations to maximise idle time.**

```kotlin
val sync = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)          // do the heavy work in the cradle
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "sync", ExistingPeriodicWorkPolicy.KEEP, sync,
)
```

**A wearable in its charging cradle is where all expensive work belongs.** Design the sync model
around that, not around "every 15 minutes because it's simpler."

### 4.2 Network is the most expensive thing you can do

Ranked **Very high** in Google's table.

- **Never poll on a short interval.** A 30-second poll keeps the radio out of idle permanently and
  will not meet an 80 mA budget on its own.
- **Send deltas, not full state.** Google's phrasing: *transmit state changes instead of rapid
  updates*, and only those that actually change the UI.
- **Coalesce.** Ten requests batched into one cost far less than ten spread out, even for identical
  payload.
- **Offline-first.** Write locally, sync opportunistically. On a warehouse floor this is also the
  correct functional design — Wi-Fi coverage is patchy and the user cannot wait.
- Remember **there is no FCM** on these non-GMS devices (`device-matrix.md` §2.7), so "just use
  push" is not available. Long-interval pull plus charging-time sync is the realistic pattern.

### 4.3 Wakelocks

Ranked **Medium**, and mostly avoidable. Google: avoid manual wakelocks; acceptable uses are
background media and work where **WorkManager or JobScheduler holds the lock on your behalf**.

```bash
# Find unexpected wakelocks and over-long ones
adb shell dumpsys batterystats > stats.txt   # then load into Battery Historian
```

### 4.4 Sensors and location

- **GPS is ranked High.** Acquire only on explicit user request, and stop immediately after.
- Register sensors as late as possible, unregister in `onStop` — never in `onDestroy` only.
- Verify unregistration the way Google suggests: swipe-dismiss the app (or blank the screen with
  your palm), then

  ```bash
  adb shell dumpsys sensorservice     # your listener must be gone
  ```

- On Wear OS specifically, use **Health Services** (`ExerciseClient`, `PassiveMonitoringClient`)
  instead of raw `SensorManager` — it batches intelligently. Verify the app does not wake **more
  than every minute or two** in ambient mode.

### 4.5 Camera and scanning

The most expensive subsystem you can hold open — CPU, memory and battery at once.

- **Prefer DataWedge** over running your own decode loop; the work happens once, in Zebra's
  service, tuned for the hardware.
- **Trigger-driven, not continuous.** A continuously-running camera will not meet the budget.
- **Release the camera in `onPause`**, without exception.
- Zebra: `"Set the device to wake only when touching the scan trigger or display"` — the scan
  trigger is the intended entry point to an interaction. Design around it.

---

## 5. Doze — and Zebra's explicit warning

> Zebra: `"Ensure proper Doze mode handling; don't disable or whitelist apps from battery`
> `optimization"`

This is worth stating plainly because the temptation is strong and the instruction is unambiguous.
The usual "enterprise app" reflex — request
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, or have the MDM whitelist the app — **is contrary to
Zebra's guidance and will cost you the shift.** Doze is doing the work that makes 800 mAh last
10 hours.

```kotlin
// ✗ Do not ship this on a Zebra wearable
startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
    data = Uri.parse("package:$packageName")
})
```

Work *with* Doze instead:

- **WorkManager** for deferrable work — it is Doze-aware and batches into maintenance windows.
- **A foreground service only for genuinely continuous, user-visible work** (an active picking
  session), started and stopped with that session — never for the life of the app. Note the
  tension with `cpu-performance.md` §5: a foreground service avoids repeated cold starts but keeps
  you alive to consume power. Choose deliberately; do not drift into it.
- **`setExactAndAllowWhileIdle` only for a genuine user-facing deadline**, and rarely.
- **Never** a repeating `AlarmManager` on a short interval.

---

## 6. Wear OS specifics

### 6.1 Ambient / always-on

Supporting ambient mode is a *saving*, not a cost, for use cases that would otherwise keep the
screen fully on (Google names fitness). In ambient the display is dimmed and update rates drop to
roughly once a minute — design an ambient rendering that is legible in black-and-white at low
refresh.

### 6.2 Tiles and complications

Google's guidance is specific and strict:

- **Disable automatic refresh, or set the refresh rate to 2 hours or longer.**
- Use **FCM or appropriately scheduled jobs** for updates instead of polling.
- **Do not schedule work when the user is not interacting** with the tile or complication.
- **Offline-first**, and **share a single database** across the app, tiles and complications.

A tile refreshing every few minutes is one of the fastest ways to blow the 3.2 %/hour target.

### 6.3 Data Layer

- Be **conservative** — each transmission uses power and **wakes the paired device**.
- Set up `WearableListenerService` listeners only once the app is active.
- Send **state changes**, not raw streams. Google's example: if you display kilometres to one
  decimal place, do not transmit every metre.

```bash
adb shell dumpsys activity service WearableService
#   RpcService  -> MessageClient call frequency and paths
#   DataService -> DataClient item frequency
```

---

## 7. Measure — power claims must be measured, not argued

```bash
PKG=com.example.smallscreen

# --- full battery stats, for Battery Historian ---
adb shell dumpsys batterystats --reset
#   ... run a realistic workflow for 30+ minutes, unplugged ...
adb shell dumpsys batterystats > stats.txt
#   load stats.txt into Battery Historian:
#     - System stats > Jobscheduler stats  (are jobs running too often?)
#     - App stats > Scheduled job          (duration and frequency)
#     - wakelocks: unexpected ones, and ones longer than expected

# --- per-app summary ---
adb shell dumpsys batterystats $PKG

# --- coarse drain check: unplug, note level, run the workflow, note level ---
adb shell dumpsys battery | grep level
```

Also useful:

- **Android Studio Power Profiler** (*View > Tool Windows > Profiler*) — Google specifically
  suggests inspecting a system trace **as the screen goes off**, looking for work that keeps
  running and CPU that stays high.
- **Perfetto** (`ui.perfetto.dev`) — inspect your threads during screen-off and after dismissal.

**The acceptance test is a real shift, not a benchmark.** Charge to 100 %, run the actual workflow
on the actual device for the actual shift length, unplugged. Anything else is an estimate.

---

## 8. The screen-off audit

The highest-yield 20 minutes you can spend. Put the device to sleep and ask: *what is still
running?*

```bash
adb shell input keyevent KEYCODE_SLEEP
#   wait 2 minutes, then:
adb shell dumpsys sensorservice | grep -i $PKG      # should be nothing
adb shell dumpsys power | grep -i wake              # no wakelock of yours
adb shell dumpsys activity services $PKG            # only intentional services
adb shell dumpsys alarm | grep $PKG                 # no short-interval alarms
```

Every hit is a defect. A wearable app that does nothing while the screen is off is an app that
makes it to the end of the shift.

---

## 9. Checklist

- [ ] Average-current budget written down (~80 mA on an 800 mAh WS50)
- [ ] Background is `#FF000000`; no large white or blue fills
- [ ] `FLAG_KEEP_SCREEN_ON` absent, or scoped to one screen and cleared in `onPause`
- [ ] Every task completable inside the 10–15 s screen timeout
- [ ] No polling on a short interval; heavy sync gated on `setRequiresCharging(true)`
- [ ] No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; no MDM battery-optimisation whitelist requested
- [ ] No manual wakelocks; deferrable work goes through WorkManager
- [ ] Camera/scanner released in `onPause`; trigger-driven, not continuous
- [ ] Sensors unregistered in `onStop`, verified with `dumpsys sensorservice`
- [ ] (Wear) tiles/complications refresh ≥ 2 hours; Data Layer sends state changes only
- [ ] Screen-off audit (§8) clean
- [ ] **A full unplugged shift completed on the real device**
