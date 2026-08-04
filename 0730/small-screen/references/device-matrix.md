# Device matrix — verified small-screen targets

> **Provenance.** Every figure in the tables below is quoted from a named source, listed in
> §6. Zebra figures come from the **WS50 Programmer's Guide (EMDK for Android 13-0)** and the
> **WS50 / WS501 specification sheets**. Wear OS figures come from **developer.android.com**
> (Wear OS 6 / Wear Compose 1.6.0, retrieved 2026-07-30).
> **Nothing here is recalled from model memory.** Values marked `UNVERIFIED` were *not* found in
> any source and must be read off a real device (§5) before you rely on them.

This file exists so that no other reference — and no generated code — has to guess a number.

---

## 1. The three targets at a glance

| | **Zebra WS50** | **Zebra WS501** | **Wear OS 6 watch** |
|---|---|---|---|
| Platform | Android **11 AOSP** | Android (see §2.2) | Wear OS 6 (Android-based) |
| **GMS / Play Services** | **No** (verified) | **No** (`UNVERIFIED` — see §2.7) | **Yes** |
| Display | 2.0 in AMOLED | 2.0 in AMOLED, optically bonded | typically 1.2–1.5 in OLED |
| Resolution | **460 × 460** | **460 × 460** | varies by model |
| Density | **320 dpi bucketized** (actual 326) | 320 dpi bucketized | varies |
| **Design canvas** | **230 × 230 dp** | **230 × 230 dp** | **192 – 240+ dp** wide |
| Screen shape | **square** | **square** | **round** (usually) |
| Physical screen | 1.4 in × 1.4 in (35.88 mm sq) | 1.4 in × 1.4 in | varies |
| **RAM** | **1 GB** | **3 GB** | `UNVERIFIED` per model |
| Flash | 8 GB | 32 GB | varies |
| CPU | `UNVERIFIED` | **Qualcomm QC2290** | varies |
| Battery | **800 mAh** (wrist) / **1300 mAh** (converged) | **1300 mAh / 5 Wh**, hot-swap | varies |
| Wi-Fi / BT | `UNVERIFIED` / `UNVERIFIED` | **Wi-Fi 6** / **BT 5.3** | varies |
| Imager | `UNVERIFIED` | **SE4770** 1D/2D | none |
| System bars | **status bar hidden, nav bar removed** | same | TimeText, no nav bar |
| Split-screen | **not to be used** | same | n/a |

**The one number to internalise:** on both Zebra wearables you are laying out inside
**230 × 230 dp**. A Wear OS watch gives you **192–240 dp** of width. These are the same order of
magnitude — roughly **one eighth the area of a 411 × 891 dp phone**. Design intuition transfers
between the two small-screen families far better than it transfers from a phone.

---

## 2. Zebra WS50 / WS501 — verbatim quotes

### 2.1 Display and canvas

From the **WS50 Programmer's Guide**:

- `"Display size: 2.0 inches (diagonal)"`
- `"Screen dimensions: 1.4 inches (35.88 mm) x 1.4 inches (35.88 mm)"`
- `"Max. resolution: 460 x 460 pixels"`
- `"Screen density: 320 'bucketized' dpi (actual=326 dpi)"`
- `"Display type: AMOLED capacitive touch panel"`
- `"Zebra recommends a resolution of 230dp x 230dp (exported at 2X) when sketching apps"`

Because the density bucket is **320 dpi (xhdpi)**, the px→dp factor is exactly **2**:
`460 px ÷ 2 = 230 dp`. Ship drawables in **xhdpi**; that is the bucket the device actually asks
for. The *actual* 326 dpi matters only when you convert a physical measurement (mm/inches) into
pixels — see §3.

From the **WS501 spec sheet**:

- `"2 in. AMOLED 460 x 460 color display; optically bonded to touch panel"`

So **the canvas is identical across WS50 and WS501**. Only the compute and memory budget changed.

### 2.2 Memory, storage, CPU

WS50 Programmer's Guide:

> `"The RAM in the WS50 is limited to 1GB, which must be shared among the Linux kernel, Android`
> `app launcher, the Zebra software stack and other services"`

WS501 spec sheet:

- `"3 GB RAM/32 GB Flash"`
- `"Qualcomm® QC2290"`

The WS501 spec sheet states the OS only as
`"Android; for supported Android versions, visit: www.zebra.com/android-versions"` — it does **not**
name a version. Treat the **WS501 Android version as `UNVERIFIED`** and confirm it against
`zebra.com/android-versions` or the device itself (§5) before you set `minSdk`/`targetSdk`.

The WS50 Programmer's Guide states Android **11 AOSP**. Zebra publishes LifeGuard updates and an
Android 13 migration path for enterprise devices, and lists WS50 among **non-GMS** LifeGuard
devices — so a given WS50 fleet **may be on 11 or later**. Do not hardcode an assumption; read it.

