package com.example.beermenu.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

object UploadClient {
    private const val TAG = "UploadClient"
    private val db = FirebaseFirestore.getInstance()

    suspend fun upload(html: String, image: Bitmap? = null): Result<String> {
        return try {
            val menuData = hashMapOf<String, Any>(
                "html_content" to html,
                "timestamp" to System.currentTimeMillis()
            )

            if (image != null) {
                val base64 = bitmapToBase64(image)
                Log.d(TAG, "Bild als Base64: ${base64.length / 1024} KB")
                menuData["image_base64"] = base64
            }

            db.collection("menus")
                .add(menuData)
                .await()

            Result.success("Upload zu Firebase erfolgreich!")
        } catch (e: Exception) {
            Log.e(TAG, "Upload fehlgeschlagen", e)
            Result.failure(e)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
