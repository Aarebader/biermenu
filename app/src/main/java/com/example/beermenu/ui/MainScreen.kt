package com.example.beermenu.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import com.example.beermenu.BuildConfig
import com.example.beermenu.ai.AnalysisResult
import com.example.beermenu.ai.BeerAttributes
import com.example.beermenu.ai.BeerEntry
import com.example.beermenu.ai.BeerDescriptionStore
import com.example.beermenu.ai.BeerMenuStore
import com.example.beermenu.ai.GeminiClient
import com.example.beermenu.ai.PromptStore
import com.example.beermenu.camera.CameraManager
import com.example.beermenu.network.UploadClient
import kotlinx.coroutines.launch

private sealed class Screen {
    object Menu : Screen()
    object Camera : Screen()
    object EditPrompt : Screen()
    object EditMenu : Screen()
    object ShowMenu : Screen()
    data class Preview(val bitmap: Bitmap) : Screen()
    data class Processing(val bitmap: Bitmap) : Screen()
    data class Result(val html: String) : Screen()
    data class Error(val message: String) : Screen()
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    val geminiClient = remember { GeminiClient(BuildConfig.GEMINI_API_KEY) }

    // Beim App-Start: BeerDescriptionStore aus Firestore bootstrappen
    LaunchedEffect(Unit) {
        if (BeerDescriptionStore.loadLocal(context).isEmpty()) {
            val cloudDescs = BeerDescriptionStore.loadFromCloud(context)
            if (cloudDescs.isNullOrEmpty()) {
                val cloudEntries = BeerMenuStore.loadFromCloud(context)
                if (cloudEntries != null) {
                    val extracted = BeerDescriptionStore.extractFrom(cloudEntries)
                    if (extracted.isNotEmpty()) {
                        BeerDescriptionStore.saveLocal(context, extracted)
                        try { BeerDescriptionStore.saveToCloud(extracted) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            screen = Screen.Preview(bitmap)
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraManager?.shutdown() }
    }

    when (val s = screen) {
        is Screen.Menu -> MenuScreen(
            onTakePhoto = { screen = Screen.Camera },
            onPickFromGallery = { galleryLauncher.launch("image/*") },
            onShowMenu = { screen = Screen.ShowMenu },
            onEditMenu = { screen = Screen.EditMenu },
            onEditPrompt = { screen = Screen.EditPrompt }
        )

        is Screen.EditPrompt -> EditPromptScreen(
            context = context,
            onBack = { screen = Screen.Menu }
        )

        is Screen.EditMenu -> EditMenuScreen(
            context = context,
            onBack = { screen = Screen.Menu }
        )

        is Screen.ShowMenu -> ShowMenuScreen(
            onBack = { screen = Screen.Menu }
        )

        is Screen.Camera -> CameraScreen(
            onPhotoCaptured = { bitmap -> screen = Screen.Preview(bitmap) },
            onBack = { screen = Screen.Menu },
            onCameraManagerCreated = { cameraManager = it }
        )

        is Screen.Preview -> PreviewScreen(
            bitmap = s.bitmap,
            onUse = {
                saveCapturedImage(context, s.bitmap)
                screen = Screen.Processing(s.bitmap)
                coroutineScope.launch {
                    when (val result = geminiClient.analyzeImage(s.bitmap, PromptStore.load(context))) {
                        is AnalysisResult.Success -> {
                            if (result.entries.isNotEmpty()) {
                                val storedDescs = BeerDescriptionStore.loadLocal(context)
                                val enrichedEntries = BeerDescriptionStore.applyTo(result.entries, storedDescs)
                                // Neue KI-Beschreibungen + gespeicherte zusammenführen (gespeicherte haben Vorrang)
                                val newDescs = BeerDescriptionStore.extractFrom(result.entries)
                                val mergedDescs = newDescs + storedDescs
                                BeerDescriptionStore.saveLocal(context, mergedDescs)
                                val enrichedHtml = com.example.beermenu.ai.HtmlGenerator.generate(enrichedEntries)
                                BeerMenuStore.save(context, enrichedEntries)
                                try { BeerMenuStore.saveToCloud(enrichedEntries) } catch (_: Exception) {}
                                screen = Screen.EditMenu
                            } else {
                                screen = Screen.EditMenu
                            }
                        }
                        is AnalysisResult.Error -> screen = Screen.Error(result.message)
                    }
                }
            },
            onRetake = { screen = Screen.Camera },
            onPickFromGallery = { galleryLauncher.launch("image/*") }
        )

        is Screen.Processing -> ProcessingScreen()

        is Screen.Result -> {
            var isUploading by remember { mutableStateOf(false) }
            ResultScreen(
                html = s.html,
                isUploading = isUploading,
                onUpload = {
                    coroutineScope.launch {
                        isUploading = true
                        val result = UploadClient.upload(s.html)
                        isUploading = false
                        result.onSuccess {
                            Toast.makeText(context, "Upload erfolgreich", Toast.LENGTH_SHORT).show()
                            screen = Screen.Menu
                        }.onFailure {
                            Toast.makeText(context, "Upload fehlgeschlagen", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onNewMenu = { screen = Screen.Menu }
            )
        }

        is Screen.Error -> ErrorScreen(
            message = s.message,
            onBack = { screen = Screen.Menu }
        )
    }
}

@Composable
private fun MenuScreen(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onShowMenu: () -> Unit,
    onEditMenu: () -> Unit,
    onEditPrompt: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Biermenu Scanner", fontSize = 24.sp)
            Button(
                onClick = onTakePhoto,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Foto aufnehmen")
            }
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Bild aus Galerie")
            }
            OutlinedButton(
                onClick = onShowMenu,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Biermenu anzeigen")
            }
            OutlinedButton(
                onClick = onEditMenu,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Biermenu bearbeiten")
            }
        }
    }
}

@Composable
private fun ShowMenuScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Zurück") }
            Text("Biermenu", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Divider()
        UrlPreview(url = "https://biermenu.web.app/")
    }
}

@Composable
private fun EditMenuScreen(
    context: android.content.Context,
    onBack: () -> Unit
) {
    val entries = remember { mutableStateListOf<BeerEntry>().also { it.addAll(BeerMenuStore.load(context)) } }
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    // Beim Öffnen aus Firestore laden und lokale Liste aktualisieren
    var userEdited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cloudEntries = BeerMenuStore.loadFromCloud(context)
        BeerDescriptionStore.loadFromCloud(context) // aktualisiert lokalen Cache als Nebeneffekt
        // Falls BeerDescriptionStore leer (noch nie "Annehmen" gedrückt), aus Einträgen bootstrappen
        if (BeerDescriptionStore.loadLocal(context).isEmpty()) {
            val source = cloudEntries ?: BeerMenuStore.load(context)
            val extracted = BeerDescriptionStore.extractFrom(source)
            if (extracted.isNotEmpty()) BeerDescriptionStore.saveLocal(context, extracted)
        }
        // Einträge nur überschreiben falls Benutzer noch keine Änderungen gemacht hat
        if (!userEdited && cloudEntries != null) { entries.clear(); entries.addAll(cloudEntries) }
    }

    // editState: Triple(entryIndex, field, currentText)
    var editState by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
    // Index des Eintrags, dessen Farbe gerade bearbeitet wird
    var colorEditIndex by remember { mutableStateOf<Int?>(null) }

    editState?.let { (index, field, _) ->
        var editText by remember(editState) { mutableStateOf(editState!!.third) }
        AlertDialog(
            onDismissRequest = { editState = null },
            title = { Text(when (field) { "name" -> "Biername"; "type" -> "Typ / Stil"; else -> "Beschreibung" }) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (field == "description") 3 else 1
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    entries[index] = when (field) {
                        "name" -> {
                            val oldEntry = entries[index]
                            val oldKey = BeerDescriptionStore.makeKey(oldEntry.brewery, oldEntry.name)
                            // Alle Attribute unter altem Key sichern
                            val store = BeerDescriptionStore.loadLocal(context).toMutableMap()
                            store[oldKey] = BeerDescriptionStore.attrsOf(oldEntry)
                            BeerDescriptionStore.saveLocal(context, store)
                            val newEntry = oldEntry.copy(name = editText)
                            val newKey = BeerDescriptionStore.makeKey(newEntry.brewery, newEntry.name)
                            val storedAttrs = store[newKey]
                            Log.d("BeerDescStore", "Name edit: '$oldKey' -> '$newKey' ${if (storedAttrs != null) "HIT" else "MISS"}")
                            if (storedAttrs != null) BeerDescriptionStore.applyToEntry(newEntry, storedAttrs) else newEntry
                        }
                        "type"        -> entries[index].copy(type = editText)
                        else          -> entries[index].copy(description = editText)
                    }
                    userEdited = true
                    BeerMenuStore.save(context, entries.toList())
                    editState = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { editState = null }) { Text("Abbrechen") }
            }
        )
    }

    // Farbauswahl-Dialog
    colorEditIndex?.let { index ->
        val availableColors = entries.map { it.nameColor }.filter { it.isNotBlank() }.distinct()
        AlertDialog(
            onDismissRequest = { colorEditIndex = null },
            title = { Text("Namensfarbe wählen") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableColors.forEach { colorHex ->
                        val color = colorHex.toComposeColor() ?: return@forEach
                        val isSelected = entries[index].nameColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    entries[index] = entries[index].copy(nameColor = colorHex)
                                    userEdited = true
                                    BeerMenuStore.save(context, entries.toList())
                                    colorEditIndex = null
                                }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { colorEditIndex = null }) { Text("Abbrechen") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Biermenu bearbeiten",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        isUploading = true
                        val list = entries.toList()
                        BeerMenuStore.save(context, list)
                        BeerMenuStore.saveToCloud(list)
                        val newAttrs = BeerDescriptionStore.extractFrom(list)
                        if (newAttrs.isNotEmpty()) {
                            // Bestehende Einträge behalten, nur aktuelle ergänzen/aktualisieren
                            val merged = BeerDescriptionStore.loadLocal(context) + newAttrs
                            BeerDescriptionStore.saveLocal(context, merged)
                            try { BeerDescriptionStore.saveToCloud(merged) }
                            catch (e: Exception) { Log.w("MainScreen", "Description cloud save failed: ${e.message}") }
                        }
                        val html = com.example.beermenu.ai.HtmlGenerator.generate(list)
                        val capturedImage = loadCapturedImage(context)
                        val result = UploadClient.upload(html, capturedImage)
                        isUploading = false
                        result.onSuccess {
                            Toast.makeText(context, "Upload erfolgreich", Toast.LENGTH_SHORT).show()
                            onBack()
                        }.onFailure {
                            Toast.makeText(context, "Upload fehlgeschlagen", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isUploading
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Annehmen",
                    tint = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                )
            }
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Verwerfen",
                    tint = androidx.compose.ui.graphics.Color.Red
                )
            }
        }
        Divider()

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Noch kein Biermenu generiert.\nBitte zuerst ein Foto auswerten.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(entries) { index, entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Biername + Farbpalette
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = entry.name.ifBlank { "(kein Name)" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = entry.nameColor.toComposeColor()
                                        ?: MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { editState = Triple(index, "name", entry.name) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(24.dp)
                                        .background(
                                            entry.nameColor.toComposeColor()
                                                ?: MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        .clickable { colorEditIndex = index }
                                )
                            }
                            // Typ / Stil
                            Text(
                                text = entry.type.ifBlank { "(kein Typ)" },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editState = Triple(index, "type", entry.type) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Divider(modifier = Modifier.padding(horizontal = 12.dp))
                            // Beschreibung
                            Text(
                                text = entry.description.ifBlank { "(keine Beschreibung)" },
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editState = Triple(index, "description", entry.description) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPromptScreen(
    context: android.content.Context,
    onBack: () -> Unit
) {
    var promptText by remember { mutableStateOf(PromptStore.load(context)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Prompt bearbeiten", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = { Text("Prompt") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = {
                    promptText = PromptStore.DEFAULT_PROMPT
                    PromptStore.reset(context)
                }
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Zurücksetzen")
            }
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Abbrechen",
                    tint = androidx.compose.ui.graphics.Color.Red
                )
            }
            IconButton(
                onClick = {
                    PromptStore.save(context, promptText)
                    onBack()
                }
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Speichern",
                    tint = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
private fun CameraScreen(
    onPhotoCaptured: (Bitmap) -> Unit,
    onBack: () -> Unit,
    onCameraManagerCreated: (CameraManager) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }

    DisposableEffect(Unit) {
        onDispose { cameraManager?.shutdown() }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                cameraManager?.takePhoto { bitmap -> onPhotoCaptured(bitmap) }
            }) {
                Icon(Icons.Filled.Add, "Foto aufnehmen")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        val manager = CameraManager(context, lifecycleOwner, this)
                        manager.startCamera()
                        cameraManager = manager
                        onCameraManagerCreated(manager)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Text("Zurück")
            }
        }
    }
}

@Composable
private fun PreviewScreen(
    bitmap: Bitmap,
    onUse: () -> Unit,
    onRetake: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Aufgenommenes Bild",
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) {
                Text("Neu aufnehmen")
            }
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.weight(1f)
            ) {
                Text("Bild aus Galerie")
            }
            Button(
                onClick = onUse,
                modifier = Modifier.weight(1f)
            ) {
                Text("Bild verwenden")
            }
        }
    }
}

