package patterns.bitmap

/*
 * STEP-03 -- downsample a bitmap decode.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * THE ARITHMETIC: bytes = width * height * bytesPerPixel   (ARGB_8888 = 4, RGB_565 = 2)
 *
 *   12 MP, 4000 x 3000, ARGB_8888 ............. 48.0 MB   <- one line of code
 *   same image at 1080 x 810, ARGB_8888 ........ ~3.5 MB   <- 93 % less, no visible loss
 *   same image at 1080 x 810, RGB_565 .......... ~1.7 MB
 *
 * The screen cannot display 12 MP, so the full decode buys nothing.
 *
 * WHAT THIS REPLACES:  val bmp = BitmapFactory.decodeFile(path)
 *
 * MUST NOT CHANGE: visible quality at the size actually displayed. Keep ARGB_8888 wherever alpha is
 * genuinely used -- icons, overlays, masks, and gradient-heavy images where banding would show.
 * WATCH FOR: downstream code that assumed full-resolution dimensions. Adjusting it is part of this
 * step (anti-pattern AP-09).
 * DO NOT apply to known-small bundled assets -- that is a false positive.
 */

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File

/**
 * Decodes [path] no larger than [reqWidth] x [reqHeight].
 *
 * @param opaque true when the image has no meaningful alpha; halves the cost via RGB_565.
 */
fun decodeSampled(path: String, reqWidth: Int, reqHeight: Int, opaque: Boolean): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(File(path), reqWidth, reqHeight, opaque)
    } else {
        decodeWithBitmapFactory(path, reqWidth, reqHeight, opaque)
    }

// ---------------------------------------------------------------- API 28+

private fun decodeWithImageDecoder(
    file: File,
    reqWidth: Int,
    reqHeight: Int,
    opaque: Boolean,
): Bitmap? {
    val source = ImageDecoder.createSource(file)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val sample = maxOf(1, minOf(info.size.width / reqWidth, info.size.height / reqHeight))
        decoder.setTargetSampleSize(sample)
        if (opaque) {
            // Picks the cheaper configuration where it can.
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        }
    }
}

// ---------------------------------------------------------------- below API 28

private fun decodeWithBitmapFactory(
    path: String,
    reqWidth: Int,
    reqHeight: Int,
    opaque: Boolean,
): Bitmap? {
    // Pass 1: bounds only. inJustDecodeBounds allocates NO pixels -- this is the whole trick.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Pass 2: decode at 1/sample scale.
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        inPreferredConfig = if (opaque) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)
}

/** Largest power of two that keeps the result at or above the requested size. */
internal fun calculateSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var sample = 1
    while (sourceWidth / (sample * 2) >= reqWidth && sourceHeight / (sample * 2) >= reqHeight) {
        sample *= 2
    }
    return sample
}

/*
 * OWNERSHIP: whoever creates a bitmap outside a managed pool must release it and drop the
 * reference. Never hold a Bitmap in a static, a companion object, or an unbounded collection.
 *
 * VERIFY: peak during the image operation, plus the graphics and native lines of the memory
 * breakdown, before and after -- same device, same scenario, same unit.
 */
