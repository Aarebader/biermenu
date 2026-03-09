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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.beermenu.ui.theme.*
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

// ─── Reusable top bar ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeerTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = CreamWhite
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Zurück", tint = CreamWhite)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TealDark
        )
    )
}

// ─── Menu screen ───

@Composable
private fun MenuScreen(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onShowMenu: () -> Unit,
    onEditMenu: () -> Unit,
    onEditPrompt: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(TealDark, TealMedium)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                "BIER",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 48.sp,
                    letterSpacing = 6.sp
                ),
                color = AmberPrimary
            )
            Text(
                "MENU",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    letterSpacing = 8.sp
                ),
                color = CreamWhite
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtle divider line
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(2.dp)
                    .background(AmberPrimary, RoundedCornerShape(1.dp))
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Primary action button
            Button(
                onClick = onTakePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Foto aufnehmen", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary actions
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CreamWhite
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(AmberPrimary, AmberLight))
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Menu, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Bild aus Galerie")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onShowMenu,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CreamWhite
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(AmberPrimary, AmberLight))
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Biermenu anzeigen")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onEditMenu,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CreamWhite
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(AmberPrimary, AmberLight))
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Biermenu bearbeiten")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Settings link
            TextButton(onClick = onEditPrompt) {
                Text(
                    "Prompt bearbeiten",
                    color = WarmGrey,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ─── Show menu screen ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowMenuScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { BeerTopBar(title = "Biermenu", onBack = onBack) },
        containerColor = TealDark
    ) { padding ->
        UrlPreview(
            url = "https://biermenu.web.app/",
            modifier = Modifier.padding(padding)
        )
    }
}

// ─── Edit menu screen ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMenuScreen(
    context: android.content.Context,
    onBack: () -> Unit
) {
    val entries = remember { mutableStateListOf<BeerEntry>().also { it.addAll(BeerMenuStore.load(context)) } }
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    var userEdited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cloudEntries = BeerMenuStore.loadFromCloud(context)
        BeerDescriptionStore.loadFromCloud(context)
        if (BeerDescriptionStore.loadLocal(context).isEmpty()) {
            val source = cloudEntries ?: BeerMenuStore.load(context)
            val extracted = BeerDescriptionStore.extractFrom(source)
            if (extracted.isNotEmpty()) BeerDescriptionStore.saveLocal(context, extracted)
        }
        if (!userEdited && cloudEntries != null) { entries.clear(); entries.addAll(cloudEntries) }
    }

    var editState by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
    var colorEditIndex by remember { mutableStateOf<Int?>(null) }

    // Edit dialog
    editState?.let { (index, field, _) ->
        var editText by remember(editState) { mutableStateOf(editState!!.third) }
        AlertDialog(
            onDismissRequest = { editState = null },
            title = {
                Text(
                    when (field) { "name" -> "Biername"; "type" -> "Typ / Stil"; else -> "Beschreibung" },
                    color = CreamWhite
                )
            },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (field == "description") 3 else 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberPrimary,
                        cursorColor = AmberPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    entries[index] = when (field) {
                        "name" -> {
                            val oldEntry = entries[index]
                            val oldKey = BeerDescriptionStore.makeKey(oldEntry.brewery, oldEntry.name)
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
                }) { Text("OK", color = AmberPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { editState = null }) { Text("Abbrechen") }
            },
            containerColor = TealMedium
        )
    }

    // Color picker dialog
    colorEditIndex?.let { index ->
        val availableColors = entries.map { it.nameColor }.filter { it.isNotBlank() }.distinct()
        AlertDialog(
            onDismissRequest = { colorEditIndex = null },
            title = { Text("Namensfarbe wählen", color = CreamWhite) },
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
                                    color = if (isSelected) AmberPrimary else WarmGrey,
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
            },
            containerColor = TealMedium
        )
    }

    Scaffold(
        topBar = {
            BeerTopBar(
                title = "Biermenu bearbeiten",
                actions = {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).then(Modifier.padding(end = 12.dp)),
                            color = AmberPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isUploading = true
                                    val list = entries.toList()
                                    BeerMenuStore.save(context, list)
                                    BeerMenuStore.saveToCloud(list)
                                    val newAttrs = BeerDescriptionStore.extractFrom(list)
                                    if (newAttrs.isNotEmpty()) {
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
                            }
                        ) {
                            Icon(Icons.Filled.Check, "Hochladen", tint = BeerGreen)
                        }
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, "Verwerfen", tint = BeerRed)
                    }
                }
            )
        },
        containerColor = TealDark
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Noch kein Biermenu generiert.\nBitte zuerst ein Foto auswerten.",
                    color = WarmGrey,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(entries) { index, entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = TealMedium
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Beer name + color dot
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = entry.name.ifBlank { "(kein Name)" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = entry.nameColor.toComposeColor() ?: AmberPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { editState = Triple(index, "name", entry.name) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(24.dp)
                                        .background(
                                            entry.nameColor.toComposeColor() ?: AmberPrimary,
                                            CircleShape
                                        )
                                        .border(1.dp, WarmGrey, CircleShape)
                                        .clickable { colorEditIndex = index }
                                )
                            }
                            // Type / style
                            Text(
                                text = entry.type.ifBlank { "(kein Typ)" },
                                fontSize = 13.sp,
                                color = Color(0xFFCCC5B9),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editState = Triple(index, "type", entry.type) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Divider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = TealLight.copy(alpha = 0.5f)
                            )
                            // Description
                            Text(
                                text = entry.description.ifBlank { "(keine Beschreibung)" },
                                fontSize = 13.sp,
                                color = CreamWhite.copy(alpha = 0.85f),
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

// ─── Edit prompt screen ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPromptScreen(
    context: android.content.Context,
    onBack: () -> Unit
) {
    var promptText by remember { mutableStateOf(PromptStore.load(context)) }

    Scaffold(
        topBar = {
            BeerTopBar(
                title = "Prompt bearbeiten",
                actions = {
                    IconButton(onClick = {
                        promptText = PromptStore.DEFAULT_PROMPT
                        PromptStore.reset(context)
                    }) {
                        Icon(Icons.Filled.Refresh, "Zurücksetzen", tint = CreamWhite)
                    }
                    IconButton(onClick = {
                        PromptStore.save(context, promptText)
                        onBack()
                    }) {
                        Icon(Icons.Filled.Check, "Speichern", tint = BeerGreen)
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, "Abbrechen", tint = BeerRed)
                    }
                }
            )
        },
        containerColor = TealDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxSize(),
                label = { Text("Prompt") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberPrimary,
                    unfocusedBorderColor = TealLight,
                    focusedLabelColor = AmberPrimary,
                    cursorColor = AmberPrimary,
                    focusedTextColor = CreamWhite,
                    unfocusedTextColor = CreamWhite
                )
            )
        }
    }
}

