package patterns.images

/*
 * STEP-07 -- cap and tier an image loader cache.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * THE PROBLEM: image library defaults are a PERCENTAGE OF AVAILABLE HEAP, calibrated for consumer
 * phones. On a 3 GB device shared with the vendor software stack and a management agent, that
 * percentage is memory the workflow needed. Nothing warns you; it simply works on the developer's
 * 8 GB handheld and fails in the warehouse.
 *
 * DECISION NEEDED FROM THE DEVELOPER: the size per tier. Propose, do not assume.
 * MUST NOT CHANGE: which images are available -- only how many stay cached.
 *
 * This pattern is deliberately library-agnostic: it computes the budget, and then shows how to hand
 * that budget to whichever loader the project already uses. Do NOT add an image library to apply
 * this step (anti-pattern AP-10).
 */

import android.app.ActivityManager
import android.content.Context

/** Memory budget for one device, derived from what the device reports about itself. */
data class ImageBudget(
    val memoryCacheBytes: Long,
    val diskCacheBytes: Long,
    val preferOpaqueConfig: Boolean,
    val prefetchPages: Int,
) {
    companion object {

        /**
         * Tier from reported capability, NEVER from a model string: model names are unreliable and
         * multiply per region, and one binary must serve a 1 GB wearable and an 8 GB handheld.
         */
        fun forDevice(context: Context): ImageBudget {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val heapMb = activityManager?.memoryClass ?: 128
            val lowRam = activityManager?.isLowRamDevice ?: true

            return when {
                lowRam || heapMb <= 128 -> ImageBudget(
                    memoryCacheBytes = 2L * 1024 * 1024,
                    diskCacheBytes = 16L * 1024 * 1024,
                    preferOpaqueConfig = true,
                    prefetchPages = 0,
                )
                heapMb <= 256 -> ImageBudget(
                    memoryCacheBytes = 8L * 1024 * 1024,
                    diskCacheBytes = 32L * 1024 * 1024,
                    preferOpaqueConfig = true,
                    prefetchPages = 1,
                )
                else -> ImageBudget(
                    memoryCacheBytes = 24L * 1024 * 1024,
                    diskCacheBytes = 64L * 1024 * 1024,
                    preferOpaqueConfig = false,
                    prefetchPages = 1,
                )
            }
        }
    }
}

/*
 * APPLYING THE BUDGET
 *
 * The sizes above are a STARTING POINT for the developer to confirm, not a measured
 * recommendation -- there is no published per-device memory threshold for any Zebra tier.
 *
 * Coil-shaped loader:
 *
 *   val budget = ImageBudget.forDevice(context)
 *   ImageLoader.Builder(context)
 *       .memoryCache {
 *           MemoryCache.Builder(context).maxSizeBytes(budget.memoryCacheBytes.toInt()).build()
 *       }
 *       .diskCache {
 *           DiskCache.Builder()
 *               .directory(context.cacheDir.resolve("img"))
 *               .maxSizeBytes(budget.diskCacheBytes)
 *               .build()
 *       }
 *       .allowRgb565(budget.preferOpaqueConfig)
 *       .build()
 *
 * Glide-shaped loader, in an AppGlideModule:
 *
 *   override fun applyOptions(context: Context, builder: GlideBuilder) {
 *       val budget = ImageBudget.forDevice(context)
 *       builder.setMemoryCache(LruResourceCache(budget.memoryCacheBytes))
 *       builder.setDiskCache(InternalCacheDiskCacheFactory(context, budget.diskCacheBytes))
 *       if (budget.preferOpaqueConfig) {
 *           builder.setDefaultRequestOptions(RequestOptions().format(DecodeFormat.PREFER_RGB_565))
 *       }
 *   }
 *
 * Disk cache matters too: several Zebra tiers ship 32 GB of flash, so be modest.
 *
 * WIRE INTO PRESSURE HANDLING (STEP-05): clear the memory cache on TRIM_MEMORY_UI_HIDDEN. The
 * cache is pure anonymous memory -- compressible into zRAM but never droppable -- so releasing it
 * when the UI is hidden is close to free.
 *
 * VERIFY: steady-state RssAnon after browsing the image-heavy screen, before and after, same device
 * and same scenario.
 */
