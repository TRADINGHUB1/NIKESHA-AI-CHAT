package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val sessions: List<String> = listOf("default"),
    val activeSessionId: String = "default",
    val isSending: Boolean = false,
    val textInput: String = "",
    val isGoogleLoggedIn: Boolean = false,
    val googleEmail: String? = null,
    val googleName: String? = null,
    val selectedImagePath: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DatabaseProvider.getDatabase(application)
    private val repository = ChatRepository(database.messageDao())
    private val prefs = application.getSharedPreferences("nikesha_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeSessionJob: Job? = null

    init {
        // Load Google Login state from prefs
        val loggedIn = prefs.getBoolean("google_logged_in", false)
        val email = prefs.getString("google_email", null)
        val name = prefs.getString("google_name", null)
        
        // Start with a brand new session every time the app starts
        val activeSession = "Chat Session " + System.currentTimeMillis().toString().takeLast(4)
        prefs.edit().putString("active_session_id", activeSession).apply()

        _uiState.update { currentState ->
            currentState.copy(
                isGoogleLoggedIn = loggedIn,
                googleEmail = email,
                googleName = name,
                activeSessionId = activeSession
            )
        }

        // Start collecting messages for active session
        observeSessionMessages(activeSession)

        // Observe session lists from Db
        viewModelScope.launch {
            repository.getUniqueSessions().collect { sessionNames ->
                _uiState.update { currentState ->
                    val baseList = mutableListOf("default")
                    baseList.addAll(sessionNames.filter { it != "default" })
                    currentState.copy(sessions = baseList.distinct())
                }
            }
        }
    }

    private fun observeSessionMessages(sessionId: String) {
        activeSessionJob?.cancel()
        activeSessionJob = viewModelScope.launch {
            repository.getMessagesBySession(sessionId).collect { messageList ->
                _uiState.update { currentState ->
                    currentState.copy(messages = messageList)
                }
            }
        }
    }

    fun onTextInputChanged(text: String) {
        _uiState.update { it.copy(textInput = text) }
    }

    fun selectSession(sessionId: String) {
        prefs.edit().putString("active_session_id", sessionId).apply()
        _uiState.update { it.copy(activeSessionId = sessionId) }
        observeSessionMessages(sessionId)
    }

    fun createNewSession() {
        val newSessionId = "Chat Session " + System.currentTimeMillis().toString().takeLast(4)
        selectSession(newSessionId)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_uiState.value.activeSessionId == sessionId) {
                selectSession("default")
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val session = _uiState.value.activeSessionId
            repository.deleteSession(session)
        }
    }

    fun googleSignIn(email: String, name: String) {
        prefs.edit()
            .putBoolean("google_logged_in", true)
            .putString("google_email", email)
            .putString("google_name", name)
            .apply()

        _uiState.update { currentState ->
            currentState.copy(
                isGoogleLoggedIn = true,
                googleEmail = email,
                googleName = name
            )
        }
    }

    fun googleSignOut() {
        prefs.edit()
            .putBoolean("google_logged_in", false)
            .remove("google_email")
            .remove("google_name")
            .apply()

        _uiState.update { currentState ->
            currentState.copy(
                isGoogleLoggedIn = false,
                googleEmail = null,
                googleName = null
            )
        }
    }

    fun selectImage(uriString: String?) {
        if (uriString == null) {
            _uiState.update { it.copy(selectedImagePath = null) }
            return
        }
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val uri = android.net.Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = java.io.File(context.filesDir, "selected_image_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    _uiState.update { it.copy(selectedImagePath = file.absolutePath) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImagePath = null) }
    }

    fun sendMessage() {
        val currentInput = _uiState.value.textInput.trim()
        val imagePath = _uiState.value.selectedImagePath
        if ((currentInput.isEmpty() && imagePath == null) || _uiState.value.isSending) return

        val session = _uiState.value.activeSessionId
        _uiState.update { it.copy(textInput = "", selectedImagePath = null, isSending = true) }

        viewModelScope.launch {
            try {
                repository.sendMessageToGemini(currentInput, _uiState.value.messages, session, imagePath)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun sendSuggestion(suggestionText: String) {
        if (_uiState.value.isSending) return

        val session = _uiState.value.activeSessionId
        _uiState.update { it.copy(isSending = true) }

        viewModelScope.launch {
            try {
                repository.sendMessageToGemini(suggestionText, _uiState.value.messages, session)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }
}
