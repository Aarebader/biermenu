package com.example.beermenu.utils

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object BitmapConverter {
    fun bitmapToByteArray(bitmap: Bitmap, quality: Int = 80): ByteArray {
        ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            return outputStream.toByteArray()
        }
    }
}
