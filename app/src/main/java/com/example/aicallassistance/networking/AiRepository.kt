package com.example.aicallassistance.networking

import com.example.aicallassistance.networking.response.AiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AiRepository {

    // This is a placeholder. In a real app, you would use the ApiService with Retrofit.
    suspend fun getAiResponse(prompt: String): AiResponse {
        return withContext(Dispatchers.IO) {
            // Simulate network delay
            delay(1500)

            // Return a canned response
            val responseText = when {
                prompt.contains("hello", ignoreCase = true) -> "Hello there! How can I help you?"
                prompt.contains("how are you", ignoreCase = true) -> "I am just a computer program, but thank you for asking!"
                prompt.contains("goodbye", ignoreCase = true) -> "Goodbye! Have a great day."
                else -> "I'm sorry, I don't understand. Can you please rephrase?"
            }
            AiResponse(responseText)
        }
    }
}
