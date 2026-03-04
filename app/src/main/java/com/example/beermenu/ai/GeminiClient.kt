package com.example.beermenu.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiClient(private val apiKey: String) {

    // Wir nutzen hier das offizielle Google AI Studio SDK (GenerativeModel),
    // dieses ist kostenlos und erfordert kein Firebase Blaze-Abo.
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzeImage(image: Bitmap, prompt: String): String? {
        val scaledImage = scaleBitmap(image, 1024)
        val inputContent = content {
            image(scaledImage)
            text(prompt)
        }

        return try {
            Log.d("GeminiClient", "Versuche Modell: gemini-2.5-flash (Google AI Studio)")
            val response = generativeModel.generateContent(inputContent)
            var result = response.text
            
            if (result != null) {
                Log.d("GeminiClient", "Erfolg mit Modell: gemini-2.5-flash")
                if (scaledImage != image) scaledImage.recycle()
                return result.replace("```html", "", ignoreCase = true).replace("```", "").trim()
            }
            null
        } catch (e: Exception) {
            val msg = e.message ?: "Unbekannter Fehler"
            Log.e("GeminiClient", "API Studio Aufruf fehlgeschlagen: $msg", e)
            
            if (scaledImage != image) scaledImage.recycle()
            "ERROR_DETAILS: Google AI Studio API fehlgeschlagen.\n$msg"
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = maxSize.toFloat() / Math.max(width, height)
        if (ratio >= 1.0) return bitmap
        return Bitmap.createScaledBitmap(bitmap, Math.round(ratio * width), Math.round(ratio * height), true)
    }

    companion object {
        const val BEER_MENU_PROMPT = """Rolle: Du bist ein Experte für Datenextraktion (OCR) und ein erfahrener Beer-Sommelier.
Aufgabe: Extrahiere die Informationen von der abgebildeten Bierkarte und erstelle eine strukturierte HTML-Tabelle.
Regeln:
- Obere Zeile: [Brauerei] und [Biername] (oft durch Punkt getrennt).
- Untere Zeile: [Stil/Hopfensorten] und [Alkoholgehalt in %].
- Zusatzinfos: °P oder IBU extrahieren.
- Menge: Standard 3 dl. Preis: Standard 6.- (falls nicht anders sichtbar).
- Beschreibung: 1-2 einladende Sätze basierend auf Stil/Hopfen erfinden.
- Untappd: Fiktives oder echtes Rating hinzufügen.
Output: Liefere AUSSCHLIESSLICH vollständigen HTML-Code (inklusive CSS im <style> Block für ein dunkles, modernes Design). Keine Markdown-Blöcke außen herum."""
    }
}
