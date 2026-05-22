package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

class ChatRepository(private val messageDao: MessageDao) {

    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessagesFlow()

    fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesBySessionFlow(sessionId)
    }

    fun getUniqueSessions(): Flow<List<String>> {
        return messageDao.getUniqueSessionsFlow()
    }

    private fun getInlineDataFromPath(path: String?): InlineData? {
        if (path == null) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(path) ?: return null
            val outputStream = ByteArrayOutputStream()
            // Compress image slightly to fit request size quickly
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            InlineData(mimeType = "image/jpeg", data = base64Data)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteSession(sessionId: String) {
        messageDao.deleteSession(sessionId)
    }

    suspend fun sendMessageToGemini(
        userText: String,
        previousMessages: List<MessageEntity>,
        sessionId: String = "default",
        imagePath: String? = null
    ): String {
        // Create user message in DB with local imagePath
        val userMessage = MessageEntity(
            role = "user",
            content = userText,
            sessionId = sessionId,
            imagePath = imagePath
        )
        messageDao.insertMessage(userMessage)

        // Map previous messages and the new user message to GeminiContent format
        val chatContents = mutableListOf<GeminiContent>()

        // Add historic messages
        previousMessages.forEach { msg ->
            val roleName = if (msg.role == "user") "user" else "model"
            val partsList = mutableListOf<GeminiPart>()
            partsList.add(GeminiPart(text = msg.content))
            
            val inlineImg = getInlineDataFromPath(msg.imagePath)
            if (inlineImg != null) {
                partsList.add(GeminiPart(inlineData = inlineImg))
            }

            chatContents.add(
                GeminiContent(
                    role = roleName,
                    parts = partsList
                )
            )
        }

        // Add the current message
        val currentParts = mutableListOf<GeminiPart>()
        currentParts.add(GeminiPart(text = userText))
        val currentInlineImg = getInlineDataFromPath(imagePath)
        if (currentInlineImg != null) {
            currentParts.add(GeminiPart(inlineData = currentInlineImg))
        }

        chatContents.add(
            GeminiContent(
                role = "user",
                parts = currentParts
            )
        )

        // System prompt instructs Nikesha’s identity and short response limits
        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "You are Nikesha, a friendly, intelligent, and highly charming female AI chatbot. " +
                            "You must keep your responses extremely short, polite, and concise, strictly within 1 or 2 sentences. " +
                            "If the user asks in Sinhala, respond nicely in Sinhala. If the user asks in English, respond in English."
                )
            )
        )

        val request = GeminiRequest(
            contents = chatContents,
            systemInstruction = systemInstruction,
            generationConfig = GeminiRequestConfig(temperature = 0.7f)
        )

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val errorMsg = "Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel."
            val modelMsg = MessageEntity(role = "model", content = errorMsg, sessionId = sessionId)
            messageDao.insertMessage(modelMsg)
            return errorMsg
        }

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val modelResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I couldn't generate a response. Please try again."

            // Save model response to local database
            val modelMessage = MessageEntity(role = "model", content = modelResponseText, sessionId = sessionId)
            messageDao.insertMessage(modelMessage)

            modelResponseText
        } catch (e: Exception) {
            val errorText = "Error: Unable to connect to Nikesha. (${e.localizedMessage ?: "Network error"})"
            val modelMessage = MessageEntity(role = "model", content = errorText, sessionId = sessionId)
            messageDao.insertMessage(modelMessage)
            errorText
        }
    }

    suspend fun clearChatHistory() {
        messageDao.clearHistory()
    }
}
