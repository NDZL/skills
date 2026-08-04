# Wear OS — the surfaces a Zebra wearable does not have

> **Provenance.** All values, component names and thresholds are from developer.android.com
> (Wear OS 6 / Wear Compose 1.6.0, retrieved 2026-07-30) and are collected in `device-matrix.md`
> §6. **Read this file only if you are targeting Wear OS.** For a Zebra WS50/WS501 it is not
> applicable — those devices run full Android AOSP with none of these surfaces.

---

## 1. The core difference: Wear OS is not one app, it is several surfaces

A Zebra wearable app is a normal Android app that happens to be on a 230 dp screen. **A Wear OS
app is a set of cooperating surfaces**, and the user may never open the "app" at all.

| Surface | What it is | User reaches it by |
|---|---|---|
| **App** | your full-screen activity | app launcher |
| **Tile** | a single glanceable, non-interactive-ish screen | swiping from the watch face |
| **Complication** | one datum embedded in a watch face | already on screen, always |
| **Ongoing activity** | your active session surfaced system-wide | watch face indicator / recents |
| **Notification** | short interruptive message | wrist raise |
| **Watch face** | the whole face (a separate product) | — |

**Design implication:** on Wear OS the *most-used* part of your product is often the tile or
complication, not the app. Google's power guidance reflects this — tiles and complications get
strict refresh limits precisely because they are always present.

This is the sharpest contrast with the Zebra devices, where the status bar and navigation bar are
removed and your activity **is** the entire experience.

---

## 2. Toolkit and dependencies

```kotlin
// Wear-specific artifacts. Latest release 1.6.0 (2026-03-25).
implementation("androidx.wear.compose:compose-material3:1.6.0")
implementation("androidx.wear.compose:compose-foundation:1.6.0")
implementation("androidx.wear.compose:compose-navigation:1.6.0")

// Tiles use ProtoLayout, not Compose UI
implementation("androidx.wear.protolayout:protolayout-material3:<latest>")
implementation("androidx.wear.tiles:tiles:<latest>")

// Responsive padding helpers
implementation("com.google.android.horologist:horologist-compose-layout:<latest>")
```

⚠ **Do not use `androidx.compose.material3`** (the phone artifact) on a watch. It gives you wrong
component sizing, no curvature support, no swipe-to-dismiss integration, and none of the Wear
Material 3 Expressive styling.

**Material 3 Expressive** is supported on **Wear OS 3 and higher**; Wear OS 6 ships a design
refresh based on it. Wear Compose Material 3 and ProtoLayout Material 3 are what expose it,
including Wear OS 6's dynamic colour theming.

---

## 3. App scaffolding and navigation

```kotlin
@Composable
fun WearApp() {
    AppScaffold {                                  // app level: TimeText, transitions
        val navController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "list",
        ) {
            composable("list") { PickListScreen(onSelect = { navController.navigate("detail/$it") }) }
            composable("detail/{id}") { DetailScreen() }
        }
    }
}

@Composable
fun PickListScreen(onSelect: (String) -> Unit) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->   // screen level
        TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ListHeaderDefaults.minimumTopListContentPadding
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                ) { Text("Picks") }
            }
            items(rows, key = { it.id }) { row ->
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    onClick = { onSelect(row.id) },
                ) { Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
```

Key points:

- **`AppScaffold` once, `ScreenScaffold` per screen.** They coordinate `TimeText` and scroll state;
  skipping them is why hand-rolled Wear screens look subtly wrong.
- **Horizontal swipe belongs to the system** (swipe-to-dismiss). Never bind a horizontal gesture.
- **`rememberResponsiveColumnPadding` (Horologist)** computes percentage padding — pass it to
  **both** `ScreenScaffold`'s and `TransformingLazyColumn`'s `contentPadding`.
- **Wear Compose Navigation3** (new in 1.6.0) integrates `NavDisplay`/`SceneStrategy` with Wear
  swipe-to-dismiss; prefer it for new work if you are already on Navigation 3.

### 3.1 Screen sizes

```kotlin
const val LARGE_DISPLAY_BREAKPOINT = 225

@Composable
fun isLargeDisplay() =
    LocalConfiguration.current.screenWidthDp >= LARGE_DISPLAY_BREAKPOINT
```

- Design first for the smallest round screen: **204–216 dp**; test at **192 dp** with enlarged fonts.
- **Percentage margins only** — absolute margins get clipped by the bezel.
- **Hard rule: a larger screen must never show *less* information than a smaller one.** If a
  breakpoint increases component or text size, it must not push content out.
- Quality tiers Google defines: **Tier 1** works everywhere; **Tier 2** shows *more* on larger
  screens; **Tier 3** offers differentiated experiences at breakpoints.

---

## 4. Tiles

