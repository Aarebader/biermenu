# Projekt Zusammenfassung: "Beer Menu" Scanner App

Diese Datei dient als technischer Einstiegspunkt und Dokumentation für zukünftige Arbeiten mit Gemini CLI.

## 1. Projekt-Übersicht
- **Ziel:** Eine Android-App, die eine physische Bierkarte (z.B. eine Kreidetafel) abfotografiert, mittels Google Gemini KI (Optical Character Recognition & Structuring) ausliest, als dunkles HTML-Tabellen-Design formatiert und in Echtzeit in eine Cloud-Datenbank hochlädt, sodass sie sofort auf einer Webseite sichtbar ist.
- **Plattformen:** Android (Kotlin, Jetpack Compose) & Web (HTML/JS)
- **Status:** Phase 4 (Upload & Web-Vorschau) vollständig abgeschlossen. Die App funktioniert End-to-End.

## 2. Architektur & Tech-Stack (Android App)
Das Projekt befindet sich im Ordner `/home/schwyter/biermenu/app`.
- **UI-Framework:** Jetpack Compose (Modernes, deklaratives UI)
- **Kamera:** Android CameraX (in `CameraManager.kt` & `CameraPreview.kt`)
- **KI-Integration:** Google AI Studio SDK (`com.google.ai.client.generativeai`)
  - **Modell:** `gemini-2.5-flash` (Kostenloses Free-Tier über AI Studio)
  - **Klasse:** `GeminiClient.kt` (Enthält den fest verdrahteten System-Prompt für die Datenextraktion und die Bild-Skalierung auf max 1024px zur Token-Ersparnis).
  - **API-Key:** Ist aktuell als String in `MainScreen.kt` hinterlegt (Sollte für spätere Releases in die `local.properties` oder `BuildConfig` ausgelagert werden).
- **Datenbank & Upload:** Firebase Firestore (`com.google.firebase:firebase-firestore`)
  - **Klasse:** `UploadClient.kt`
  - **Ablauf:** Speichert das generierte HTML als String zusammen mit einem UNIX-Timestamp in der Collection `menus`.

## 3. Web-Frontend (Firebase Hosting)
Das Projekt befindet sich im Ordner `/home/schwyter/biermenu/biermenu_web`.
- **Ziel:** Eine rudimentäre Single-Page-Application (SPA), die das neueste HTML aus Firestore lädt und anzeigt.
- **Hosting:** Firebase Hosting (Spark Plan - Kostenlos).
- **Dateien:** `index.html` (Enthält die Firebase Web-Config, verbindet sich mit Firestore, zieht das neueste Dokument aus der Collection `menus` (sortiert nach `timestamp` absteigend, Limit 1) und überschreibt das eigene DOM mit dem KI-generierten Code).
- **Live-URL:** `https://biermenu.web.app`

## 4. Bekannte Herausforderungen & Lösungen (Historie)
- **API 404 Fehler (Gemini):** Ursprünglich wurde das Firebase Vertex AI SDK versucht. Dieses erfordert jedoch ein kostenpflichtiges Blaze-Abo. Die Lösung war der Wechsel zurück auf das Google AI Studio SDK mit einem kostenlosen API-Key. Außerdem wurde das Modell von `gemini-1.5-flash` auf `gemini-2.5-flash` aktualisiert, da die 1.5er Version unter dem verwendeten Key einen 404-Fehler warf.
- **Berechtigungen:** Das Tooling (`firebase-tools`) musste auf dem Linux-System mit `sudo` installiert werden (`npm error code EACCES`).

## 5. Nächste mögliche Schritte / To-Dos
Wenn dieses Projekt in Zukunft weiterentwickelt wird, bieten sich folgende Punkte an:
- [ ] **Sicherheit:** Den statischen API-Key in `MainScreen.kt` in die `local.properties` auslagern (Nutzung des `Secrets Gradle Plugin`).
- [ ] **Design (Android):** Das Compose-UI im `MainScreen.kt` aufhübschen (Material 3 Theming anwenden, bessere Abstände).
- [ ] **Web-Integration:** Das aktuelle Skript in `biermenu_web/index.html` so anpassen, dass es als iFrame oder Web-Component in eine *bestehende* Brauerei-Webseite eingebettet werden kann, anstatt den gesamten Bildschirm (`document.write`) zu überschreiben.
- [ ] **Fehlerbehandlung:** Bessere visuelle Fehlermeldungen in der App, falls das Foto unscharf ist oder die KI kein Bier erkennt.
