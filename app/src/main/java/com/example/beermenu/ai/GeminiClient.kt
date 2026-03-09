package com.example.beermenu.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONArray

sealed class AnalysisResult {
    data class Success(val html: String, val entries: List<BeerEntry>) : AnalysisResult()
    data class Error(val message: String) : AnalysisResult()
}

class GeminiClient(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzeImage(image: Bitmap, prompt: String): AnalysisResult {
        val scaledImage = scaleBitmap(image, 1024)
        val inputContent = content {
            image(scaledImage)
            text(prompt)
        }

        return try {
            Log.d("GeminiClient", "Versuche Modell: gemini-2.5-flash (Google AI Studio)")
            val response = generativeModel.generateContent(inputContent)
            val raw = response.text

            if (scaledImage != image) scaledImage.recycle()

            if (raw == null) {
                return AnalysisResult.Error("Keine Antwort von der KI erhalten.")
            }

            Log.d("GeminiClient", "Erfolg mit Modell: gemini-2.5-flash")
            Log.d("GeminiClient", "Raw response (first 500): ${raw.take(500)}")
            val entries = parseEntries(raw)
            if (entries.isEmpty()) {
                Log.e("GeminiClient", "Keine Einträge geparst. Vollständige Antwort:\n$raw")
                return AnalysisResult.Error("Keine Biereinträge erkannt.\nKI-Antwort:\n${raw.take(300)}")
            }
            val html = HtmlGenerator.generate(entries)
            AnalysisResult.Success(html = html, entries = entries)
        } catch (e: Exception) {
            val msg = e.message ?: "Unbekannter Fehler"
            Log.e("GeminiClient", "API Studio Aufruf fehlgeschlagen: $msg", e)
            if (scaledImage != image) scaledImage.recycle()
            AnalysisResult.Error("Google AI Studio API fehlgeschlagen.\n$msg")
        }
    }

    private fun parseEntries(raw: String): List<BeerEntry> {
        // Robustly extract the JSON array even if the AI includes surrounding text
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) {
            Log.w("GeminiClient", "Kein JSON-Array gefunden in: ${raw.take(200)}")
            return emptyList()
        }
        val json = raw.substring(start, end + 1)
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                BeerEntry(
                    brewery     = o.optString("brewery"),
                    name        = o.optString("name"),
                    type        = o.optString("type"),
                    alcohol     = o.optString("alcohol"),
                    amount      = o.optString("amount", "3 dl"),
                    price       = o.optString("price", "6.-"),
                    description = o.optString("description"),
                    untappd     = o.optString("untappd"),
                    nameColor   = o.optString("nameColor")
                )
            }
        } catch (e: Exception) {
            Log.w("GeminiClient", "JSON-Parsing fehlgeschlagen: ${e.message}\nJSON: ${json.take(300)}")
            emptyList()
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = maxSize.toFloat() / Math.max(width, height)
        if (ratio >= 1.0) return bitmap
        return Bitmap.createScaledBitmap(bitmap, Math.round(ratio * width), Math.round(ratio * height), true)
    }
}
