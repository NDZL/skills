# Screen and layout — designing *only* for a 230 dp canvas

> **Provenance.** All Zebra numeric values are quoted in `device-matrix.md` §2 and §4 from the WS50
> Programmer's Guide; all Wear OS values in `device-matrix.md` §6 from developer.android.com.
> This file turns those numbers into layout rules. It adds no new device facts.

---

## 1. Small-screen-*only* is a different discipline from responsive

This skill is for building an app that **only ever runs on a watch-sized screen**. That is not a
constrained version of responsive design — it is a different job, and it is a *easier* one if you
commit to it.

| | Responsive / adaptive app | **Small-screen-only app (this skill)** |
|---|---|---|
| Layout authority | breakpoints; layout must survive any width | **one fixed canvas: 230 × 230 dp** |
| Content decisions | progressive disclosure by width | **decided once, at design time** |
| Density buckets shipped | mdpi → xxxhdpi | **xhdpi only** |
| Rotation | must handle both | **locked to portrait** |
| Navigation | drawer / tabs / rail by width | **one hardware Back + linear steps** |
| Test matrix | many form factors | **one, occasionally two** |
| Typical failure | works everywhere, optimal nowhere | *(none — if you follow the canvas)* |

**What you gain by dropping adaptivity:** you can hard-commit to exact dp values, ship one
drawable bucket, delete every `ConstraintSet`/`sw*dp` variant, and — most importantly — make
*content* decisions instead of *layout* decisions. On a 230 dp screen the scarce resource is not
pixels, it is the user's attention for the two seconds they look at their wrist.

**What you must not do:** take a phone layout and shrink it. That is the failure mode this skill
exists to prevent, and it is the subject of `anti-patterns.md`.

> ⚠ **Zebra's own words:** `"Its two-inch display and 1GB RAM require significant modifications to`
> `existing apps, UIs and workflows."` The guide says *workflows*, not just UIs. If you only change
> the layout, you have done a third of the work.

---

## 2. The canvas, and what actually fits

### 2.1 Zebra WS50 / WS501

```
460 × 460 px  ÷ 2  (320 dpi bucket)  =  230 × 230 dp   ← square
no status bar · no navigation bar · portrait only · no split-screen
```

Design assets at **230 × 230 dp exported at 2×**, exactly as Zebra specifies. Ship **xhdpi**
drawables and nothing else — every other bucket is dead weight in an 8 GB device (WS50).

### 2.2 The information budget — measure once, then design to it

With **12 dp** side padding you have **206 dp** of usable content width. Approximate capacities on
that width:

