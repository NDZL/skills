# AIDC Barcode — Integration (golden path)
> Source: https://techdocs.zebra.com/ai-datacapture/latest/setup/ + /barcodedecoder/ — AI Data Capture SDK 4.0, verified 2026-07-20. Identifiers verbatim. (Scaffold — expand as needed.)

## Gradle / Maven
```kotlin
// settings.gradle(.kts) repositories
maven { url = uri("https://zebratech.jfrog.io/artifactory/emc-mvn-ext") }

// libs.versions.toml
// aiSdkVersion   = "4.0.1"
// barcodeDecoder = "5.0.3"

// module build.gradle(.kts)
implementation("com.zebra.ai.sdk.vision:AI-Data-Capture-SDK:4.0.1") { artifact { type = "aar" } }
implementation("com.zebra.ai.models.vision:barcode-decoder:5.0.3")  { artifact { type = "aar" } }

android { androidResources { noCompress += listOf("tar", "tar.crypt") } }
// compileSdk / targetSdk = 34 (Android 14) or higher; CAMERA permission in the manifest
```

## Minimal decode (still image)
```java
import com.zebra.ai.vision.detector.BarcodeDecoder;

AIVisionSDK.getInstance(getApplicationContext()).init();
BarcodeDecoder.Settings settings = new BarcodeDecoder.Settings("barcode-decoder");
settings.Symbology.UPCE1.enable(true);                 // enable ONLY what you need

Executor executor = Executors.newFixedThreadPool(1);
BarcodeDecoder.getBarcodeDecoder(settings, executor).thenAccept(decoder -> {
    decoder.decode(bitmap, executor).thenAccept(res -> {
        String value = res.getvalue();
        int symbology = res.getSymbology();
    });
    decoder.dispose();
});
```

## Live camera (CameraX)
Use `EntityTrackerAnalyzer` (implements `ImageAnalysis.Analyzer`) on a CameraX `ImageAnalysis` use case for real-time detect + decode + track. See /camerax/.
TODO: add the EntityTrackerAnalyzer wiring snippet + lifecycle/dispose.
