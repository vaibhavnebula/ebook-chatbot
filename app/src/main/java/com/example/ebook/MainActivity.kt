package com.example.ebook

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ebook.ui.theme.EbookTheme
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlin.concurrent.thread
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.room.Room
import java.util.UUID
import androidx.compose.material.icons.filled.Menu
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var llm: LlmInference
    private lateinit var chatDao: ChatDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* -------- ROOM DATABASE INIT (NEW) -------- */
        val db = Room.databaseBuilder(
            applicationContext,
            ChatDatabase::class.java,
            "chat_db"
        ).fallbackToDestructiveMigration().build()

        chatDao = db.chatDao()

        /* -------- LLM INIT (UNCHANGED) -------- */
        val llmOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(
                copyAssetToInternalStorage(
                    this,
                    "gemma3-1b-it-int4.task"
                )
            )
            .setMaxTokens(1024)
            .build()

        llm = LlmInference.createFromOptions(this, llmOptions)


        /* -------- SET UI -------- */
        setContent {
            EbookTheme {
                ChatScreen(
                    llm = llm,
                    chatDao = chatDao
                )
            }
        }
    }
}

/* -------------------- CHAT MODEL -------------------- */

data class ChatMessage(
    val text: String? = null,
    val imageUri: Uri? = null,
    val diagramImages: List<String> = emptyList(),
    val isUser: Boolean,
    val isThinking: Boolean = false
)

/* -------------------- UI -------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    llm: LlmInference,
    chatDao: ChatDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var sessionCreated by remember { mutableStateOf(false) }

    // 👇 ADDED: LazyListState for auto-scroll
    val listState = rememberLazyListState()

    // 👇 ADDED: Auto-scroll to latest message when messages change
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            selectedImageUri = uri
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Science Book Chatbot",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )) },
                navigationIcon = {
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Default.Menu, contentDescription = "History")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        currentSessionId = UUID.randomUUID().toString()
                        messages = emptyList()
                        showHistory = false
                        sessionCreated = false
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat")
                    }
                }
            )
        }
    ) { padding ->

        if (showHistory) {
            HistoryScreen(
                chatDao = chatDao,
                onSessionSelected = { sessionId ->
                    scope.launch {
                        val oldMessages = chatDao.getMessages(sessionId)

                        messages = oldMessages.map {
                            ChatMessage(
                                text = it.text,
                                isUser = it.isUser,
                                imageUri = it.imageUri?.let(Uri::parse),
                                diagramImages = it.diagramImages
                                    ?.split(",")
                                    ?.filter { name -> name.isNotBlank() }
                                    ?: emptyList()
                            )
                        }

                        currentSessionId = sessionId
                        showHistory = false
                    }
                }
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
//                .padding(16.dp)
        ) {

            /* -------------------- CHAT LIST -------------------- */
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState  //  ADDED: attach scroll state
            ) {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color =
                                if (msg.isUser)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {

                                msg.imageUri?.let { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Uploaded image",
                                        modifier = Modifier
                                            .size(180.dp)
                                            .padding(bottom = 6.dp)
                                    )
                                }

                                msg.text?.let { text ->
                                    Text(
                                        text = text,
                                        color =
                                            if (msg.isUser)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                msg.diagramImages.forEach { path ->
                                    AsyncImage(
                                        model = "file:///android_asset/diagrams/$path",
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                // 👇 ADDED: small bottom padding so last message isn't clipped
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            /* -------------------- SELECTED IMAGE PREVIEW -------------------- */

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()              // 👈 THIS MAKES IT MOVE
                    .navigationBarsPadding()
                    .padding(12.dp)
            ) {
                selectedImageUri?.let { uri ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected image",
                            modifier = Modifier
                                .size(100.dp)
                                .padding(4.dp)
                        )
                        IconButton(onClick = { selectedImageUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove image")
                        }
                    }
                }
            }

            /* -------------------- INPUT ROW -------------------- */
            Row(verticalAlignment = Alignment.CenterVertically) {

                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Upload Image")
                }

                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask a question") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    enabled = !isLoading,
                    onClick = {
                        if (userInput.isBlank() && selectedImageUri == null) return@Button

                        val textToSend = userInput.takeIf { it.isNotBlank() }
                        val imageToSend = selectedImageUri

                        userInput = ""
                        selectedImageUri = null
                        isLoading = true

                        messages = messages + ChatMessage(
                            text = textToSend,
                            imageUri = imageToSend,
                            isUser = true
                        ) + ChatMessage(
                            text = "Thinking...",
                            isUser = false,
                            isThinking = true
                        )

                        scope.launch {

                            if (!sessionCreated) {
                                chatDao.insertSession(
                                    ChatSessionEntity(
                                        sessionId = currentSessionId,
                                        title = textToSend ?: "New Chat",
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                sessionCreated = true
                            }

                            chatDao.insertMessage(
                                ChatMessageEntity(
                                    sessionId = currentSessionId,
                                    text = textToSend ?: "",
                                    isUser = true,
                                    timestamp = System.currentTimeMillis(),
                                    imageUri = imageToSend?.toString()
                                )
                            )
                            /* -------- OCR -------- */
                            val ocrText = imageToSend?.let {
                                try {
                                    extractTextFromImage(context, it)
                                } catch (e: Exception) {
                                    ""
                                }
                            }
                            /* -------- FINAL QUESTION -------- */
                            val finalQuestion = buildString {
                                textToSend?.let { append(it) }
                                if (!ocrText.isNullOrBlank()) {
                                    append("\n\nText found in image:\n")
                                    append(ocrText)
                                }
                            }

                            val prompt = """
                                            You are a helpful educational assistant.
                                            Answer the following clearly and simply.
                                            
                                            User input:
                                            $finalQuestion
                                            
                                            Answer:
                                            """.trimIndent()

                            val answer = withContext(Dispatchers.Default) {
                                llm.generateResponse(prompt)
                            }

                            /* -------- DIAGRAM DETECTION -------- */
                            val topic = detectDiagramTopic(finalQuestion + " " + answer)
                            val diagramImages =
                                topic?.let { getDiagramImages(context, it) } ?: emptyList()

                            chatDao.insertMessage(
                                ChatMessageEntity(
                                    sessionId = currentSessionId,
                                    text = answer,
                                    isUser = false,
                                    timestamp = System.currentTimeMillis(),
                                    diagramImages = diagramImages.joinToString(",")
                                )
                            )

                            messages = messages.dropLast(1) +
                                    ChatMessage(
                                        text = answer,
                                        isUser = false,
                                        diagramImages = diagramImages
                                    )

                            isLoading = false
                        }
                    }
                ) {
                    Text("Ask")
                }
            }
        }
    }
}

/* -------------------- FILE COPY (LLM ONLY) -------------------- */
private fun copyAssetToInternalStorage(
    context: android.content.Context,
    assetName: String
): String {
    val file = java.io.File(context.filesDir, assetName)
    if (!file.exists()) {
        context.assets.open(assetName).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    return file.absolutePath
}