// ─── Camera screen ───

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
            FloatingActionButton(
                onClick = {
                    cameraManager?.takePhoto { bitmap -> onPhotoCaptured(bitmap) }
                },
                containerColor = AmberPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, "Foto aufnehmen")
            }
        },
        containerColor = Color.Black
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
            // Back button with tinted background
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(TealDark.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(Icons.Filled.ArrowBack, "Zurück", tint = CreamWhite)
            }
        }
    }
}

// ─── Preview screen ───

@Composable
private fun PreviewScreen(
    bitmap: Bitmap,
    onUse: () -> Unit,
    onRetake: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TealDark)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Aufgenommenes Bild",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CreamWhite),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(WarmGrey, WarmGrey))
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Neu", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CreamWhite),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(WarmGrey, WarmGrey))
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Galerie", fontSize = 13.sp)
            }
            Button(
                onClick = onUse,
                modifier = Modifier.weight(1.5f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Verwenden", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Processing screen ───

@Composable
private fun ProcessingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(TealDark, TealMedium))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                color = AmberPrimary,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Bierkarte wird analysiert...",
                style = MaterialTheme.typography.titleMedium,
                color = CreamWhite
            )
        }
    }
}

// ─── Result screen ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreen(
    html: String,
    isUploading: Boolean,
    onUpload: () -> Unit,
    onNewMenu: () -> Unit
) {
    Scaffold(
        containerColor = TealDark,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TealDark)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNewMenu,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CreamWhite),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(WarmGrey, WarmGrey))
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Neues Menu")
                }
                Button(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hochladen")
                    }
                }
            }
        }
    ) { padding ->
        HtmlPreview(
            htmlContent = html,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

// ─── Error screen ───

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TealDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Fehler",
                style = MaterialTheme.typography.headlineMedium,
                color = BeerRed
            )
            Text(
                message,
                color = CreamWhite,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
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