@Composable
private fun ProcessingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Bierkarte wird analysiert...")
        }
    }
}

@Composable
private fun ResultScreen(
    html: String,
    isUploading: Boolean,
    onUpload: () -> Unit,
    onNewMenu: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HtmlPreview(
            htmlContent = html,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onNewMenu,
                modifier = Modifier.weight(1f)
            ) {
                Text("Neues Menu")
            }
            Button(
                onClick = onUpload,
                modifier = Modifier.weight(1f),
                enabled = !isUploading
            ) {
                Text(if (isUploading) "Lädt hoch..." else "Hochladen")
            }
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Fehler",
                fontSize = 20.sp,
                color = androidx.compose.ui.graphics.Color.Red
            )
            Text(message)
            Button(onClick = onBack) {
                Text("Zurück zum Menü")
            }
        }
    }
}

private fun saveCapturedImage(context: android.content.Context, bitmap: Bitmap) {
    val file = File(context.filesDir, "captured_menu.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
}

private fun loadCapturedImage(context: android.content.Context): Bitmap? {
    val file = File(context.filesDir, "captured_menu.jpg")
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

private fun String.toComposeColor(): androidx.compose.ui.graphics.Color? {
    val hex = this.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    return try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#$hex"))
    } catch (e: IllegalArgumentException) {
        null
    }
}
