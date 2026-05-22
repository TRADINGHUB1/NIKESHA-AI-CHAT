package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MessageEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R
import android.app.Activity
import java.util.Locale
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf("home") } // "home" or "chat" or "voice_call"

    var voicePitch by remember { mutableStateOf(1.35f) }
    var speechRate by remember { mutableStateOf(1.05f) }
    var isMuted by remember { mutableStateOf(false) }
    val triggerNextVoiceInput = remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    BackHandler(enabled = currentScreen == "chat" || currentScreen == "voice_call") {
        tts?.stop()
        currentScreen = "home"
    }

    val context = LocalContext.current

    DisposableEffect(context) {
        val speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        speech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (currentScreen == "voice_call" && !isMuted) {
                    triggerNextVoiceInput.value = true
                }
            }
            override fun onError(utteranceId: String?) {}
        })
        tts = speech
        onDispose {
            speech.shutdown()
        }
    }

    val speakText: (String) -> Unit = { text ->
        tts?.let { speech ->
            if (isTtsReady) {
                val hasSinhala = text.any { it in '\u0D80'..'\u0DFF' }
                val locale = if (hasSinhala) Locale("si", "LK") else Locale.US
                speech.language = locale
                speech.setPitch(voicePitch)
                speech.setSpeechRate(speechRate)
                speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UtteranceId")
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = spokenList?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                if (currentScreen == "voice_call") {
                    viewModel.sendSuggestion(text)
                } else {
                    viewModel.onTextInputChanged(text)
                }
            }
        }
    }

    val launchSpeechToText = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "si-LK")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "si-LK")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, "si-LK")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "කතා කරන්න (Speak to Nikesha)...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Speech recognition is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // Dialog state for authentic Google Auth Flow
    var showAccountChooser by remember { mutableStateOf(false) }
    var showProfileDetails by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectImage(uri.toString())
        }
    }

    var isVoiceModeActive by remember { mutableStateOf(false) }

    // Handler for automatic speech recognition loop
    LaunchedEffect(triggerNextVoiceInput.value) {
        if (triggerNextVoiceInput.value) {
            triggerNextVoiceInput.value = false
            launchSpeechToText()
        }
    }

    // Welcoming greeting when starting a Voice Call
    LaunchedEffect(currentScreen) {
        if (currentScreen == "voice_call") {
            viewModel.createNewSession()
            // Make Nikesha start with a cute welcoming greeting
            speakText("හෙලෝ! මම නිකේෂා. ඔයා එක්ක කතා කරන්න මම බලාගෙන ඉන්නවා. අද ඔයාට මොනවද දැනගන්න ඕනේ?")
        }
    }

    // Auto-read-aloud when Gemini responds in Voice Mode or Voice Call screen
    LaunchedEffect(uiState.messages, uiState.isSending) {
        val lastMessage = uiState.messages.lastOrNull()
        if (lastMessage != null && lastMessage.role == "model" && !uiState.isSending) {
            if (currentScreen == "voice_call" || (currentScreen == "chat" && isVoiceModeActive)) {
                speakText(lastMessage.content)
            }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        if (uiState.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Modal Drawer for Sidebar History
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = BentoBackground,
                modifier = Modifier.width(310.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Title
                        Text(
                            text = "නිකේෂා - ඉතිහාසය",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                        Text(
                            text = "CHAT HISTORY SESSIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoPrimary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Create New Session (Gradient Bento Card!)
                        Card(
                            onClick = {
                                viewModel.createNewSession()
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(GeminiGradientStart, GeminiGradientMiddle)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Session",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "අලුත් කතාබහක් (New Chat)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // List of Sessions
                        Text(
                            text = "PAST CONVERSATIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.sessions) { sessionName ->
                                val isActive = uiState.activeSessionId == sessionName
                                Card(
                                    onClick = {
                                        viewModel.selectSession(sessionName)
                                        coroutineScope.launch { drawerState.close() }
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) BentoPrimaryContainer else BentoSurface
                                    ),
                                    border = BorderStroke(1.dp, if (isActive) BentoPrimary else BentoBorder),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Session Icon",
                                            tint = if (isActive) BentoPrimary else BentoTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (sessionName == "default") "ප්‍රධාන කතාබහ (Main)" else sessionName,
                                            color = if (isActive) BentoOnPrimaryContainer else BentoTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (sessionName != "default") {
                                            IconButton(
                                                onClick = { viewModel.deleteSession(sessionName) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Session",
                                                    tint = Color.Red.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Section of Drawer: Google Sign-In Status
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Surface(
                            color = BentoInputContainer,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                if (uiState.isGoogleLoggedIn) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(BentoPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (uiState.googleName ?: "P").take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = uiState.googleName ?: "User Account",
                                                color = BentoTextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = uiState.googleEmail ?: "pamodmalith9@gmail.com",
                                                color = BentoTextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "Logout",
                                            color = Color.Red.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { viewModel.googleSignOut() }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showAccountChooser = true }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Simulated Google Icon
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                                    .border(1.dp, BentoBorder, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "G",
                                                    color = BentoPrimary,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Text(
                                                text = "Sign in with Google",
                                                color = BentoTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Arrow Signin",
                                            tint = BentoTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        // App Scaffold screen
        Scaffold(
            containerColor = BentoBackground,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // 1. Header Section
                BentoHeader(
                    userAvatarLetter = if (uiState.isGoogleLoggedIn) {
                        (uiState.googleName ?: "Pamod").take(1).uppercase()
                    } else null,
                    onMenuClicked = { coroutineScope.launch { drawerState.open() } },
                    onProfileClicked = {
                        if (uiState.isGoogleLoggedIn) {
                            showProfileDetails = true
                        } else {
                            showAccountChooser = true
                        }
                    },
                    onBackClicked = if (currentScreen == "chat" || currentScreen == "voice_call") { { 
                        tts?.stop()
                        currentScreen = "home" 
                    } } else null
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (currentScreen == "home") {
                    HomeScreen(
                        uiState = uiState,
                        onStartNewChat = {
                            viewModel.createNewSession()
                            currentScreen = "chat"
                        },
                        onStartVoiceChat = {
                            currentScreen = "voice_call"
                        },
                        onStartImageChat = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                            currentScreen = "chat"
                        },
                        onSelectSession = { sessionName ->
                            viewModel.selectSession(sessionName)
                            currentScreen = "chat"
                        },
                        onPromptSuggested = { prompt ->
                            currentScreen = "chat"
                            viewModel.sendSuggestion(prompt)
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else if (currentScreen == "voice_call") {
                    VoiceCallScreen(
                        uiState = uiState,
                        onEndCall = {
                            tts?.stop()
                            currentScreen = "home"
                        },
                        onTriggerMic = {
                            launchSpeechToText()
                        },
                        voicePitch = voicePitch,
                        onPitchChanged = { voicePitch = it },
                        speechRate = speechRate,
                        onSpeechRateChanged = { speechRate = it },
                        isMuted = isMuted,
                        onMuteToggled = { isMuted = it },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // 2. Bento Grid Row (Status Card + Sparkle Quick Action Card)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActiveChatStatusCard(
                            modifier = Modifier.weight(0.65f)
                        )
                        QuickActionSparkleCard(
                            onPromptSuggested = { prompt -> viewModel.sendSuggestion(prompt) },
                            modifier = Modifier.weight(0.35f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Central Chat Compartment - The Big Conversation Bento Box!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BentoSurface),
                        border = BorderStroke(1.dp, BentoBorder),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Floating Voice Companion Mode Toggle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .zIndex(2f)
                            ) {
                                Surface(
                                    onClick = { isVoiceModeActive = !isVoiceModeActive },
                                    color = if (isVoiceModeActive) BentoPrimary.copy(alpha = 0.2f) else BentoInputContainer.copy(alpha = 0.9f),
                                    border = BorderStroke(1.dp, if (isVoiceModeActive) BentoPrimary else BentoBorder),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVoiceModeActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                            contentDescription = "කටහඬ සංවාද (Voice Mode)",
                                            tint = if (isVoiceModeActive) BentoPrimary else BentoTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isVoiceModeActive) "Auto-Speak ON" else "Voice Chat OFF",
                                            color = if (isVoiceModeActive) BentoPrimary else BentoTextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (uiState.messages.isEmpty()) {
                                EmptyStateLayout(
                                    onSuggestionSelected = { suggestion ->
                                        viewModel.sendSuggestion(suggestion)
                                    }
                                )
                            } else {
                                LazyColumn(
                                    state = scrollState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentPadding = PaddingValues(top = 48.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(uiState.messages) { message ->
                                        MessageBubble(
                                            message = message,
                                            onSpeakClicked = speakText
                                        )
                                    }

                                    if (uiState.isSending) {
                                        item {
                                            NikeshaTypingIndicator(modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Stats Row Bento Cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FastModeCard(modifier = Modifier.weight(1f))
                        SecureCard(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Chat input area
                    ChatInputRow(
                        textInput = uiState.textInput,
                        onTextChanged = { viewModel.onTextInputChanged(it) },
                        onSendClicked = { viewModel.sendMessage() },
                        isSending = uiState.isSending,
                        selectedImagePath = uiState.selectedImagePath,
                        onPickImage = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearImageClicked = { viewModel.clearSelectedImage() },
                        onMicClicked = { launchSpeechToText() }
                    )
                }
            }
        }
    }

    // AUTHENTIC GOOGLE ACCOUNT CHOOSER SHEET
    if (showAccountChooser) {
        ModalBottomSheet(
            onDismissRequest = { showAccountChooser = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BentoBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Google Graphic Icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 28.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Sign in with Google",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = BentoTextPrimary
                )
                Text(
                    text = "to continue to Nikesha AI Chatbot",
                    fontSize = 13.sp,
                    color = BentoTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isSigningIn) {
                    CircularProgressIndicator(color = BentoPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Connecting standard Google Account...", color = BentoTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    // Account option 1 (Dynamic user account based on instructions)
                    Card(
                        onClick = {
                            isSigningIn = true
                            coroutineScope.launch {
                                delay(1200)
                                viewModel.googleSignIn("pamodmalith9@gmail.com", "Pamod Malith")
                                isSigningIn = false
                                showAccountChooser = false
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = BentoInputContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4285F4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "P", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pamod Malith",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = BentoTextPrimary
                                )
                                Text(
                                    text = "pamodmalith9@gmail.com",
                                    fontSize = 12.sp,
                                    color = BentoTextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Active account verified",
                                tint = Color(0xFF34A853),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Use another account Option
                    Card(
                        onClick = {
                            isSigningIn = true
                            coroutineScope.launch {
                                delay(1000)
                                viewModel.googleSignIn("guest@example.com", "Nikesha Guest")
                                isSigningIn = false
                                showAccountChooser = false
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, BentoBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Account",
                                tint = BentoTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Use another account / Guest",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = BentoTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "To continue, Google will share your name, email address, profile picture and preferences with Nikesha. Review Nikesha's Privacy Policy and Terms of Service.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                        color = BentoTextSecondary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    // SIGNED IN USER PROFILE DETAILS SHEET
    if (showProfileDetails) {
        ModalBottomSheet(
            onDismissRequest = { showProfileDetails = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BentoBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Google Account",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(GeminiGradientStart, GeminiGradientMiddle)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState.googleName ?: "P").take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = uiState.googleName ?: "Google User",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
                Text(
                    text = uiState.googleEmail ?: "pamodmalith9@gmail.com",
                    fontSize = 13.sp,
                    color = BentoTextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons inside profile chooser
                Button(
                    onClick = {
                        viewModel.googleSignOut()
                        showProfileDetails = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Sign Out",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Sign Out from Google", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showProfileDetails = false },
                    border = BorderStroke(1.dp, BentoBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(text = "Close", color = BentoTextPrimary)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun BentoHeader(
    userAvatarLetter: String?,
    onMenuClicked: () -> Unit,
    onProfileClicked: () -> Unit,
    onBackClicked: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBackClicked != null) {
                // Back arrow in place of Menu
                IconButton(
                    onClick = onBackClicked,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BentoInputContainer)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = BentoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // 1. Sleek menu icon to slide out Conversation History Drawer
                IconButton(
                    onClick = onMenuClicked,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BentoInputContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Chat History Menu",
                        tint = BentoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Avatar Logo and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_nikesha_logo),
                    contentDescription = "Nikesha Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(
                        text = "Nikesha",
                        color = BentoTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "POWERED BY GEMINI",
                        color = BentoTextSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Action Button: Clean Profile Avatar / G Sign In
        IconButton(
            onClick = onProfileClicked,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (userAvatarLetter != null) BentoPrimaryContainer else BentoInputContainer)
                .testTag("google_profile_btn")
        ) {
            if (userAvatarLetter != null) {
                Text(
                    text = userAvatarLetter,
                    color = BentoOnPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Sign In with Google",
                    tint = BentoTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun ActiveChatStatusCard(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoSurface),
        border = BorderStroke(1.dp, BentoBorder),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE CHAT",
                    color = BentoPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                // Declarative Pulse Dot Animation
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E).copy(alpha = minOf(pulseAlpha, 1f)))
                )
            }
            Text(
                text = "Brief responses enabled. Ready for quick answers.",
                color = BentoTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun QuickActionSparkleCard(
    onPromptSuggested: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prompts = listOf(
        "Nikesha, oyata sithuwili thiyෙනවද?",
        "ලස්සන සිංහල කවි පද පේළි කිහිපයක් කියන්න.",
        "Nikesha, මට අද දවස සතුටින් ගත කරන්න උපදෙසක් දෙන්න.",
        "කෙටි කතාවක් මට කියන්න."
    )
    Card(
        onClick = {
            val randomPrompt = prompts.random()
            onPromptSuggested(randomPrompt)
        },
        colors = CardDefaults.cardColors(containerColor = BentoPrimaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Analyze Prompt Sparkle",
                tint = BentoOnPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SPARKLE",
                color = BentoOnPrimaryContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun FastModeCard(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoYellowContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Fast Mode Icon",
                tint = BentoOnYellowContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "FAST RESPONSES",
                color = BentoOnYellowContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun SecureCard(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoRedContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Secure Lock Icon",
                tint = BentoOnRedContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "SECURE & LOCAL",
                color = BentoOnRedContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun EmptyStateLayout(
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Welcome Sparkle Sphere
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GeminiGradientMiddle.copy(alpha = 0.4f),
                            GeminiGradientStart.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Emblem Sparkle",
                tint = GeminiGradientMiddle,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "හලෝ, මම නිකේෂා!",
            color = BentoTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Gemini සමඟ සම්බන්ධ වී ඇති මම ඔයාට කෙටියෙන්, පැහැදිලිව පිළිතුරු දෙන්න සූදානම්. ඕනෑම දෙයක් අහන්න!",
            color = BentoTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Suggestions Title inside bento
        Text(
            text = "NIKESHA SUGGESTIONS",
            color = BentoPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Grid-like clean cards
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SuggestionCard(
                    title = "ඔයා කවුද?",
                    subText = "Nikesha ගේ විස්තර දැනගන්න.",
                    onClick = { onSuggestionSelected("ඔයා කවුද?") },
                    modifier = Modifier.weight(1f)
                )
                SuggestionCard(
                    title = "කවියක් ලියන්න",
                    subText = "ලස්සන සිංහල කවි පද පේළි කිහිපයක්.",
                    onClick = { onSuggestionSelected("ලස්සන සිංහල කවි පද පේළි කිහිපයක් රචනා කරන්න.") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SuggestionCard(
                    title = "කෙටි කතාවක්",
                    subText = "විනෝදජනක වචන 30 ක කතාවක්.",
                    onClick = { onSuggestionSelected("විනෝදජනක වචන 30 ක කෙටි කතාවක් මට කියන්න.") },
                    modifier = Modifier.weight(1f)
                )
                SuggestionCard(
                    title = "Hobbies",
                    subText = "කරන්න පුළුවන් නව විනෝදාංශයක්.",
                    onClick = { onSuggestionSelected("මගේ විවේක කාලයට කරන්න පුළුවන් අලුත් විනෝදාංශයක් යෝජනා කරන්න.") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SuggestionCard(
    title: String,
    subText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = BentoBackground
        ),
        border = BorderStroke(1.dp, BentoBorder),
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Suggestion Sparkle",
                tint = BentoPrimary,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = title,
                    color = BentoTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subText,
                    color = BentoTextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    onSpeakClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Mini spark gradient sphere representing Nikesha avatar
            Box(
                modifier = Modifier
                    .padding(end = 6.dp, top = 4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(GeminiGradientStart, GeminiGradientMiddle)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Nikesha status bubble",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Surface(
            color = if (isUser) Color(0xFFE1E3E1) else Color(0xFFF0F4F9),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            border = if (!isUser) BorderStroke(1.dp, BentoBorder) else null,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable {
                    clipboardManager.setText(AnnotatedString(message.content))
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.imagePath != null) {
                    AsyncImage(
                        model = message.imagePath,
                        contentDescription = "Message Image Attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(bottom = 8.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Text(
                    text = message.content,
                    color = BentoTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.SansSerif
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.height(4.dp))
                    IconButton(
                        onClick = { onSpeakClicked(message.content) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = BentoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NikeshaTypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0)
        ),
        label = "dot_0"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(200)
        ),
        label = "dot_1"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(400)
        ),
        label = "dot_2"
    )

    Row(
        modifier = modifier
            .background(Color(0xFFF0F4F9), RoundedCornerShape(16.dp))
            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Nikesha compiles response", color = BentoTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(6.dp))
        listOf(scale1, scale2, scale3).forEach { scaleValue ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        scaleX = scaleValue
                        scaleY = scaleValue
                        alpha = scaleValue
                    }
                    .background(BentoPrimary, CircleShape)
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
    }
}

@Composable
fun ChatInputRow(
    textInput: String,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    isSending: Boolean,
    selectedImagePath: String?,
    onPickImage: () -> Unit,
    onClearImageClicked: () -> Unit,
    onMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (selectedImagePath != null) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp, start = 8.dp)
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = selectedImagePath,
                        contentDescription = "Selected image preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    IconButton(
                        onClick = onClearImageClicked,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear selected image",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BentoInputContainer, RoundedCornerShape(32.dp))
                    .padding(vertical = 4.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPickImage,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach photo",
                        tint = BentoPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onMicClicked,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Speak to Nikesha",
                        tint = BentoPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                TextField(
                    value = textInput,
                    onValueChange = onTextChanged,
                    placeholder = {
                        Text(
                            text = "Nikesha ගෙන් අහන්න...",
                            color = BentoTextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("user_input_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = BentoTextPrimary,
                        unfocusedTextColor = BentoTextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = BentoPrimary
                    ),
                    shape = CircleShape,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(4.dp))

                val canSend = (textInput.trim().isNotEmpty() || selectedImagePath != null) && !isSending
                FloatingActionButton(
                    onClick = {
                        if (canSend) {
                            onSendClicked()
                        }
                    },
                    containerColor = if (canSend) BentoPrimary else Color.Transparent,
                    contentColor = if (canSend) Color.White else BentoTextSecondary.copy(alpha = 0.5f),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    uiState: ChatUiState,
    onStartNewChat: () -> Unit,
    onStartVoiceChat: () -> Unit,
    onStartImageChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onPromptSuggested: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Sleek Greeting / Welcome Segment
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BentoSurface),
                border = BorderStroke(1.dp, BentoBorder),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ආයුබෝවන්, ${uiState.googleName ?: "මිතුරා"}! 👋",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BentoTextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "නිකේෂා - ද්වීභාෂික සිංහල AI සහායකයා වෙත සාදරයෙන් පිළිගනිමු. ඔබගේ ප්‍රශ්න විමසන්න, පින්තූර විශ්ලේෂණය කරන්න, සහ කටහඬින් කතාබස් කරන්න.",
                            fontSize = 12.sp,
                            color = BentoTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // AI Core Badge
                    Image(
                        painter = painterResource(id = R.drawable.img_nikesha_logo),
                        contentDescription = "Nikesha AI",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, BentoPrimary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // 2. Bento Actions - Core Entry Points
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ඉක්මන් ක්‍රියා (QUICK ACTIONS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoPrimary,
                    letterSpacing = 1.sp
                )

                // CTA 1: Full-width Chat session starting block
                Card(
                    onClick = onStartNewChat,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(GeminiGradientStart, GeminiGradientMiddle)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = "Chat",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "නව කතාබහක් අරඹන්න",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Start a fully interactive bilingual chat",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Enter",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // CTA 2 & 3: Double Bento grid row (Voice & Image analyzer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Voice mode bento
                    Card(
                        onClick = onStartVoiceChat,
                        colors = CardDefaults.cardColors(containerColor = BentoSurface),
                        border = BorderStroke(1.dp, BentoBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(BentoPrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Speech Voice",
                                        tint = BentoPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Go",
                                    tint = BentoTextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "කටහඬ සංවාද",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                                Text(
                                    text = "Auto-speak Voice mode",
                                    fontSize = 10.sp,
                                    color = BentoTextSecondary
                                )
                            }
                        }
                    }

                    // Image mode bento
                    Card(
                        onClick = onStartImageChat,
                        colors = CardDefaults.cardColors(containerColor = BentoSurface),
                        border = BorderStroke(1.dp, BentoBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(BentoYellowContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Add image",
                                        tint = BentoOnYellowContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Go",
                                    tint = BentoTextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "ඡායාරූප විමසීම්",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                                Text(
                                    text = "Analyze visuals with AI",
                                    fontSize = 10.sp,
                                    color = BentoTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Recent Conversations
        val recentSessions = uiState.sessions.take(4)
        if (recentSessions.isNotEmpty()) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "මෑතකදී කළ සංවාද (RECENT CHATS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    recentSessions.forEach { sessionName ->
                        val isDefault = sessionName == "default"
                        val displayName = if (isDefault) "ප්‍රධාන කතාබහ (General Chat)" else {
                            val id = sessionName.replace("Chat Session ", "")
                            "සංවාදය #$id"
                        }

                        Card(
                            onClick = { onSelectSession(sessionName) },
                            colors = CardDefaults.cardColors(containerColor = BentoSurface),
                            border = BorderStroke(1.dp, BentoBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(BentoInputContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "History",
                                            tint = BentoTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BentoTextPrimary
                                        )
                                        Text(
                                            text = if (isDefault) "Default sandbox workspace" else "Past session database ID: $displayName",
                                            fontSize = 10.sp,
                                            color = BentoTextSecondary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Enter chat",
                                    tint = BentoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Guided Sparkle Suggestions
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Ideas",
                        tint = Color(0xFFF4B400),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "සංවාද සඳහා යෝජනා (SUGGESTED TOPICS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                val prompts = listOf(
                    "ලංකාවේ ලස්සනම සංචාරක ස්ථාන 5ක් මොනවාද? 🌴",
                    "මට සිංහල කවියක් ලියලා දෙන්න. 📝",
                    "සෞඛ්‍ය සම්පන්න ආහාර වේලක් සාදාගන්නේ කෙසේද? 🍎",
                    "තේ වගාවේ ඉතිහාසය කෙටියෙන් පැහැදිලි කරන්න. ☕"
                )

                prompts.forEach { prompt ->
                    Card(
                        onClick = { onPromptSuggested(prompt) },
                        colors = CardDefaults.cardColors(containerColor = BentoInputContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 12.sp,
                                color = BentoTextPrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Send suggestion",
                                tint = BentoTextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SiriLikeWaveform(
    isSpeaking: Boolean,
    isListening: Boolean,
    isThinking: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isSpeaking) 1.6f else if (isThinking) 0.8f else 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isSpeaking) 1.9f else if (isThinking) 1.1f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isSpeaking) 1.4f else if (isThinking) 0.7f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(310, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale3"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 12) {
            val scale = when (i % 3) {
                0 -> scale1
                1 -> scale2
                else -> scale3
            }
            val heightDp = (14 + (i % 4) * 6).dp
            val brush = Brush.verticalGradient(
                colors = listOf(
                    GeminiGradientStart,
                    GeminiGradientMiddle,
                    GeminiGradientEnd
                )
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .height(heightDp * scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(brush = brush)
            )
        }
    }
}

@Composable
fun VoiceCallScreen(
    uiState: ChatUiState,
    onEndCall: () -> Unit,
    onTriggerMic: () -> Unit,
    voicePitch: Float,
    onPitchChanged: (Float) -> Unit,
    speechRate: Float,
    onSpeechRateChanged: (Float) -> Unit,
    isMuted: Boolean,
    onMuteToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isThinking = uiState.isSending
    // Approximate if speaking using message stream states or TTS ready
    val isSpeaking = !uiState.isSending && uiState.messages.lastOrNull()?.role == "model"
    val isListening = !isThinking && !isSpeaking && !isMuted

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSpeaking) 1.15f else if (isListening) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, BentoBorder.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Meta Information
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isThinking) Color.Yellow else if (isSpeaking) Color.Green else GeminiGradientMiddle)
                        )
                        Text(
                            text = if (isThinking) "ක්‍රියාකාරී ඇමතුම (ACTIVE CALL)" else "සම්බන්ධිතයි (CONNECTED)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Text(
                    text = "ද්වීභාෂික හඬ සහායිකා (Nikesha)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CosmicTextPrimary
                )
                Text(
                    text = if (isThinking) "නිකේෂා සිතමින් පවතී... 💭"
                           else if (isSpeaking) "නිකේෂා පිළිතුරු ලබාදේ... 🌸"
                           else if (isMuted) "හඬ නිහඬ කර ඇත (Muted)"
                           else "සවන් දෙමින්... (Listening) 🎤",
                    fontSize = 13.sp,
                    color = if (isThinking) Color.Yellow else if (isSpeaking) CosmicAccent else CosmicTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Central Glowing Avatar Section
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
            ) {
                // Glow Backdrop Circles
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    GeminiGradientStart.copy(alpha = if (isSpeaking) 0.35f else 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    GeminiGradientMiddle.copy(alpha = if (isSpeaking) 0.45f else 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Image(
                    painter = painterResource(id = R.drawable.img_nikesha_logo),
                    contentDescription = "Active call chatbot logo",
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(
                                3.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(GeminiGradientStart, GeminiGradientMiddle, GeminiGradientEnd)
                                )
                            ),
                            CircleShape
                        ),
                    contentScale = ContentScale.Crop
                )
            }

            // Real-time Waveform visualizer
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SiriLikeWaveform(
                    isSpeaking = isSpeaking,
                    isListening = isListening,
                    isThinking = isThinking
                )
                
                // Show a brief transcription of what the user last said or Nikesha is saying
                val lastMsg = uiState.messages.lastOrNull()
                if (lastMsg != null) {
                    Text(
                        text = "\"${lastMsg.content.take(85)}${if(lastMsg.content.length > 85) "..." else ""}\"",
                        fontSize = 12.sp,
                        color = CosmicTextSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // Customization sliders / Pitch controls block to customize the sweet/cute girl voice tone!
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicBackground.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, BentoBorder.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title for voice customization
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "හඬ සැකසුම් (VOICE SOUND TUNING)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicAccent,
                            letterSpacing = 0.5.sp
                        )
                        val voicePresetLabel = when {
                            voicePitch >= 1.4f -> "Cute Anime Voice 🎀"
                            voicePitch >= 1.25f -> "Sweet Girl Voice 🌸"
                            voicePitch >= 1.05f -> "Friendly Female Voice 👩"
                            voicePitch >= 0.95f -> "Normal Voice 👤"
                            else -> "Deep Voice 🎙️"
                        }
                        Text(
                            text = voicePresetLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextPrimary
                        )
                    }

                    // Pitch Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "මිහිරි බව / Pitch ශබ්දය: ${String.format(Locale.US, "%.2f", voicePitch)}x",
                                fontSize = 11.sp,
                                color = CosmicTextSecondary
                            )
                        }
                        Slider(
                            value = voicePitch,
                            onValueChange = onPitchChanged,
                            valueRange = 0.8f..1.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = CosmicAccent,
                                activeTrackColor = CosmicAccent,
                                inactiveTrackColor = CosmicTextSecondary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }

                    // Speed Rate Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "කතා කරන වේගය: ${String.format(Locale.US, "%.2f", speechRate)}x",
                                fontSize = 11.sp,
                                color = CosmicTextSecondary
                            )
                        }
                        Slider(
                            value = speechRate,
                            onValueChange = onSpeechRateChanged,
                            valueRange = 0.7f..1.4f,
                            colors = SliderDefaults.colors(
                                thumbColor = CosmicPrimary,
                                activeTrackColor = CosmicPrimary,
                                inactiveTrackColor = CosmicTextSecondary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            // Tactical Control Action Buttons at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mute Action Button
                IconButton(
                    onClick = { onMuteToggled(!isMuted) },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) BentoOnRedContainer.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                        .border(1.dp, if (isMuted) BentoOnRedContainer else Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute / Unmute Call Microphone",
                        tint = if (isMuted) Color.Red else CosmicTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. Huge centered manual Speak / Microphone icon trigger button
                IconButton(
                    onClick = onTriggerMic,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(GeminiGradientStart, GeminiGradientMiddle, GeminiGradientEnd)
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = "Force microphone input prompt",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 3. Decline call action button (Hang Up)
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEA4335)) // Standard call decline red color
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Disconnect Active AI Voice Call",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
