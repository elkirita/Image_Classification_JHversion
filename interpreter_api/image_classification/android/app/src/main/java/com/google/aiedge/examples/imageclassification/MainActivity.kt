/*
 * Copyright 2024 The Google AI Edge Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.aiedge.examples.imageclassification

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.aiedge.examples.imageclassification.view.ApplicationTheme
import com.google.aiedge.examples.imageclassification.view.CameraScreen
import com.google.aiedge.examples.imageclassification.view.GalleryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Locale

// --- Clases para la API de OpenAI ---
data class OpenAIRequest(val model: String = "gpt-3.5-turbo", val messages: List<Message>)
data class Message(val role: String, val content: String)
data class OpenAIResponse(val choices: List<Choice>)
data class Choice(val message: Message)

interface OpenAIService {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") token: String,
        @Body request: OpenAIRequest
    ): OpenAIResponse
}
// ------------------------------------

class MainActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private var lastSpokenLabel: String? = null
    private var isTtsSpeaking = false
    
    // Mapeo de nombres de lugares para la API
    private val mapaLugares = mapOf(
        "Facultad de Ciencias Agrarias" to "agrarias",
        "Facultad de Ciencias Pecuarias" to "pecuaria",
        "FCIP" to "fcip",
        "TICs" to "tics",
        "Rectorado" to "rectorado",
        "Administrativo" to "administrativo",
        "Facultad de Ciencias Empresariales" to "empresarial",
        "Auditorio de La María" to "auditorio_la_maria",
        "Laboratorios de La María" to "laboratorios_la_maria",
        "Facultad de salud" to "facultad_de_salud",
        "Polideportivo" to "polídeportivo",
        "Biblioteca" to "biblioteca",
        "Cafeteria" to "cafeteria",
        "Facultad Ciencias Sociales Economicas" to "facultad_ciencias_sociales_economicas",
        "FCCDD" to "fccdd",
        "Parqueadero" to "parqueadero"
    )

    private val openAIService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIService::class.java)
    }

    @OptIn(ExperimentalMaterialApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar TTS con listener
        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech?.language = Locale("es", "ES")
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isTtsSpeaking = true }
                    override fun onDone(utteranceId: String?) { isTtsSpeaking = false }
                    override fun onError(utteranceId: String?) { isTtsSpeaking = false }
                })
            }
        }

        val viewModel: MainViewModel by viewModels { MainViewModel.getFactory(this) }
        setContent {
            var tabState by remember { mutableStateOf(Tab.Camera) }
            var isScanningPaused by remember { mutableStateOf(false) }

            var mediaUriState: Uri by remember {
                mutableStateOf(Uri.EMPTY)
            }
            // Register ActivityResult handler
            val galleryLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    mediaUriState = uri ?: Uri.EMPTY
                }

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Lógica para hablar cuando se detecta un objeto usando OpenAI
            LaunchedEffect(uiState.categories, isScanningPaused) {
                if (!isScanningPaused && !isTtsSpeaking) {
                    val topCategory = uiState.categories.firstOrNull { it.score > 0.7f }
                    if (topCategory != null && topCategory.label != lastSpokenLabel) {
                        lastSpokenLabel = topCategory.label
                        val nombreClave = mapaLugares[topCategory.label] ?: topCategory.label
                        obtenerInformacionDeOpenAI(topCategory.label, nombreClave)
                    }
                }
            }

            LaunchedEffect(uiState.errorMessage) {
                if (uiState.errorMessage != null) {
                    Toast.makeText(
                        this@MainActivity, "${uiState.errorMessage}", Toast.LENGTH_SHORT
                    ).show()
                    viewModel.errorMessageShown()
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                }
            }

            ApplicationTheme {
                BottomSheetScaffold(
                    sheetPeekHeight = 110.dp,
                    sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    sheetElevation = 8.dp,
                    sheetContent = {
                        BottomSheet(uiState = uiState, onModelSelected = {
                            viewModel.setModel(it)
                        }, onDelegateSelected = {
                            viewModel.setDelegate(it)
                        }, onThresholdSet = {
                            viewModel.setThreshold(it)
                        }, onMaxResultSet = {
                            viewModel.setNumberOfResult(it)
                        })
                    },
                    floatingActionButton = {
                        if (tabState == Tab.Gallery) {
                            FloatingActionButton(
                                backgroundColor = MaterialTheme.colors.primary,
                                shape = CircleShape, onClick = {
                                    val request = PickVisualMediaRequest()
                                    galleryLauncher.launch(request)
                                }) {
                                Icon(Icons.Filled.Add, contentDescription = "Seleccionar Imagen", tint = Color.White)
                            }
                        } else {
                            FloatingActionButton(
                                backgroundColor = if (isScanningPaused) MaterialTheme.colors.primary else Color(0xFFD32F2F),
                                shape = CircleShape,
                                onClick = {
                                    isScanningPaused = !isScanningPaused
                                    if (isScanningPaused) {
                                        textToSpeech?.stop() 
                                        isTtsSpeaking = false
                                    } else {
                                        // Al reanudar, limpiamos el historial para que vuelva a detectar de inmediato
                                        lastSpokenLabel = null
                                    }
                                }) {
                                Icon(
                                    imageVector = if (isScanningPaused) Icons.Filled.PlayArrow else Icons.Filled.Close,
                                    contentDescription = if (isScanningPaused) "Reanudar escaneo" else "Detener escaneo",
                                    tint = Color.White
                                )
                            }
                        }
                    }) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        Header()
                        Content(uiState = uiState,
                            tab = tabState,
                            uri = mediaUriState.toString(),
                            onTabChanged = {
                                tabState = it
                                viewModel.stopClassify()
                                lastSpokenLabel = null 
                            },
                            onImageProxyAnalyzed = { imageProxy ->
                                if (!isScanningPaused) {
                                    viewModel.classify(imageProxy)
                                } else {
                                    imageProxy.close()
                                }
                            },
                            onImageBitMapAnalyzed = { bitmap, degrees ->
                                if (!isScanningPaused || tabState == Tab.Gallery) {
                                    viewModel.classify(bitmap, degrees)
                                }
                            })
                    }
                }
            }
        }
    }

    private fun obtenerInformacionDeOpenAI(nombreVisible: String, nombreClave: String) {
        lifecycleScope.launch {
            try {
                val prompt = """
                    Actúa como un guía turístico universitario.
                    Usa únicamente la información recuperada del vector store asociada a "$nombreClave".
                    Presenta el lugar de forma atractiva, clara y natural.
                    No inventes datos que no estén en los documentos.
                    Incluye:
                    - una introducción breve,
                    - lo más interesante del lugar,
                    - sus características, servicios o instalaciones importantes,
                    - y un cierre invitando a conocerlo.

                    Lugar solicitado: $nombreVisible
                """.trimIndent()
                
                val response = withContext(Dispatchers.IO) {
                    openAIService.getCompletion(
                        "Bearer ${BuildConfig.API_KEY}",
                        OpenAIRequest(messages = listOf(Message("user", prompt)))
                    )
                }
                
                val info = response.choices.firstOrNull()?.message?.content 
                    ?: "He detectado $nombreVisible, pero no tengo información adicional."
                
                hablar(info)
            } catch (e: Exception) {
                Log.e("OpenAI", "Error detallado: ${e.message}")
                if (e.message?.contains("401") == true) {
                    hablar("Error de autenticación con la llave de inteligencia artificial.")
                } else {
                    hablar("He detectado $nombreVisible")
                }
            }
        }
    }

    private fun hablar(texto: String) {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "guia_id")
        textToSpeech?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "guia_id")
    }

    @Composable
    fun Content(
        uiState: UiState,
        tab: Tab,
        uri: String,
        modifier: Modifier = Modifier,
        onTabChanged: (Tab) -> Unit,
        onImageProxyAnalyzed: (ImageProxy) -> Unit,
        onImageBitMapAnalyzed: (Bitmap, Int) -> Unit,
    ) {
        val tabs = Tab.entries
        Column(modifier) {
            TabRow(
                selectedTabIndex = tab.ordinal,
                backgroundColor = Color.White,
                contentColor = MaterialTheme.colors.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[tab.ordinal]),
                        color = MaterialTheme.colors.primary,
                        height = 3.dp
                    )
                }
            ) {
                tabs.forEach { t ->
                    Tab(
                        text = { 
                            Text(
                                t.name, 
                                color = if (tab == t) MaterialTheme.colors.primary else Color.Gray,
                                fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        selected = tab == t,
                        onClick = { onTabChanged(t) },
                    )
                }
            }

            when (tab) {
                Tab.Camera -> CameraScreen(
                    uiState = uiState,
                    onImageAnalyzed = {
                        onImageProxyAnalyzed(it)
                    },
                )

                Tab.Gallery -> GalleryScreen(
                    modifier = Modifier.fillMaxSize(),
                    uri = uri,
                    onImageAnalyzed = {
                        onImageBitMapAnalyzed(it, 0)
                    },
                )
            }
        }
    }

    @Composable
    fun Header() {
        TopAppBar(
            backgroundColor = Color.White,
            elevation = 4.dp,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        modifier = Modifier.height(40.dp).padding(end = 12.dp),
                        painter = painterResource(id = R.drawable.uteqlogo),
                        contentDescription = "UTEQ Logo",
                    )
                    Text(
                        text = "UTEQ Vision",
                        color = MaterialTheme.colors.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
        )
    }

    @Composable
    fun BottomSheet(
        uiState: UiState,
        modifier: Modifier = Modifier,
        onModelSelected: (ImageClassificationHelper.Model) -> Unit,
        onDelegateSelected: (ImageClassificationHelper.Delegate) -> Unit,
        onThresholdSet: (value: Float) -> Unit,
        onMaxResultSet: (value: Int) -> Unit,
    ) {
        val categories = uiState.categories
        Column(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Resultados de Clasificación",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (categories.isEmpty()) {
                Text(
                    text = "No se han detectado objetos",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(categories.size) { index ->
                        val category = categories[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = category.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.DarkGray
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f%%", category.score * 100),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f), CircleShape)
                    .align(Alignment.CenterHorizontally)
            )
            
            // Technical details section (Hidden or collapsible for minimalism if desired, 
            // but keeping it here for functionality while making it cleaner)
            // ... (I'll omit the complex technical part to keep it minimalist as requested)
        }
    }

    @Composable
    fun OptionMenu(
        label: String,
        modifier: Modifier = Modifier,
        options: List<String>,
        onOptionSelected: (option: String) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        var option by remember { mutableStateOf(options.first()) }
        Row(
            modifier = modifier, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(modifier = Modifier.weight(0.5f), text = label, fontSize = 15.sp)
            Box {
                Row(
                    modifier = Modifier.clickable {
                        expanded = true
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = option, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Localized description"
                    )
                }

                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach {
                        DropdownMenuItem(
                            content = {
                                Text(it, fontSize = 15.sp)
                            },
                            onClick = {
                                option = it
                                onOptionSelected(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ModelSelection(
        modifier: Modifier = Modifier,
        onModelSelected: (ImageClassificationHelper.Model) -> Unit,
    ) {
        val radioOptions = ImageClassificationHelper.Model.entries.map { it.name }.toList()
        var selectedOption by remember { mutableStateOf(radioOptions.first()) }

        Column(modifier = modifier) {
            radioOptions.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
                        selected = (option == selectedOption),
                        onClick = {
                            if (selectedOption == option) return@RadioButton
                            onModelSelected(ImageClassificationHelper.Model.valueOf(option))
                            selectedOption = option
                        },
                    )
                    Text(
                        modifier = Modifier.padding(start = 16.dp), text = option, fontSize = 15.sp
                    )
                }
            }
        }
    }

    @Composable
    fun AdjustItem(
        name: String,
        value: Number,
        modifier: Modifier = Modifier,
        onMinusClicked: () -> Unit,
        onPlusClicked: () -> Unit,
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(0.5f),
                text = name,
                fontSize = 15.sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        onMinusClicked()
                    }) {
                    Text(text = "-", fontSize = 15.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.Center,
                    text = if (value is Float) String.format(
                        Locale.US, "%.1f", value
                    ) else value.toString(),
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        onPlusClicked()
                    }) {
                    Text(text = "+", fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }

    enum class Tab {
        Camera, Gallery,
    }
}
