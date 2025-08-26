package com.example.aicallassistance.networking

import com.example.aicallassistance.networking.request.AiRequest
import com.example.aicallassistance.networking.response.AiResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("v1/completions") // Example endpoint
    suspend fun getAiResponse(@Body request: AiRequest): AiResponse

}
