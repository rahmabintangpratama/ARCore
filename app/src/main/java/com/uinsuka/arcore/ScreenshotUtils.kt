package com.uinsuka.arcore

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import io.github.sceneview.ar.ArSceneView
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun ArSceneView.getBitmap(callback: (Bitmap?) -> Unit) {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    try {
        // Make sure we're running on Android O or higher
        val handler = Handler(Looper.getMainLooper())

        PixelCopy.request(
            this, // source
            bitmap, // destination
            { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    callback(bitmap)
                } else {
                    callback(null)
                }
            },
            handler
        )
    } catch (e: Exception) {
        callback(null)
    }
}

// Opsional: Versi suspend function jika ingin menggunakan dengan coroutines
suspend fun ArSceneView.getBitmapSuspend(): Bitmap? = suspendCoroutine { continuation ->
    getBitmap { bitmap ->
        continuation.resume(bitmap)
    }
}