A tile is a **glanceable, fast, mostly-static** surface built with **ProtoLayout**, not Compose UI.
It is rendered by the system in another process.

Power rules (Google, verbatim in effect):

- **Disable automatic refresh, or set the refresh interval to 2 hours or longer.**
- Use **FCM or appropriately scheduled jobs** to push updates instead of polling.
- **Do not schedule work when the user is not interacting** with the tile.
- **Offline-first**, and **share one database** with the app and complications.

```kotlin
class PickTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(Timeline.fromLayoutElement(layout()))
                // No freshness interval => no automatic refresh. Push updates instead:
                //   TileService.getUpdater(context).requestUpdate(PickTileService::class.java)
                .build()
        )
}
```

**Design a tile for one datum and at most one tap target.** If it needs scrolling or a form, it
should be an app screen, not a tile.

---

## 5. Complications

One value, inside someone else's watch face. Constraints are tighter than a tile's:

- You do not control placement, size, colour or typography — the watch face does.
- Provide **every complication type you can support** (`SHORT_TEXT`, `RANGED_VALUE`,
  `MONOCHROMATIC_IMAGE`, …) so more faces can host you.
- Always supply **preview data** — that is what the user sees when choosing complications.
- Same refresh discipline as tiles: **≥ 2 hours**, or push.
- Read from the **shared database**; never fetch from the network in a complication provider.

---

## 6. Ambient mode / always-on

Ambient is a **power saving**, not a feature cost, for anything that would otherwise hold the
screen fully on — Google names fitness as the canonical case.

In ambient the display dims and updates drop to roughly once a minute. Design a second, minimal
rendering: high contrast, few lit pixels, no animation, no colour dependence.

Verification Google specifically asks for:

- Confirm the screen actually goes off within the timeout with always-on **disabled** in settings.
- Use the **Power Profiler** to inspect a system trace **as the screen goes off**, looking for work
  that keeps running.
- With `ExerciseClient`, verify via Battery Historian that the app does not wake **more than every
  minute or two** in ambient.

---

## 7. Health Services

For anything sensor-driven, use **Health Services** rather than `SensorManager` — it batches
delivery so the SoC wakes less often.

- **`ExerciseClient`** — active, user-initiated sessions.
- **`PassiveMonitoringClient`** — all-day background monitoring.

Verify unregistration the way Google describes: swipe-dismiss the app, or touch the screen with
your palm while it is off/ambient, then

```bash
adb shell dumpsys sensorservice
```

and confirm your listener is gone.

---

## 8. Data Layer — talking to the phone

Google's framing is that the **phone should do the heavy lifting** (network, sync) and the watch
receives changes.

- Be **conservative**: every transmission uses power and **wakes the paired device**.
- Set up your `WearableListenerService` listener **only when the app is active**.
- **Transmit state changes, not raw streams.** The documented example: if you show kilometres to
  one decimal, do not send an update per metre.
- Only transmit changes that **actually update the UI**.

```bash
adb shell dumpsys activity service WearableService
#   RpcService  -> MessageClient frequency and paths
#   DataService -> DataClient item frequency
```

**Standalone vs paired:** decide explicitly. A standalone app must work with no phone (and needs
its own auth and connectivity story); a paired app can delegate. Declare it honestly in the
manifest — `com.google.android.wearable.standalone` — because it changes install behaviour.

---

## 9. Watch faces

A separate product with its own format and **enforced** memory limits:

- **Watch Face Format: 10 MB maximum in ambient mode, 100 MB in interactive mode.**

If you are shipping a watch face, those are hard ceilings, not guidance. See
`memory.md` §10.

---

## 10. Wear OS vs Zebra — the differences that trip people up

| | **Zebra WS50 / WS501** | **Wear OS 6** |
|---|---|---|
| Platform | full Android AOSP | Wear OS |
| **GMS** | **no** | **yes** |
| FCM push | unavailable | available |
| Screen shape | **square** | **round** |
| Canvas | fixed **230 dp** | **192–240+ dp**, must adapt |
| Margins | absolute dp fine | **percentages required** |
| Lists | `RecyclerView` / `LazyColumn` | **`TransformingLazyColumn`** |
| Curvature transforms | ❌ wrong — flat panel | ✅ required |
| Back | **hardware key event** | **swipe-to-dismiss** |
| Extra surfaces | none | tiles, complications, watch faces |
| Ambient mode | n/a | first-class |
| Sensors | `SensorManager` | **Health Services** |
| Scanning | **DataWedge / SE4770 imager** | none |
| Battery target | **shift** (~10 h), hot-swap on WS501 | **full day**, < 3.2 %/h |
| Deployment | MDM / StageNow | Play Store |

**The two share a design philosophy — one task per screen, glanceable, ruthless about power — and
almost no code below the ViewModel.** `toolkit-choice.md` §5 covers how to structure a codebase
that must serve both.
