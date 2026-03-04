package com.example.beermenu.network

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object UploadClient {
    // Initialisiere die Firestore Instanz
    private val db = FirebaseFirestore.getInstance()

    suspend fun upload(html: String): Result<String> {
        return try {
            // Wir erstellen ein Dokument mit dem generierten HTML-Code und einem Zeitstempel
            val menuData = hashMapOf(
                "html_content" to html,
                "timestamp" to System.currentTimeMillis()
            )

            // "menus" ist der Name der Collection (Tabelle) in der Firestore Datenbank
            db.collection("menus")
                .add(menuData)
                .await() // Warte, bis der Upload abgeschlossen ist

            Result.success("Upload zu Firebase erfolgreich!")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
