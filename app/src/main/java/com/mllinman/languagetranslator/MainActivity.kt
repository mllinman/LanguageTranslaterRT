package com.mllinman.languagetranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.mllinman.languagetranslator.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var translationService: TranslationService
    private lateinit var subscriptionManager: SubscriptionManager
    private var isListening = false
    
    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 101
        private const val SUBSCRIPTION_REQUEST_CODE = 102
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupSubscriptionManager()
        setupTranslationService()
        setupClickListeners()
        updateSubscriptionUI()
        
        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
            binding.listenButton.isEnabled = false
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }
    
    private fun setupSubscriptionManager() {
        subscriptionManager = SubscriptionManager(this)
    }
    
    private fun setupTranslationService() {
        translationService = TranslationService(subscriptionManager)
    }
    
    private fun setupClickListeners() {
        binding.listenButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                checkPermissionAndStartListening()
            }
        }
        
        binding.clearButton.setOnClickListener {
            clearTexts()
        }
    }
    
    private fun checkPermissionAndStartListening() {
        Dexter.withContext(this)
            .withPermission(Manifest.permission.RECORD_AUDIO)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    startListening()
                }
                
                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.permission_denied),
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            }).check()
    }
    
    private fun startListening() {
        if (isListening) return
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(speechRecognitionListener)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        isListening = true
        updateUI()
        speechRecognizer.startListening(intent)
    }
    
    private fun stopListening() {
        if (!isListening) return
        
        speechRecognizer.stopListening()
        speechRecognizer.destroy()
        isListening = false
        updateUI()
    }
    
    private fun clearTexts() {
        binding.originalText.text = ""
        binding.translatedText.text = ""
        binding.detectedLanguageText.text = ""
        binding.detectedLanguageText.visibility = android.view.View.GONE
    }
    
    private fun updateUI() {
        if (isListening) {
            binding.statusText.text = getString(R.string.listening)
            binding.listenButton.text = getString(R.string.stop_listening)
            binding.listenButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_stop)
            binding.progressIndicator.visibility = android.view.View.VISIBLE
        } else {
            binding.statusText.text = getString(R.string.start_listening)
            binding.listenButton.text = getString(R.string.start_listening)
            binding.listenButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_microphone)
            binding.progressIndicator.visibility = android.view.View.GONE
        }
    }
    
    private fun translateText(text: String, detectedLanguage: String) {
        binding.statusText.text = getString(R.string.translating)
        binding.progressIndicator.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    translationService.translateToEnglish(text, detectedLanguage)
                }
                
                when (result) {
                    is TranslationResult.Success -> {
                        binding.translatedText.text = result.translatedText
                        binding.statusText.text = getString(R.string.start_listening)
                        updateSubscriptionUI() // Update usage display
                    }
                    
                    is TranslationResult.LimitExceeded -> {
                        binding.statusText.text = getString(R.string.subscription_limit_reached)
                        binding.translatedText.text = result.message
                        showUpgradeDialog()
                    }
                    
                    is TranslationResult.Error -> {
                        binding.statusText.text = getString(R.string.translation_error)
                        binding.translatedText.text = result.originalText
                        Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
                
                binding.progressIndicator.visibility = android.view.View.GONE
                
            } catch (e: Exception) {
                binding.statusText.text = getString(R.string.translation_error)
                binding.progressIndicator.visibility = android.view.View.GONE
                Toast.makeText(this@MainActivity, getString(R.string.translation_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateSubscriptionUI() {
        val summary = subscriptionManager.getSubscriptionSummary()
        
        // Update toolbar subtitle with subscription info
        supportActionBar?.subtitle = when (summary.tier) {
            SubscriptionManager.SubscriptionTier.PRO -> "Pro • Unlimited"
            SubscriptionManager.SubscriptionTier.FREE -> "Free • ${summary.remainingTranslations} left today"
        }
    }
    
    private fun showUpgradeDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Daily Limit Reached")
            .setMessage("You've reached your daily translation limit. Upgrade to Pro for unlimited translations.")
            .setPositiveButton("Upgrade") { _, _ ->
                openSubscriptionActivity()
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }
    
    private fun openSubscriptionActivity() {
        val intent = Intent(this, SubscriptionActivity::class.java)
        startActivityForResult(intent, SUBSCRIPTION_REQUEST_CODE)
    }
    
    private val speechRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            binding.statusText.text = getString(R.string.listening)
        }
        
        override fun onBeginningOfSpeech() {
            // Speech input has begun
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - could be used for visual feedback
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }
        
        override fun onEndOfSpeech() {
            binding.statusText.text = "Processing..."
        }
        
        override fun onError(error: Int) {
            isListening = false
            updateUI()
            
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.no_speech_detected)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> getString(R.string.speech_recognition_error)
            }
            
            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
        }
        
        override fun onResults(results: Bundle?) {
            isListening = false
            updateUI()
            
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                binding.originalText.text = spokenText
                
                // Detect language and translate
                lifecycleScope.launch {
                    try {
                        val detectedLanguage = withContext(Dispatchers.IO) {
                            translationService.detectLanguage(spokenText)
                        }
                        
                        binding.detectedLanguageText.text = getString(R.string.detected_language, detectedLanguage)
                        binding.detectedLanguageText.visibility = android.view.View.VISIBLE
                        
                        if (detectedLanguage.lowercase() != "english" && detectedLanguage.lowercase() != "en") {
                            translateText(spokenText, detectedLanguage)
                        } else {
                            binding.translatedText.text = spokenText
                            binding.statusText.text = getString(R.string.start_listening)
                        }
                        
                    } catch (e: Exception) {
                        // Fallback: assume it's not English and try to translate anyway
                        translateText(spokenText, "auto")
                    }
                }
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                binding.originalText.text = matches[0]
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            // Reserved for future use
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_subscription -> {
                openSubscriptionActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SUBSCRIPTION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Subscription status may have changed, update UI
            updateSubscriptionUI()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateSubscriptionUI()
    }
}