| Element | Approximate capacity on a 230 dp canvas |
|---|---|
| Characters per line @ 14 sp | **~24–28** |
| Lines of 14 sp body text (with a title bar) | **~8** |
| Full-height 60 dp touch rows visible at once | **~3**, plus a partial 4th that hints "scroll" |
| Buttons on one screen | **2–4 maximum** (Zebra's stated limit) |
| Simultaneous data fields a user can absorb | realistically **1 primary + 2 secondary** |

> These capacities are **derived**, not quoted from Zebra. Verify them against your actual typeface
> and font-scale settings before treating them as contractual — `Paint.measureText()` or a
> `@Preview` at 230 dp settles it in a minute. The *shape* of the constraint is what matters:
> you have room for one idea per screen.

### 2.3 Wear OS

```
192 dp ─────── 204–216 dp ─────── 225 dp ─────── 240+ dp
  ▲               ▲                  ▲
  │               │                  └─ LARGE_DISPLAY_BREAKPOINT
  │               └─ design here FIRST (smallest supported round)
  └─ test here with enlarged fonts
```

Wear OS is the one place where a *little* adaptivity is mandatory rather than optional, because
the round bezel clips absolute margins. Use **percentage margins**, and remember the hard rule:
**a larger screen must never show less information than a smaller one.**

---

## 3. Touch targets — the rule

Derived in `device-matrix.md` §4, where the source conflict is documented and resolved.

| Situation | Minimum |
|---|---|
| **Absolute floor, anywhere** | **48 dp** |
| Normal interactive control | **60 dp** |
| Control near a screen edge | **80 dp** |
| **Gloved operation** | **64 dp**, prefer **80 dp** |

**Never use the "30 × 30 dp" figure that appears in the WS50 guide** — it contradicts the same
guide's own 48 dp floor and its own quoted fingertip diameter of 8–10 mm (≈51–65 dp). A finger
cannot reliably hit 30 dp.

Three practical consequences:

1. **Edge controls need to be bigger, not smaller.** The instinct on a tiny screen is to tuck
   controls into corners. Zebra says the opposite: near an edge, go to **80 dp**. Touch accuracy
   degrades at the bezel, and on a wrist-worn device the approach angle is awkward.
2. **The touch target and the visual are separate things.** A 24 dp icon can — and should — live
   inside a 60 dp target. Grow the target, not the glyph.
3. **Ask whether the app will be operated gloved before you pick a number.** Cold-chain and
   warehouse deployments are gloved by default; this changes 48 dp into 64–80 dp and can change
   how many controls fit on a screen from four to two. It is a *required input*, not a nicety.

Expanding a target without changing the visual:

```xml
<!-- Views: the target is the whole 60dp button; the glyph is 24dp inside it -->
<ImageButton
    android:layout_width="60dp"
    android:layout_height="60dp"
    android:padding="18dp"
    android:scaleType="fitCenter"
    android:background="?attr/selectableItemBackground"
    android:src="@drawable/ic_confirm"
    android:contentDescription="@string/confirm" />
```

```kotlin
// Compose: minimumInteractiveComponentSize enforces the floor; go beyond it explicitly
Box(
    modifier = Modifier
        .size(60.dp)                       // the touch target
        .clip(CircleShape)
        .clickable(onClick = onConfirm),
    contentAlignment = Alignment.Center,
) {
    Icon(
        painter = painterResource(R.drawable.ic_confirm),
        contentDescription = stringResource(R.string.confirm),
        modifier = Modifier.size(24.dp),   // the visual
    )
}
```

---

## 4. Typography

Verbatim from Zebra: `"Body copy: 14pt"`, `"Captions: 12pt"`,
`"No text should be smaller than 12pt"`.

| Role | Size | Notes |
|---|---|---|
| Primary value (the one thing the screen is *for*) | **20–24 sp** | readable at a glance, at arm's length |
| Body copy | **14 sp** | Zebra's stated body size |
| Caption / label | **12 sp** | **hard floor — never go below** |

Use **sp**, not dp, for text so font-scale accessibility settings work. Then handle the
consequence: Wear OS documentation warns that UI element height changes **non-linearly** with font
scaling and bold-text accessibility settings. A layout that fits at 1.0× can overflow at 1.3×.

**Rules that follow from a 24–28 character line:**

- **One line for anything a user must read at a glance.** Two lines is a paragraph here.
- **Never truncate the identifying part of a string.** For SKUs, order numbers and license plates
  the *end* usually carries the discriminating digits, so `TextUtils.TruncateAt.MIDDLE` beats
  `END`. `SKU-000…-0042` is useful; `SKU-00000000…` is not.
- **Numbers over prose.** `12 / 40` communicates in a quarter of the space of
  `12 of 40 items picked`.
- **Front-load.** The first 12 characters carry the message; assume the rest may be clipped at a
  large font scale.

```xml
<TextView
    android:id="@+id/sku"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:maxLines="1"
    android:ellipsize="middle"
    android:textSize="14sp"
    tools:text="SKU-00000000-0042" />
```

---

## 5. Grid, spacing, padding

Verbatim from Zebra:

- `"Most measurements should align to an 8dp grid"`
- `"For iconography, typography and other small components, use a 4dp grid"`
- `"To avoid an 'overcrowded' UI, set padding at 10–16dp"`
- `"Separate UI components by 12 pixels (6 dp)"`

Define these once and never type a raw dp again — it is also how you keep a design review honest:

```xml
<!-- res/values/dimens.xml -->
<resources>
    <!-- canvas -->
    <dimen name="canvas_size">230dp</dimen>

    <!-- grid: 8dp primary, 4dp for small components -->
    <dimen name="grid_half">4dp</dimen>
    <dimen name="grid_1">8dp</dimen>
    <dimen name="grid_2">16dp</dimen>

    <!-- Zebra: padding 10-16dp; component separation 6dp -->
    <dimen name="screen_padding">12dp</dimen>
    <dimen name="component_gap">6dp</dimen>

    <!-- touch targets: see device-matrix.md §4 -->
    <dimen name="touch_min">48dp</dimen>
    <dimen name="touch_normal">60dp</dimen>
    <dimen name="touch_edge">80dp</dimen>
    <dimen name="touch_gloved">64dp</dimen>

    <!-- type -->
    <dimen name="text_primary">20sp</dimen>
    <dimen name="text_body">14sp</dimen>
    <dimen name="text_caption">12sp</dimen>
</resources>
```

There is real tension between `screen_padding` (12 dp × 2 = 24 dp of the 230 dp gone) and fitting
content. Resolve it in favour of the padding — an edge-to-edge layout on a bezelled wrist device
reads as broken and the outermost pixels are the hardest to touch.

---

## 6. Layout structure: one screen, one task

Zebra: `"Break workflows into single-screen steps"`, `"limit buttons (2-4 maximum)"`,
`"Design single-task UIs"`. The canonical structure that satisfies all three:

```
┌──────────────────────────────┐  230 dp
│  context     12sp    ~20dp   │  where am I / step 2 of 5
├──────────────────────────────┤
│                              │
│   THE ONE THING     20-24sp  │  the primary value, big
│   secondary         14sp     │  at most two supporting lines
│                              │
├──────────────────────────────┤
│  ┌────────────┐ ┌──────────┐ │
│  │  primary   │ │ secondary│ │  60dp tall, 2 max side by side
│  └────────────┘ └──────────┘ │
└──────────────────────────────┘
```

**Use `ConstraintLayout`** — Zebra explicitly recommends it "for better performance", and a flat
hierarchy matters more here than anywhere (see `cpu-performance.md` §3).

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="@dimen/screen_padding">

    <TextView
        android:id="@+id/context"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:maxLines="1"
        android:ellipsize="end"
        android:textSize="@dimen/text_caption"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <TextView
        android:id="@+id/primary"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:maxLines="1"
        android:ellipsize="middle"
        android:textSize="@dimen/text_primary"
        app:layout_constraintTop_toBottomOf="@id/context"
        app:layout_constraintBottom_toTopOf="@id/actions"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintVertical_bias="0.35" />

    <!-- actions pinned to the bottom: the thumb's natural reach -->
    <LinearLayout
        android:id="@+id/actions"
        android:layout_width="match_parent"
        android:layout_height="@dimen/touch_normal"
        android:orientation="horizontal"
        android:divider="@null"
        app:layout_constraintBottom_toBottomOf="parent">

        <Button
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="match_parent"
            android:layout_marginEnd="@dimen/component_gap"
            android:text="@string/confirm" />

        <Button
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="match_parent"
            android:text="@string/skip" />
    </LinearLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

### 6.1 Lock the orientation and opt out of resizing

A small-screen-only app has no business rotating or being resized.

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="portrait"
    android:resizeableActivity="false"
    android:configChanges="keyboardHidden|orientation|screenSize" />
```

```xml
<!-- and, since Zebra says avoid split-screen -->
<application android:resizeableActivity="false" ... >
```

---

## 7. Navigation with no navigation bar

On WS50/WS501 `"status bar is hidden and Navigation bar removed"`. Navigation is **hardware keys**
plus your own linear flow.

- **Back arrives as a key event** (default: left button). Handle it or the user is trapped.
- **Home** (default: right button) will leave your app — you cannot prevent it, so never rely on
  your process staying alive (`memory.md` §4).
- **Long-press right = power.** Do not put a destructive action on a long-press of that button.

```kotlin
// Modern, lifecycle-correct Back handling. Do NOT override onBackPressed().
class PickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.canGoToPreviousStep()) {
                viewModel.previousStep()
            } else {
                isEnabled = false                       // let the system handle it
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }
}
```

Programmable buttons are mapped through Zebra's key-remapping facilities (Key Programmer / MX),
which are **out of scope for this skill** — but be aware that a customer may have remapped the
Back button away, so also expose an on-screen way back when a flow can strand the user.

**Navigation patterns that fit 230 dp:**

| Pattern | Verdict |
|---|---|
| **Linear wizard** (step 1 → 2 → 3) | ✅ the default choice |
| **Single scrolling list → detail** | ✅ works well |
| Bottom navigation bar | ❌ costs ~56 dp (24 % of the canvas) for navigation chrome |
| Navigation drawer | ❌ needs an edge swipe and a hamburger you have no room for |
| Tabs | ❌ labels are unreadable at 12 sp in 3 columns |
| Nested navigation > 2 levels | ❌ users lose their place with no breadcrumb room |

**On Wear OS**, navigation is different again: horizontal swipe is **swipe-to-dismiss** (reserved
by the system). Use `AppScaffold` / `ScreenScaffold`, and `SwipeDismissableNavHost` — or the new
**Wear Compose Navigation3** (`androidx.wear.compose` 1.6.0), which integrates `NavDisplay` and
`SceneStrategy` with Wear's swipe-to-dismiss logic.

---

## 8. Lists

Roughly **3 rows of 60 dp** are visible at once. That has design consequences:

- **Sort and filter server-side or in the ViewModel.** The user will not scroll 200 rows on a
  wrist. If the right item is not in the first three, the list is the wrong UI — use a scan or a
  search-by-code instead.
- **One line per row.** Two-line rows halve an already tiny viewport.
- **Never paginate with a "load more" button.** It costs a whole row and a tap.
- **Show a partial 4th row** so the affordance to scroll is visible without a scrollbar.

```kotlin
// Zebra (square screen): a plain RecyclerView/LazyColumn is correct.
LazyColumn(
    contentPadding = PaddingValues(vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    items(rows, key = { it.id }) { row -> PickRow(row) }
}
```

```kotlin
// Wear OS (round screen): use the Wear-specific list, which handles curvature + responsive padding
val columnState = rememberTransformingLazyColumnState()
val transformationSpec = rememberTransformationSpec()

ScreenScaffold(scrollState = columnState) { contentPadding ->
    TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
        items(rows, key = { it.id }) { row ->
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec)
                    .minimumVerticalContentPadding(
                        ButtonDefaults.minimumVerticalListContentPadding
                    ),
                transformation = SurfaceTransformation(transformationSpec),
                onClick = { onSelect(row) },
            ) {
                Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
```

⚠ **Do not use `TransformingLazyColumn`/`ScalingLazyColumn` on the Zebra square screen.** Their
scaling and curvature transforms exist to compensate for a round bezel; on a square 230 dp panel
they shrink your content for no reason.

---

## 9. Feedback and transient UI

You have no status bar for notifications and no room for a snackbar that does not cover your
content. Replace each phone idiom deliberately:

| Phone idiom | On a 230 dp canvas |
|---|---|
| Toast | acceptable but easily missed — pair with haptics |
| Snackbar + action | ❌ covers ~25 % of the screen; make it a screen state instead |
| Modal dialog | only for genuinely destructive confirmation, full-screen, 2 buttons max |
| Progress spinner in a dialog | ❌ inline state on the screen that is already showing |
| Notification | mostly pointless with the status bar hidden |
| Form validation under the field | ❌ no room; validate on submit, show as a screen state |

**Use the channels the hardware gives you** — they cost no pixels:

- **Haptics** for every state change the user must notice. On a wrist-worn device this is the
  highest-bandwidth output you have.
- **LEDs** via Zebra's AIDL interface — left/right multi-colour, **solid only, no blinking**. Green
  for accepted, red for rejected is instantly legible and costs zero screen area.
- **Short audio tone** for success/failure, where the environment permits.

---

## 10. Colour

Verbatim from Zebra: `"Blue is the most challenging color for the WS50 ... panel to render"` and
`"Use 'Full Black'"` to maximize battery operation. (`device-matrix.md` §4.3 notes the source says
"LCD" where the display is AMOLED; the advice holds for AMOLED.)

- **Background: true black `#FF000000`.** On AMOLED this switches pixels off — it is
  simultaneously the best contrast and the biggest battery lever you control (`battery-power.md` §3).
- **Avoid blue as a primary or a semantic colour.** Blue subpixels are the least efficient and
  age fastest. This kills the usual "blue = primary action" convention — use a light-on-black
  neutral for primary and reserve saturated colour for status.
- **Do not rely on colour alone** for status: gloves, sunlight, safety glasses and colour-blindness
  all degrade it. Pair with an icon or a word.
- **Aim high on contrast** — 4.5:1 is a floor, not a target, for a device used outdoors and at
  arm's length.

```xml
<!-- res/values/themes.xml -->
<style name="Theme.SmallScreen" parent="Theme.Material3.Dark.NoActionBar">
    <item name="android:colorBackground">#FF000000</item>   <!-- true black: pixels off -->
    <item name="colorSurface">#FF000000</item>
    <item name="colorOnBackground">#FFFFFFFF</item>
    <item name="android:windowBackground">#FF000000</item>
    <item name="android:statusBarColor">#FF000000</item>
</style>
```

Avoid large areas of white. A full-white screen is the worst case for both battery and outdoor
glare on this hardware.

---

## 11. Verify the layout

**Zebra — run it at the real size.** An emulator at 460 × 460 / 320 dpi is a faithful proxy for
layout (not for memory or CPU):

```bash
# AVD hardware profile: 460x460, 320 dpi, no skin, portrait-locked
adb shell wm size      # confirm 460x460
adb shell wm density   # confirm 320

# Check your own layout depth — see cpu-performance.md §3
adb shell dumpsys gfxinfo <your.package> framestats
```

Also test at the largest font scale you support, because element heights grow non-linearly:

```bash
adb shell settings put system font_scale 1.3
# ... exercise every screen ...
adb shell settings put system font_scale 1.0
```

**Wear OS — use the provided preview and screenshot tooling**, which covers the device sizes and
font scales for you:

```kotlin
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun PickScreenPreview() { PickScreen() }
```

```kotlin
@RunWith(ParameterizedRobolectricTestRunner::class)
class PickScreenTest(override val device: WearDevice) : WearScreenshotTest() {
    override val tolerance = 0.02f

    @Test fun screen() = runTest { AppScaffold { PickScreen() } }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters
        fun devices() = WearDevice.entries
    }
}
```

```bash
./gradlew recordRoborazziDebug    # generate goldens
./gradlew verifyRoborazziDebug    # verify against them
```

**A layout is not done until it has been checked on the real device, gloved if the deployment is
gloved.** An emulator cannot tell you that a 48 dp target is unhittable through a thermal glove.
