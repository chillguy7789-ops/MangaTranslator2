package com.mangatranslator

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Translation service using LibreTranslate API (free and open-source)
 * You can host your own instance or use a public one
 */
class TranslationService {
    
    companion object {
        private const val TAG = "TranslationService"
        // Using a public LibreTranslate instance - you can change this to your own
        private const val BASE_URL = "https://libretranslate.com/"
        
        // Language codes
        const val LANG_EN = "en"
        const val LANG_JA = "ja"
        const val LANG_ZH = "zh"
        const val LANG_KO = "ko"
        const val LANG_ES = "es"
        const val LANG_FR = "fr"
        const val LANG_DE = "de"
    }
    
    // API Data Classes
    data class TranslateRequest(
        @SerializedName("q") val text: String,
        @SerializedName("source") val source: String,
        @SerializedName("target") val target: String,
        @SerializedName("format") val format: String = "text"
    )
    
    data class TranslateResponse(
        @SerializedName("translatedText") val translatedText: String
    )
    
    // Retrofit API Interface
    interface LibreTranslateApi {
        @POST("translate")
        suspend fun translate(@Body request: TranslateRequest): TranslateResponse
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api = retrofit.create(LibreTranslateApi::class.java)
    
    /**
     * Translate text from source language to target language
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Text is empty"))
            }
            
            Log.d(TAG, "Translating: $text ($sourceLang -> $targetLang)")
            
            val request = TranslateRequest(
                text = text.trim(),
                source = sourceLang,
                target = targetLang
            )
            
            val response = api.translate(request)
            Log.d(TAG, "Translation result: ${response.translatedText}")
            
            Result.success(response.translatedText)
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Translate multiple texts in batch
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String,
        targetLang: String
    ): List<Result<String>> = withContext(Dispatchers.IO) {
        texts.map { text ->
            translate(text, sourceLang, targetLang)
        }
    }
    
    /**
     * Get language name from code
     */
    fun getLanguageName(code: String): String {
        return when (code) {
            LANG_EN -> "English"
            LANG_JA -> "Japanese"
            LANG_ZH -> "Chinese"
            LANG_KO -> "Korean"
            LANG_ES -> "Spanish"
            LANG_FR -> "French"
            LANG_DE -> "German"
            else -> "Unknown"
        }
    }
}