### 2.3 Battery

WS50 Programmer's Guide / spec sheet:

- `"Standard battery: 800 mAh Li-Ion PowerPrecision (wrist)"`
- `"High-capacity battery: 1300 mAh Li-Ion PowerPrecision (converged)"`
- `"Battery life: Up to 10 hours continuous operation"`

WS501 spec sheet:

- `"5 Wh; 1300 mAh; PowerPrecision; Hot Swap"`

**The 800 mAh wrist variant is your worst case.** A 10-hour shift target against 800 mAh is the
single hardest constraint in this skill, and it is what `battery-power.md` is written against.

### 2.4 Physical / form factor

WS501 spec sheet:

- `"2.52 in L x 2.44 in W x 1.10 in D (64mm L x 62mm W x 28mm D)"`
- `"4.73 oz (134g)"`
- Sensors: light sensor, 6-axis accelerometer with MEMS gyro
- Worn on two fingers or back of hand

WS50 ships in **wrist**, **ring/finger**, and **back-of-hand** mounts. The mount changes the
battery *and* the button count (§2.5), so it is a required input, not a detail.

### 2.5 Buttons and input

WS50 Programmer's Guide:

- **Wrist variant: 4 programmable buttons**
- **Converged variant: 2 programmable buttons + trigger**
- Default mapping: **Left = Back**, **Right = Home** (long-press = power)
- **All buttons wake the device by default**
- A **custom scrollable QWERTY SIP** auto-displays for input fields

Consequences you must design for:

- **There is no navigation bar and no status bar** — `"status bar is hidden and Navigation bar`
  `removed"`. Back and Home arrive as *hardware key events*, not as system-bar taps. If you do not
  handle Back, the user cannot leave your screen.
- Because **every button wakes the device**, an accidental press costs screen-on time — the most
  expensive thing on the device (`battery-power.md` §2).
- The SIP eats most of a 230 dp canvas. Text entry on this form factor should be avoided by
  design — prefer scan, pick-from-list, or voice.

### 2.6 Camera and LEDs

WS50 Programmer's Guide:

- **Camera API2 recommended**; supported formats are **JPEG, PRIVATE, YUV_420_888, YV12 only**
- **LED control via an AIDL interface**, left/right multi-color LEDs,
  **solid illumination only — no blinking**

The format list is a hard constraint: if your imaging pipeline assumes `NV21` or a format outside
that set, it will fail on device. LEDs are a genuinely useful output channel on a device this
small — a green/red flash replaces a toast you have no room to draw.

### 2.7 Non-GMS — the architectural consequence

**WS50 is verified non-GMS.** The WS50 Programmer's Guide states `"No GMS capability"`, and Zebra's
LifeGuard documentation lists WS50 among its **non-GMS** devices, noting that without Play Services
some services must be updated by Zebra directly.

⚠ **WS501's GMS status is `UNVERIFIED`.** The WS501 spec sheet consulted here **does not mention
GMS either way**. The Zebra wearable line has been non-GMS to date, so *assume non-GMS* — but
confirm before you commit to an architecture, because the answer decides whether FCM, ML Kit and
Maps are available to you:

```bash
# Expect NO output on a non-GMS device
adb shell pm list packages | grep -E 'com.google.android.gms|com.android.vending'
```

Do not report WS501 as confirmed non-GMS on the strength of this file.

**Therefore the following are unavailable and must not appear in generated code:**

| Not available | Use instead |
|---|---|
| Google Play Store / in-app updates | MDM / StageNow / Enterprise Mobility Management |
| Firebase Cloud Messaging (FCM) | your own polling on a **long** interval, or MDM push |
| Play Services ML Kit (unbundled) | Zebra AI Suite, or ML Kit **bundled** variants |
| Google Maps SDK | offline/alternative maps, or omit |
| Google Sign-In, Play Integrity, Play Billing | enterprise auth; none of these apply |
| `GoogleApiAvailability`, Play-Services-Location fused provider | framework `LocationManager` |

This is the most common source of "works on my phone, dies on the WS50" failures, and it is not
a small-screen issue at all — it is a **non-GMS** issue that happens to arrive with the same
device. Check it first when something inexplicably fails to initialise.

---

## 3. Physical-size conversions (WS50/WS501)

At **326 actual dpi**, `1 inch = 326 px = 163 dp` (dividing by the 2× bucket factor):

| Physical | Pixels (@326 dpi) | dp (@320 bucket) |
|---|---|---|
| 7 mm (0.28 in) | ~91 px | **~46 dp** |
| 8 mm (0.31 in) | ~101 px | **~51 dp** |
| 10 mm (0.40 in) | ~130 px | **~65 dp** |

Keep this table in mind for §4 — it is what resolves the touch-target conflict.

