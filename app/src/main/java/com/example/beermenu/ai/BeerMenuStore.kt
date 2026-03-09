package com.example.beermenu.ai

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

object BeerMenuStore {

    private const val PREFS_NAME = "beermenu_prefs"
    private const val KEY_ENTRIES = "beer_entries"
    private const val COLLECTION = "biermenu"
    private const val DOCUMENT = "menu"

    private val firestore = FirebaseFirestore.getInstance()

    // Lokal in SharedPreferences speichern
    fun save(context: Context, entries: List<BeerEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("brewery", e.brewery)
                put("name", e.name)
                put("type", e.type)
                put("alcohol", e.alcohol)
                put("amount", e.amount)
                put("price", e.price)
                put("description", e.description)
                put("untappd", e.untappd)
                put("nameColor", e.nameColor)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }

    // Lokal aus SharedPreferences laden
    fun load(context: Context): List<BeerEntry> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return parseJson(json)
    }

    // In Firestore speichern
    suspend fun saveToCloud(entries: List<BeerEntry>) {
        val data = hashMapOf(
            "entries" to entries.map { e ->
                hashMapOf(
                    "id"          to e.id,
                    "brewery"     to e.brewery,
                    "name"        to e.name,
                    "type"        to e.type,
                    "alcohol"     to e.alcohol,
                    "amount"      to e.amount,
                    "price"       to e.price,
                    "description" to e.description,
                    "untappd"     to e.untappd,
                    "nameColor"   to e.nameColor
                )
            }
        )
        firestore.collection(COLLECTION).document(DOCUMENT).set(data).await()
        Log.d("BeerMenuStore", "Cloud save erfolgreich (${entries.size} Einträge)")
    }

    // Aus Firestore laden und lokalen Cache aktualisieren
    @Suppress("UNCHECKED_CAST")
    suspend fun loadFromCloud(context: Context): List<BeerEntry>? {
        return try {
            val snapshot = firestore.collection(COLLECTION).document(DOCUMENT).get().await()
            val raw = snapshot.get("entries") as? List<Map<String, Any>> ?: return null
            val entries = raw.map { m ->
                BeerEntry(
                    id          = m["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                    brewery     = m["brewery"] as? String ?: "",
                    name        = m["name"] as? String ?: "",
                    type        = m["type"] as? String ?: "",
                    alcohol     = m["alcohol"] as? String ?: "",
                    amount      = m["amount"] as? String ?: "3 dl",
                    price       = m["price"] as? String ?: "6.-",
                    description = m["description"] as? String ?: "",
                    untappd     = m["untappd"] as? String ?: "",
                    nameColor   = m["nameColor"] as? String ?: ""
                )
            }
            save(context, entries) // lokalen Cache aktualisieren
            Log.d("BeerMenuStore", "Cloud load erfolgreich (${entries.size} Einträge)")
            entries
        } catch (e: Exception) {
            Log.w("BeerMenuStore", "Cloud load fehlgeschlagen: ${e.message}")
            null
        }
    }

    private fun parseJson(json: String): List<BeerEntry> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                BeerEntry(
                    id          = o.optString("id", java.util.UUID.randomUUID().toString()),
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
            emptyList()
        }
    }
}
