package com.example.beermenu.ai

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Gespeicherte Bier-Attribute (description, type, alcohol, nameColor) pro Brewery+Name-Key.
 */
data class BeerAttributes(
    val description: String = "",
    val type: String = "",
    val alcohol: String = "",
    val nameColor: String = ""
)

object BeerDescriptionStore {

    private const val PREFS_NAME = "beermenu_prefs"
    private const val KEY_DESCRIPTIONS = "beer_descriptions"
    private const val COLLECTION = "biermenu"
    private const val DOCUMENT = "descriptions"

    private val firestore = FirebaseFirestore.getInstance()

    fun makeKey(brewery: String, name: String): String =
        "${brewery.trim().lowercase()}|${name.trim().lowercase()}"

    fun applyTo(entries: List<BeerEntry>, stored: Map<String, BeerAttributes>): List<BeerEntry> {
        Log.d("BeerDescStore", "applyTo: ${stored.size} stored keys, ${entries.size} entries")
        stored.keys.forEach { k -> Log.d("BeerDescStore", "  stored key: '$k'") }
        return entries.map { entry ->
            val key = makeKey(entry.brewery, entry.name)
            val attrs = stored[key]
            if (attrs == null) {
                Log.d("BeerDescStore", "  MISS key='$key' (brewery='${entry.brewery}', name='${entry.name}')")
                return@map entry
            }
            Log.d("BeerDescStore", "  HIT  key='$key' desc='${attrs.description.take(30)}'")
            entry.copy(
                description = attrs.description.ifBlank { entry.description },
                type        = attrs.type.ifBlank { entry.type },
                alcohol     = attrs.alcohol.ifBlank { entry.alcohol },
                nameColor   = attrs.nameColor.ifBlank { entry.nameColor }
            )
        }
    }

    fun applyToEntry(entry: BeerEntry, attrs: BeerAttributes): BeerEntry =
        entry.copy(
            description = attrs.description.ifBlank { entry.description },
            type        = attrs.type.ifBlank { entry.type },
            alcohol     = attrs.alcohol.ifBlank { entry.alcohol },
            nameColor   = attrs.nameColor.ifBlank { entry.nameColor }
        )

    fun extractFrom(entries: List<BeerEntry>): Map<String, BeerAttributes> =
        entries
            .filter { it.description.isNotBlank() || it.type.isNotBlank() || it.alcohol.isNotBlank() || it.nameColor.isNotBlank() }
            .associate {
                makeKey(it.brewery, it.name) to BeerAttributes(
                    description = it.description,
                    type        = it.type,
                    alcohol     = it.alcohol,
                    nameColor   = it.nameColor
                )
            }

    fun attrsOf(entry: BeerEntry): BeerAttributes =
        BeerAttributes(
            description = entry.description,
            type        = entry.type,
            alcohol     = entry.alcohol,
            nameColor   = entry.nameColor
        )

    // --- Local (SharedPreferences) ---

    fun saveLocal(context: Context, data: Map<String, BeerAttributes>) {
        val root = JSONObject()
        data.forEach { (key, attrs) ->
            root.put(key, JSONObject().apply {
                put("description", attrs.description)
                put("type", attrs.type)
                put("alcohol", attrs.alcohol)
                put("nameColor", attrs.nameColor)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DESCRIPTIONS, root.toString())
            .apply()
    }

    fun loadLocal(context: Context): Map<String, BeerAttributes> {
        return try {
            val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DESCRIPTIONS, null)
            if (json == null) {
                Log.d("BeerDescStore", "loadLocal: no data in SharedPreferences")
                return emptyMap()
            }
            val result = parseJson(json)
            Log.d("BeerDescStore", "loadLocal: ${result.size} entries loaded")
            result
        } catch (e: Exception) {
            Log.w("BeerDescStore", "loadLocal: parse error: ${e.message}")
            emptyMap()
        }
    }

    // --- Cloud (Firestore) ---

    suspend fun saveToCloud(data: Map<String, BeerAttributes>) {
        val map = data.mapValues { (_, attrs) ->
            hashMapOf(
                "description" to attrs.description,
                "type"        to attrs.type,
                "alcohol"     to attrs.alcohol,
                "nameColor"   to attrs.nameColor
            )
        }
        firestore.collection(COLLECTION).document(DOCUMENT).set(hashMapOf("data" to map)).await()
        Log.d("BeerDescriptionStore", "Cloud save erfolgreich (${data.size} Einträge)")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadFromCloud(context: Context): Map<String, BeerAttributes>? {
        return try {
            val snapshot = firestore.collection(COLLECTION).document(DOCUMENT).get().await()
            val raw = snapshot.get("data") as? Map<String, Any> ?: return null
            val result = raw.mapValues { (_, value) ->
                when (value) {
                    // Neues Format: verschachteltes Objekt
                    is Map<*, *> -> BeerAttributes(
                        description = value["description"] as? String ?: "",
                        type        = value["type"] as? String ?: "",
                        alcohol     = value["alcohol"] as? String ?: "",
                        nameColor   = value["nameColor"] as? String ?: ""
                    )
                    // Altes Format (Migration): nur description als String
                    is String -> BeerAttributes(description = value)
                    else -> BeerAttributes()
                }
            }
            saveLocal(context, result)
            Log.d("BeerDescriptionStore", "Cloud load erfolgreich (${result.size} Einträge)")
            result
        } catch (e: Exception) {
            Log.w("BeerDescriptionStore", "Cloud load fehlgeschlagen: ${e.message}")
            null
        }
    }

    // --- Helpers ---

    private fun parseJson(json: String): Map<String, BeerAttributes> {
        val root = JSONObject(json)
        return root.keys().asSequence().associate { key ->
            val value = root.get(key)
            key to when (value) {
                // Neues Format
                is JSONObject -> BeerAttributes(
                    description = value.optString("description"),
                    type        = value.optString("type"),
                    alcohol     = value.optString("alcohol"),
                    nameColor   = value.optString("nameColor")
                )
                // Altes Format (Migration): nur description als String
                is String -> BeerAttributes(description = value)
                else -> BeerAttributes()
            }
        }
    }
}
