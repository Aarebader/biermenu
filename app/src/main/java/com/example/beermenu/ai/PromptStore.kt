package com.example.beermenu.ai

import android.content.Context

object PromptStore {

    private const val PREFS_NAME = "beermenu_prefs"
    private const val KEY_PROMPT = "beer_menu_prompt"
    private const val KEY_PROMPT_VERSION = "beer_menu_prompt_version"
    private const val CURRENT_VERSION = 3

    const val DEFAULT_PROMPT = """Rolle: Du bist ein Experte für Datenextraktion (OCR) und ein erfahrener Beer-Sommelier.
Aufgabe: Extrahiere die Informationen von der abgebildeten Bierkarte.
Regeln:
- Obere Zeile: [Brauerei] und [Biername] (oft durch Punkt getrennt).
- Untere Zeile: [Stil/Hopfensorten] und [Alkoholgehalt in %].
- Zusatzinfos: °P oder IBU extrahieren.
- Menge: Standard 3 dl. Preis: Standard 6.- (falls nicht anders sichtbar).
- Beschreibung: 1-2 einladende Sätze auf Deutsch basierend auf Stil/Hopfen erfinden.
- Untappd: Rating von https://untappd.com hinzufügen, wenn kein rating gefunden wird, default 7
- nameColor: Hex-Farbe des Biernamens wie er auf der Karte erscheint (z.B. "#FFFFFF"), falls nicht erkennbar "#d0d0d0"
Output: Liefere AUSSCHLIESSLICH ein JSON-Array (keine Markdown-Blöcke, kein weiterer Text).
Felder pro Eintrag: name, brewery, type, alcohol, amount, price, description, untappd, nameColor"""

    fun load(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_PROMPT_VERSION, 0) < CURRENT_VERSION) {
            prefs.edit()
                .putString(KEY_PROMPT, DEFAULT_PROMPT)
                .putInt(KEY_PROMPT_VERSION, CURRENT_VERSION)
                .apply()
            return DEFAULT_PROMPT
        }
        return prefs.getString(KEY_PROMPT, DEFAULT_PROMPT) ?: DEFAULT_PROMPT
    }

    fun save(context: Context, prompt: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROMPT, prompt)
            .apply()
    }

    fun reset(context: Context) {
        save(context, DEFAULT_PROMPT)
    }
}
