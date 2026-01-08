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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.outlined.Send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var llm: LlmInference
    private lateinit var chatDao: ChatDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false
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
    val keyboardController = LocalSoftwareKeyboardController.current

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
                        Icon(Icons.Outlined.Assistant, contentDescription = "New Chat")
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
                        sessionCreated = true
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
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                state = listState
            )
            {

            items(messages) { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement =
                            if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        val isCursorOnly =
                            !msg.isUser && msg.isStreaming && msg.text.isNullOrEmpty()

                        if (isCursorOnly) {
                            // 👇 Cursor WITHOUT background
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                BlinkingCursor()
                            }
                        } else {
                            // 👇 Normal chat bubble
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 2.dp,
                                color =
                                    if (msg.isUser)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 8.dp
                                    )
                                ) {

                                    msg.imageUri?.let { uri ->
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(180.dp)
                                                .padding(bottom = 4.dp)
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
                                                .padding(top = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                // 👇 ADDED: small bottom padding so last message isn't clipped
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            /* -------------------- SELECTED IMAGE PREVIEW -------------------- */

            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Selected image",
                            modifier = Modifier.size(96.dp)
                        )

                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-6).dp)
                                .size(28.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }



            /* -------------------- INPUT ROW -------------------- */
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 2.dp,top = 0.dp)


            ) {

                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    placeholder = { Text("Ask a question") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Attach file"
                            )
                        }
                    }
                )



                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = if (isLoading)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        enabled = !isLoading,
                        onClick = {
                            keyboardController?.hide()

                            if (userInput.isBlank() && selectedImageUri == null) return@IconButton

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
                                text = "",
                                isUser = false,
                                isStreaming = true
                            )


                            scope.launch {
                                /* 🔥 KEEP YOUR EXISTING LOGIC UNCHANGED */
                                if (!sessionCreated && !textToSend.isNullOrBlank()) {
                                    chatDao.insertSession(
                                        ChatSessionEntity(
                                            sessionId = currentSessionId,
                                            title = textToSend.take(40),
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

                                val ocrText = imageToSend?.let {
                                    try { extractTextFromImage(context, it) } catch (e: Exception) { "" }
                                }

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

                                val fullAnswer = withContext(Dispatchers.Default) {
                                    llm.generateResponse(prompt)
                                }

                                val lines = fullAnswer.lines()

                                messages = messages.dropLast(1) +
                                        ChatMessage(
                                            text = "",
                                            isUser = false,
                                            isStreaming = true
                                        )

                                for (line in lines) {
                                    delay(120) // controls typing speed

                                    messages = messages.dropLast(1) +
                                            messages.last().copy(
                                                text = messages.last().text + line + "\n"
                                            )
                                }

                                messages = messages.dropLast(1) +
                                        messages.last().copy(
                                            isStreaming = false
                                        )


                                val topic = detectDiagramTopic(finalQuestion + " " + fullAnswer)
                                val diagramImages =
                                    topic?.let { getDiagramImages(context, it) } ?: emptyList()

                                chatDao.insertMessage(
                                    ChatMessageEntity(
                                        sessionId = currentSessionId,
                                        text = fullAnswer,
                                        isUser = false,
                                        timestamp = System.currentTimeMillis(),
                                        diagramImages = diagramImages.joinToString(",")
                                    )
                                )

                                messages = messages.dropLast(1) +
                                        ChatMessage(
                                            text = fullAnswer,
                                            isUser = false,
                                            diagramImages = diagramImages
                                        )

                                isLoading = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

            }
        }
    }
}

@Composable
fun BlinkingCursor() {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            visible = !visible
            kotlinx.coroutines.delay(500)
        }
    }

    Text(
        text = if (visible) "▍" else "",
        style = MaterialTheme.typography.bodyLarge
    )
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
