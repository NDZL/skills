package fixtures.step09

/*
 * FIXTURE for STEP-09 ordering -- the allocation that largeHeap is currently masking.
 *
 * See AndroidManifest.xml in this directory for the expected skill behaviour.
 *
 * The decode below is a proof-of-delivery photo straight from the device camera: 12 MP, so
 * 4000 x 3000 x 4 bytes = 48 MB in a single allocation, at ARGB_8888 with no downsampling.
 *
 * CORRECT ORDER:  STEP-03 (downsample this) -> verify -> STEP-09 (remove largeHeap) -> verify
 * WRONG ORDER:    STEP-09 first -> the app fails sooner, on more devices, immediately.
 */

import android.graphics.Bitmap
import android.graphics.BitmapFactory

class PhotoUploader(private val api: UploadApi) {

    /** Called after the camera returns a proof-of-delivery photo. */
    fun upload(path: String) {
        // 48 MB for one 12 MP image. The screen this is previewed on cannot display 12 MP.
        val full: Bitmap = BitmapFactory.decodeFile(path)

        api.send(full)
        // No recycle(), and the reference is held until the method returns.
    }
}

interface UploadApi {
    fun send(bitmap: Bitmap)
}
