---
# LAYER 1 · reference skill · design-and-constraints paradigm
name: small-screen
type: reference
product: wearable
description: >
  Build an Android app that targets ONLY a watch-sized screen — Zebra WS50 / WS501 wearables
  (2 in, 460x460 px, 230x230 dp canvas, 1 GB or 3 GB RAM, quad Cortex-A53, 800-1300 mAh, non-GMS)
  and Wear OS 6 watches (192-240 dp). Covers the fixed small canvas, touch targets, typography,
  navigation with no nav bar, memory budgeting under 1 GB, low-tier all-little-core CPU and jank,
  AMOLED/battery duty-cycle design, and XML-Views-vs-Compose choice. Use when designing,
  reviewing or generating code for a wrist-, finger- or back-of-hand-worn device, or a smartwatch.
  SKIP for phones/tablets, for making an existing app RESPONSIVE or ADAPTIVE across form factors
  (this skill is small-screen-ONLY by design), and for Zebra API specifics — DataWedge, AI Suite,
  OEMInfo, EMDK and printing each have their own skill.
license: "Apache-2.0"
metadata:
  owner: "Wearables / Device SDK team"
  lifecycle: beta
  confidentiality: customer-safe
  keywords: [WS50, WS501, wearable, small screen, watch, Wear OS, 230dp, low memory, 1GB RAM,
             low-tier CPU, Cortex-A53, battery, AMOLED, touch target, non-GMS, glanceable]
version: 0.1.0
sdk-min: "Android 11 (WS50 AOSP)"
sdk-tested: "Android 11 AOSP (WS50) · androidx.wear.compose 1.6.0 (Wear OS 6)"
composes: []
---

# small-screen

## Critical: Do Not Trust Internal Knowledge

Screen dimensions, densities, RAM, CPU, battery capacities and touch-target minimums are
**device-specific and version-specific**. Take every number **verbatim from
`references/device-matrix.md`** — never recall a device spec from memory, and never infer one from
a similar-sounding model. `device-matrix.md` marks each value with its source, and marks
`UNVERIFIED` anything that must be read off a real device instead.

Two facts that models routinely get wrong about these devices, both established in
`device-matrix.md`:

- **The Zebra WS50/WS501 are not Wear OS devices.** They run **full Android AOSP** on a square
  2-inch screen. Wear Compose, tiles, complications and ambient mode do **not** apply to them.
- **They have no Google Mobile Services.** FCM, Play Services ML Kit, Maps and Play Store are
  unavailable. Generating code that depends on GMS produces an app that cannot initialise.
  (Verified for WS50; assume-but-confirm for WS501 — `device-matrix.md` §2.7.)

## Scope

**In:** designing and building an app whose *only* target is a watch-sized screen — the fixed
230 dp Zebra canvas or the 192–240 dp Wear OS range; layout, touch, typography, colour and
navigation for that canvas; memory budgeting under 1 GB; performance on a low-tier all-little-core
CPU; battery duty-cycle design against a ~10-hour shift or a Wear OS day; and choosing between XML
Views and Compose.

**Out:** phone/tablet UI; **making an existing app responsive or adaptive** (see §"Golden path"
step 0 — this skill deliberately rejects that approach); Zebra API specifics (`datawedge-*`,
`aidc-*`, `oeminfo-*`, `printer-*`); device provisioning, MDM, StageNow, MX and key remapping;
watch-face authoring beyond its memory limits.

## Required inputs

Gather **before** generating any code or layout:

| Input | Why it changes the output |
|---|---|
| **Target device(s)** — WS50 / WS501 / Wear OS model | sets RAM (1 GB vs 3 GB), toolkit default, and whether GMS exists |
| **Android / Wear OS version** | WS50 fleets may be on 11 or later; read it, don't assume |
| **Mount** — wrist / finger-ring / back-of-hand | changes battery (800 vs 1300 mAh) **and** button count |
| **Gloved operation?** | moves the touch-target floor from 48 dp to 64–80 dp; can halve controls per screen |
| **Shift length + hot-swap available?** | sets the average-current budget (~80 mA on an 800 mAh WS50) |
| **Toolkit already chosen?** | if not, `references/toolkit-choice.md` §1 decides it |
| **Scanning via DataWedge or in-app camera?** | dominates CPU, memory and battery planning |

If the device is in hand, run the read-the-device commands in `device-matrix.md` §5 and use those
values in preference to any spec sheet.

## Golden path

0. **Commit to small-screen-only.** Do not port or adapt a phone app. Redesign the *workflow*
   first — Zebra's own guidance says the display and RAM `"require significant modifications to
   existing apps, UIs and workflows."` (`anti-patterns.md` §0.)
1. **Read the real numbers** for the target device — `device-matrix.md`, then §5 on the device itself.
2. **Choose the toolkit** — `toolkit-choice.md` §1. Wear OS → Compose. WS501 → either.
   WS50 (1 GB) → Views + `ConstraintLayout`, or Compose behind the measured gate in §3.
3. **Design one screen per decision** inside 230 dp (or 192–240 dp on Wear) — `screen-layout.md`.
   One primary value, at most two supporting lines, 2–4 buttons.
4. **Set budgets before coding** — a PSS/heap target (`memory.md` §2), a frame budget
   (`cpu-performance.md` §2), an average-current budget (`battery-power.md` §1).
5. **Build to the floor**: true-black theme, `xhdpi` only, portrait-locked, no GMS, hardware Back
   handled, workflow state persisted per step.
6. **Verify on the real device** — jank, memory, `am kill` restore, and a full unplugged shift.
   Emulators are valid for layout only.

## API / Config usage policy

