package com.mllinman.languagetranslator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Result type for translation operations
 */
sealed class TranslationResult {
    data class Success(val translatedText: String) : TranslationResult()
    data class Error(val message: String, val originalText: String) : TranslationResult()
    data class LimitExceeded(val message: String) : TranslationResult()
}

class TranslationService(private val subscriptionManager: SubscriptionManager? = null) {
    
    companion object {
        private const val TAG = "TranslationService"
        
        // Using a free translation service - MyMemory API
        private const val TRANSLATE_API_URL = "https://api.mymemory.translated.net/get"
        
        // Language codes mapping
        private val LANGUAGE_CODES = mapOf(
            "english" to "en",
            "spanish" to "es",
            "french" to "fr",
            "german" to "de",
            "italian" to "it",
            "portuguese" to "pt",
            "russian" to "ru",
            "chinese" to "zh",
            "japanese" to "ja",
            "korean" to "ko",
            "arabic" to "ar",
            "hindi" to "hi",
            "dutch" to "nl",
            "swedish" to "sv",
            "danish" to "da",
            "norwegian" to "no",
            "finnish" to "fi",
            "polish" to "pl",
            "czech" to "cs",
            "hungarian" to "hu",
            "greek" to "el",
            "turkish" to "tr",
            "hebrew" to "he",
            "thai" to "th",
            "vietnamese" to "vi",
            "indonesian" to "id",
            "malay" to "ms",
            "filipino" to "tl"
        )
    }
    
    /**
     * Detects the language of the input text
     * This is a simple implementation that could be enhanced with a proper language detection API
     */
    suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        try {
            // For this demo, we'll use a simple approach
            // In a production app, you'd want to use Google's Language Detection API or similar
            
            // Try to detect common patterns or use a free language detection service
            return@withContext detectLanguageByPattern(text)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting language", e)
            return@withContext "Unknown"
        }
    }
    
    /**
     * Translates text to English using MyMemory API with tier-based access control
     */
    suspend fun translateToEnglish(text: String, sourceLanguage: String): TranslationResult = withContext(Dispatchers.IO) {
        try {
            // Check subscription status and usage limits
            subscriptionManager?.let { manager ->
                if (!manager.canMakeTranslation()) {
                    val remaining = manager.getRemainingTranslations()
                    return@withContext TranslationResult.LimitExceeded(
                        "Daily translation limit reached. $remaining translations remaining today. Upgrade to Pro for unlimited translations."
                    )
                }
            }
            
            val sourceCode = getLanguageCode(sourceLanguage)
            val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
            
            val urlString = "$TRANSLATE_API_URL?q=$encodedText&langpair=${sourceCode}|en"
            Log.d(TAG, "Translation URL: $urlString")
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LanguageTranslator/1.0")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "Response: $response")
                
                val jsonResponse = JSONObject(response)
                val responseData = jsonResponse.getJSONObject("responseData")
                val translatedText = responseData.getString("translatedText")
                
                // Record usage for free tier users
                subscriptionManager?.recordTranslationUsage()
                
                return@withContext if (translatedText.isNotEmpty()) {
                    TranslationResult.Success(translatedText)
                } else {
                    TranslationResult.Error("Empty translation received", text)
                }
            } else {
                Log.e(TAG, "HTTP error: $responseCode")
                return@withContext TranslationResult.Error("Network error: $responseCode", text)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            return@withContext TranslationResult.Error("Translation failed: ${e.message}", text)
        }
    }
    
    /**
     * Simple language detection based on common patterns
     * This is a basic implementation - in production, use proper language detection
     */
    private fun detectLanguageByPattern(text: String): String {
        val lowerText = text.lowercase().trim()
        
        // Common patterns for different languages
        return when {
            // Spanish patterns
            lowerText.contains(Regex("\\b(el|la|los|las|y|de|en|un|una|con|por|para|que|se|no|me|te|le|nos|os|les)\\b")) -> "Spanish"
            
            // French patterns
            lowerText.contains(Regex("\\b(le|la|les|de|du|des|et|un|une|dans|pour|avec|sur|par|ce|cette|qui|que|ne|pas|je|tu|il|elle|nous|vous|ils|elles)\\b")) -> "French"
            
            // German patterns
            lowerText.contains(Regex("\\b(der|die|das|und|oder|ein|eine|mit|von|zu|auf|für|in|ist|sind|ich|du|er|sie|es|wir|ihr|sie|nicht|auch|aber|wenn)\\b")) -> "German"
            
            // Italian patterns
            lowerText.contains(Regex("\\b(il|la|lo|gli|le|di|da|in|con|su|per|tra|fra|a|e|che|non|un|una|io|tu|lui|lei|noi|voi|loro|sono|è)\\b")) -> "Italian"
            
            // Portuguese patterns
            lowerText.contains(Regex("\\b(o|a|os|as|de|da|do|em|para|com|por|que|não|um|uma|eu|tu|ele|ela|nós|vós|eles|elas|ser|estar)\\b")) -> "Portuguese"
            
            // Russian (Cyrillic characters)
            lowerText.contains(Regex("[а-яё]")) -> "Russian"
            
            // Chinese (Chinese characters)
            lowerText.contains(Regex("[\\u4e00-\\u9fff]")) -> "Chinese"
            
            // Japanese (Hiragana, Katakana, or Kanji)
            lowerText.contains(Regex("[ひらがなカタカナ\\u3040-\\u309f\\u30a0-\\u30ff\\u4e00-\\u9fff]")) -> "Japanese"
            
            // Korean (Hangul)
            lowerText.contains(Regex("[\\uac00-\\ud7af]")) -> "Korean"
            
            // Arabic (Arabic script)
            lowerText.contains(Regex("[\\u0600-\\u06ff]")) -> "Arabic"
            
            // Default to English or Unknown
            else -> if (isEnglishPattern(lowerText)) "English" else "Unknown"
        }
    }
    
    /**
     * Check if text appears to be English based on common English words
     */
    private fun isEnglishPattern(text: String): Boolean {
        val commonEnglishWords = setOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with",
            "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "she", "or", "an",
            "will", "my", "one", "all", "would", "there", "their"
        )
        
        val words = text.split(Regex("\\W+")).filter { it.isNotEmpty() }
        val englishWordCount = words.count { it.lowercase() in commonEnglishWords }
        
        return englishWordCount > words.size * 0.3 // If 30% or more words are common English words
    }
    
    /**
     * Get language code for translation API
     */
    private fun getLanguageCode(language: String): String {
        val normalizedLanguage = language.lowercase().trim()
        return LANGUAGE_CODES[normalizedLanguage] ?: when {
            normalizedLanguage.length == 2 -> normalizedLanguage // Assume it's already a language code
            normalizedLanguage == "auto" -> "auto"
            else -> "auto" // Let the API auto-detect
        }
    }
}