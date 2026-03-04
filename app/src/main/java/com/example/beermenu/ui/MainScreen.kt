package com.example.beermenu.ui

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import com.example.beermenu.ai.GeminiClient
import com.example.beermenu.camera.CameraManager
import com.example.beermenu.network.UploadClient
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    var capturedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var geminiResponse by remember { mutableStateOf<String?>(null) }
    var errorDetails by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }

    val API_KEY = "AIzaSyCogQN7ZOz7pZoXm7oqdHdPL5tUtx3RmMw"
    val geminiClient = remember { GeminiClient(API_KEY) }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.shutdown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (capturedImageBitmap == null) {
                FloatingActionButton(onClick = {
                    cameraManager?.takePhoto { bitmap ->
                        capturedImageBitmap = bitmap
                        coroutineScope.launch {
                            isLoading = true
                            errorDetails = null
                            try {
                                val result = geminiClient.analyzeImage(bitmap, GeminiClient.BEER_MENU_PROMPT)
                                if (result?.startsWith("ERROR_DETAILS:") == true) {
                                    errorDetails = result.removePrefix("ERROR_DETAILS:")
                                    geminiResponse = null
                                } else {
                                    geminiResponse = result
                                }
                            } catch (e: Exception) {
                                errorDetails = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }) {
                    Icon(Icons.Filled.Add, "Take photo")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (capturedImageBitmap == null) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            val manager = CameraManager(context, lifecycleOwner, this)
                            manager.startCamera()
                            cameraManager = manager
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (geminiResponse != null) {
                        HtmlPreview(htmlContent = geminiResponse!!)
                    } else if (errorDetails != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("API Fehler Details:", style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.Red))
                            Text(errorDetails!!, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        Image(
                            bitmap = capturedImageBitmap!!.asImageBitmap(),
                            contentDescription = "Captured beer menu",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isLoading || isUploading) {
                        CircularProgressIndicator()
                    }
                }

                if (!isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                capturedImageBitmap = null
                                geminiResponse = null
                                errorDetails = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Neu aufnehmen")
                        }

                        if (geminiResponse != null) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isUploading = true
                                        val result = UploadClient.upload(geminiResponse!!)
                                        isUploading = false
                                        result.onSuccess {
                                            Toast.makeText(context, "Upload erfolgreich", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Upload fehlgeschlagen", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isUploading
                            ) {
                                Text(if (isUploading) "Lädt hoch..." else "Hochladen")
                            }
                        }
                    }
                }
            }
        }
    }
}