- Device specs, touch minimums, type sizes and spacing come **verbatim from
  `references/device-matrix.md`**; layout rules derived from them live in `screen-layout.md`.
- Where the Zebra source **contradicts itself** — the minimum touch zone — `device-matrix.md` §4
  documents all four conflicting statements and the resolution. **Use 48 dp as the floor; never the
  30 × 30 dp figure.** Do not silently pick one side; if challenged, cite §4.
- **Never emit a GMS dependency for a Zebra target.**
- **Never emit** `android:largeHeap="true"`, `FLAG_KEEP_SCREEN_ON` outside a scoped-and-cleared
  case, or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — the last is contrary to Zebra's explicit
  instruction.
- Any quantitative performance or battery claim must be **measured on the target device** with the
  commands given; an estimate is not evidence.
- Use Wear artifacts (`androidx.wear.compose:*`) for Wear OS and **never** phone Material 3 there;
  conversely never use Wear curvature components on the Zebra square panel.

## Anti-patterns

Shrinking or adapting a phone app · bottom nav / drawer / tabs on 230 dp · touch targets < 48 dp ·
text < 12 sp · dark-grey instead of true black on AMOLED · blue as the primary accent ·
free-text entry as the main input · a screen that takes longer than the 10–15 s timeout ·
full-size bitmap decodes · `largeHeap` · keeping the screen on · short-interval polling ·
whitelisting from battery optimisation · continuous camera · assuming the process survives
backgrounding · shipping density buckets other than `xhdpi` · any GMS dependency on WS50/WS501.

Full list with reasons and replacements: `references/anti-patterns.md`.

## References

Load **one** file per intent — do not read them all.

| Intent | File |
|---|---|
| A device spec, a verbatim number, or reading specs off hardware | `references/device-matrix.md` |
| Layout, canvas, touch targets, type, colour, navigation, lists | `references/screen-layout.md` |
| XML Views vs Compose; per-toolkit performance rules; multi-target module split | `references/toolkit-choice.md` |
| RAM budget, bitmaps, caches, process death, leaks, `onTrimMemory` | `references/memory.md` |
| Jank, frame budget, cold start, threading, thermal, layout depth | `references/cpu-performance.md` |
| Battery budget, AMOLED, screen timeout, Doze, WorkManager, measurement | `references/battery-power.md` |
| Wear OS only: tiles, complications, ambient, Health Services, Data Layer | `references/wear-os-surfaces.md` |
| What to refuse and what to do instead | `references/anti-patterns.md` |

## Verification (evals/)

**Status: specified, not yet authored.** No runnable evals ship with v0.1.0 — validation level is
`inspection` only. The assertions below are what `evals/` must contain before this skill leaves
beta:

- `layout-evals.json` — a generated layout **inflates and renders** at 460 × 460 / 320 dpi with
  **no view clipped**, every touch target **≥ 48 dp**, and no text **< 12 sp**. Re-run at
  `font_scale 1.3` and assert the same.
- `memory-evals.json` — the sample workflow **runs on device** and `dumpsys meminfo` reports TOTAL
  PSS under the stated budget; `adb shell am kill` followed by relaunch **restores workflow state
  with zero data loss**.
- `cpu-evals.json` — `dumpsys gfxinfo framestats` reports **< 1 % janky frames** across the primary
  workflow on the target device; Macrobenchmark cold start within budget.
- `battery-evals.json` — the screen-off audit (`battery-power.md` §8) returns **no** registered
  sensors, wakelocks or short-interval alarms belonging to the app.
- `platform-evals.json` — the release APK contains **no** GMS dependency, **no** `largeHeap`, and
  **no** density bucket other than `xhdpi` for a Zebra target.

Every eval must run on **real hardware**; an emulator satisfies the layout assertions only.

## Sources

**The complete source register is `references/device-matrix.md` §7**, which maps every source to the
specific claims it establishes, tags each **[P]**rimary or **[S]**econdary, and lists the seven
known gaps in §7.5. Each reference file also carries the subset of URLs behind its own claims, so a
single-file load stays self-contained.

Primary sources, all retrieved **2026-07-30**:

| | Source |
|---|---|
| **Z1** | **WS50 Programmer's Guide** (EMDK for Android 13-0) — the most-quoted source in this skill: canvas, density, touch zones, grid, typography, colour, RAM, buttons, camera, power practices, non-GMS · https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/ |
| **Z2** | WS50 spec sheet · https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws50.html |
| **Z4** | WS501 spec sheet · https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws501.html |
| **Z8** | Zebra LifeGuard for Android (WS50 non-GMS) · https://techdocs.zebra.com/lifeguard/about/ |
| **Z12** | Zebra supported Android versions · https://www.zebra.com/android-versions |
| **Q1** | Qualcomm QCM2290 (quad Cortex-A53, Adreno 702) · https://www.qualcomm.com/internet-of-things/products/q2-series/qcm2290 |
| **W1** | Wear OS — Conserve power and battery · https://developer.android.com/training/wearables/apps/power |
| **W2** | Wear OS — Develop for different screen sizes · https://developer.android.com/training/wearables/compose/screen-size |
| **W3** | Wear OS — Adaptive design foundations · https://developer.android.com/design/ui/wear/guides/foundations/adaptive-design |
| **W4** | Wear Compose release notes (1.6.0) · https://developer.android.com/jetpack/androidx/releases/wear-compose |
| **W6** | Watch Face Format memory limits · https://developer.android.com/training/wearables/wff/memory-usage |

**Re-verify before a release.** Zebra BSP/LifeGuard levels, Z12, and `androidx.wear.compose`
versions all move independently of this skill. When they do, update `device-matrix.md` §7 first —
every other file cites it.