---

## 4. ⚠ Conflict in the source: minimum touch zone

The WS50 Programmer's Guide contains **four** statements about minimum touch size, and they do not
all agree. All four are quoted verbatim:

1. `"Minimum 'hit zone' sizes for apps: Bare finger: 0.28 inches (7 mm); Gloved finger: 0.40 inches (10 mm)"`
2. `"Minimum 'touch zone' for two-inch display: 60 x 60 pixels (30 x 30 dp)"`
3. `"Touch zones should in most cases be set to 60dp for most screen regions, and 80dp in areas close to screen edges"`
4. `"Touch zones should be no less than 48dp"`

Statement **2 contradicts statement 4** directly: 30 dp is less than 48 dp.

**Resolution — statement 2 is the outlier, and the physics says so.** Converting statement 1's
*physical* minimums through §3 gives **~46 dp bare** and **~65 dp gloved**. Those land essentially
on top of statements 3 and 4 (48 dp floor, 60 dp normal). Statement 2's "30 × 30 dp" matches none
of them and is inconsistent with the fingertip diameter the same document quotes
(`"the average human fingertip is about 0.31 to 0.40 inches (8–10 mm) in diameter"` — which is
51–65 dp, i.e. a finger physically cannot reliably hit a 30 dp target).

**The operative rule for generated code** (see `screen-layout.md` §3):

| Situation | Minimum touch target |
|---|---|
| Absolute floor, anywhere | **48 dp** |
| Normal interactive control | **60 dp** |
| Control near a screen edge | **80 dp** |
| **Gloved operation** (cold-chain, warehouse) | **64 dp**, prefer 80 dp |

**Do not use the 30 × 30 dp figure.** If a Zebra reviewer cites it, raise this conflict rather than
silently complying — a 30 dp control on a gloved hand is an unusable control.

### 4.1 Grid and spacing (no conflict — quoted verbatim)

- `"Most measurements should align to an 8dp grid"`
- `"For iconography, typography and other small components, use a 4dp grid"`
- `"To avoid an 'overcrowded' UI, set padding at 10–16dp"`
- `"Separate UI components by 12 pixels (6 dp)"`
- `"Border around each grid cell: 12 pixels (6 dp)"`

### 4.2 Typography (verbatim)

- `"Body copy: 14pt"`
- `"Captions: 12pt"`
- `"No text should be smaller than 12pt"`

### 4.3 Colour (verbatim, with a noted inconsistency)

- `"Blue is the most challenging color for the WS50 LCD panel to render"`
- `"Use 'Full Black'"` to maximize battery operation

⚠ **The source says "LCD panel" while the same document specifies an AMOLED display.** The
*advice* is sound for AMOLED regardless — on AMOLED, blue subpixels are the least efficient and
age fastest, and true black switches pixels off entirely. The "LCD" wording appears to be an
error in the source. Treat the guidance as **AMOLED guidance** (see `battery-power.md` §3), which
is also what makes the "full black" recommendation physically meaningful.

---

## 5. Read the specs off the real device

Never ship against a number from a spec sheet when the device is in front of you. These commands
are the authority for the fleet you are actually targeting.

```bash
# ---- identity & OS ----
adb shell getprop ro.product.model            # e.g. WS50, WS501
adb shell getprop ro.build.version.release    # Android version
adb shell getprop ro.build.version.sdk        # API level
adb shell getprop ro.zebra.build.version      # Zebra BSP / LifeGuard patch level

# ---- is this a GMS build? (expect NO output on WS50/WS501) ----
adb shell pm list packages | grep -E 'com.google.android.gms|com.android.vending'

# ---- screen: the numbers your layout must satisfy ----
adb shell wm size        # -> Physical size: 460x460
adb shell wm density     # -> Physical density: 320
#   dp width = px width / (density / 160).  460 / (320/160) = 230 dp

# ---- memory: the real budget (see memory.md) ----
adb shell cat /proc/meminfo | head -3         # MemTotal / MemFree / MemAvailable
adb shell getprop dalvik.vm.heapgrowthlimit   # per-app heap ceiling WITHOUT largeHeap
adb shell getprop dalvik.vm.heapsize          # ceiling WITH android:largeHeap="true"
adb shell getprop ro.config.low_ram           # "true" => isLowRamDevice() is true

# ---- CPU ----
adb shell cat /proc/cpuinfo | grep -i -E 'hardware|model name'
adb shell cat /sys/devices/system/cpu/present # core count

# ---- battery ----
adb shell dumpsys battery | grep -i -E 'level|scale|voltage'
```

**Two properties deserve special attention**, because this skill's memory advice hinges on them
and their WS50/WS501 values are **`UNVERIFIED`** in every source consulted:

- `dalvik.vm.heapgrowthlimit` — your per-process Java heap ceiling. On 1 GB Android devices this
  has historically been in the 96–192 MB range, but **the WS50's value is not documented in any
  source consulted here.** Read it. It sets your entire bitmap and caching budget.
- `ro.config.low_ram` — if `true`, `ActivityManager.isLowRamDevice()` returns true, and Android
  itself disables some features. `memory.md` §1 explains how to branch on it.

---

## 6. Wear OS 6 — verbatim quotes

Wear OS is a **different platform**, not a smaller Android. Quotes below are from
developer.android.com, retrieved 2026-07-30.

### 6.1 Screen sizes and the breakpoint

- Design **first** for the smallest supported round screen: **204 dp – 216 dp**
- Also test at **192 dp** with **enlarged font sizes**
- **Primary breakpoint: 225 dp**, separating smaller from larger screens
- Large screens: **225 dp to 240+ dp**

```kotlin
const val LARGE_DISPLAY_BREAKPOINT = 225

@Composable
fun isLargeDisplay() =
    LocalConfiguration.current.screenWidthDp >= LARGE_DISPLAY_BREAKPOINT
```

- **All top, bottom and side margins must be defined in percentages**, not absolute values —
  this is what stops content being clipped by a round bezel.
- Hard rule from the docs: **"a larger display screen should never show less information than
  smaller screens."**

> **Note the coincidence, and do not over-read it.** The Zebra canvas (230 dp) sits just *above*
> the Wear OS large-display breakpoint (225 dp). Useful as a sanity check on information density —
> a layout that looks right on a large round watch is in the right ballpark for a WS50 — but the
> Zebra screen is **square**, so percentage margins and round-bezel safe areas are **not**
> required there, and `ScalingLazyColumn`/`TransformingLazyColumn` curvature effects are wrong there.

### 6.2 Toolkit versions

- `androidx.wear.compose:compose-*:1.6.0` is the latest release (**2026-03-25**)
- Use the **Wear Compose Material 3** library for current features, incl. **Material 3 Expressive**
- **Material 3 Expressive is supported on Wear OS 3 and higher**; Wear OS 6 ships a design refresh
  based on it
- **Wear ProtoLayout Material 3** provides components/layouts for **tiles**
- 1.6.0 added the **Wear Compose Navigation3** library (integrates `NavDisplay` / `SceneStrategy`
  with Wear swipe-to-dismiss) and `TransformingLazyColumn` support for `reverseLayout` + snapping

### 6.3 Memory limits that are actually specified

- Watch faces using **Watch Face Format**: **10 MB maximum in ambient mode**, **100 MB in
  interactive mode**
- General wearable RAM is commonly cited as **512 MB – 1 GB**; per-model values are `UNVERIFIED`

### 6.4 Battery thresholds (numeric, from Google)

- Above **4.44 % per hour**, the watch **will not last a full day**
- **Target under 3.2 % per hour**
- **Tiles and complications: disable auto-refresh, or set refresh to 2 hours or longer**
- `ExerciseClient`: verify the app does not wake **more than every minute or two** in ambient mode

### 6.5 Relative cost of operations (from Google's table)

| Event | Impact |
|---|---|
| Network access (LTE, Wi-Fi) | **Very high** |
| Screen on / interactive mode | **High** |
| GPS sensor access | **High** |
| High CPU usage | **High** |
| Heart rate sensor | Medium |
| Bluetooth device access | Medium |
| Wakelocks | Medium |

---

## 7. Sources

Zebra:

- WS50 Programmer's Guide — EMDK for Android 13-0 —
  https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/
- WS50 Android Wearable Computer spec sheet —
  https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws50.html
- WS501 Android Wearable Computer spec sheet —
  https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws501.html
- Zebra LifeGuard for Android — https://techdocs.zebra.com/lifeguard/about/
- Android 13+ upgrade notes — https://techdocs.zebra.com/lifeguard/a13/
- Supported Android versions — https://www.zebra.com/android-versions

Wear OS:

- Conserve power and battery — https://developer.android.com/training/wearables/apps/power
- Develop for different screen sizes —
  https://developer.android.com/training/wearables/compose/screen-size
- Adaptive design foundations —
  https://developer.android.com/design/ui/wear/guides/foundations/adaptive-design
- Wear Compose release notes —
  https://developer.android.com/jetpack/androidx/releases/wear-compose
- Wear Compose Material 3 —
  https://developer.android.com/jetpack/androidx/releases/wear-compose-m3
- Optimize watch face memory usage —
  https://developer.android.com/training/wearables/wff/memory-usage
- What's new in Wear OS 6 —
  https://android-developers.googleblog.com/2025/05/whats-new-in-wear-os-6.html

**Retrieved 2026-07-30.** Re-verify before a release: Zebra BSP/LifeGuard levels and
`androidx.wear.compose` versions both move.
