# Language Translator - Real-time Android App

A modern Android application that translates any language to English in real-time using speech recognition.

## Features

🎙️ **Real-time Speech Recognition** - Listen to audio and convert speech to text  
🌍 **Multi-language Support** - Auto-detects and translates from 25+ languages  
🌙 **Dark Mode** - Modern UI with automatic dark/light theme support  
📱 **Material Design** - Clean, intuitive interface following Material Design 3  
🔒 **Privacy-focused** - Uses free translation APIs, no data stored  
⚡ **Offline-ready UI** - App works without internet (translation requires network)

## Supported Languages

The app can detect and translate from these languages:
- **European**: English, Spanish, French, German, Italian, Portuguese, Dutch, Swedish, Danish, Norwegian, Finnish, Polish, Czech, Hungarian, Greek, Turkish
- **Asian**: Chinese (Simplified/Traditional), Japanese, Korean, Thai, Vietnamese, Indonesian, Malay, Filipino, Hindi
- **Middle Eastern**: Arabic, Hebrew
- **Slavic**: Russian, Polish, Czech, Hungarian

## Screenshots

### Light Mode
The app features a clean, modern interface with card-based layout for original text and translations.

### Dark Mode  
Automatic dark mode support that follows system preferences for comfortable night usage.

## Architecture

- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 33 (Android 13)
- **Architecture Pattern**: MVVM with coroutines
- **UI Framework**: Material Design 3 with View Binding
- **Translation API**: MyMemory (free translation service)
- **Speech Recognition**: Android built-in SpeechRecognizer

## Setup Instructions

### Prerequisites
- Android Studio (latest version recommended)
- Android SDK 33
- JDK 8 or higher
- Internet connection for building dependencies

### Building the Project

1. **Clone the repository**
   ```bash
   git clone https://github.com/mllinman/Language-Translater.git
   cd Language-Translater
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned directory and select it

3. **Sync Project**
   - Android Studio will automatically sync Gradle dependencies
   - Wait for the sync to complete

4. **Build the APK**
   ```bash
   ./gradlew assembleDebug
   ```
   Or use Android Studio's Build menu → Build Bundle(s) / APK(s) → Build APK(s)

5. **Install on Device**
   - Connect your Android device via USB with Developer Options enabled
   - Run the app from Android Studio, or
   - Install the APK manually: `adb install app/build/outputs/apk/debug/app-debug.apk`

### Alternative Build Methods

#### Command Line Build
```bash
# Ensure you have Android SDK and tools in your PATH
./gradlew build

# To build release APK (requires signing configuration)
./gradlew assembleRelease
```

#### GitHub Actions (CI/CD)
The project can be built automatically using GitHub Actions. Add the following workflow:

```yaml
name: Build Android APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
        
    - name: Setup Android SDK
      uses: android-actions/setup-android@v2
      
    - name: Build with Gradle
      run: ./gradlew assembleDebug
      
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

The app requires the following permissions:
- **RECORD_AUDIO**: For speech recognition and microphone access
- **INTERNET**: For translation API calls
- **ACCESS_NETWORK_STATE**: To check network connectivity

Runtime permissions are handled gracefully with user-friendly prompts.

## Usage

1. **Launch the app** and grant microphone permission when prompted
2. **Tap "Start Listening"** to begin speech recognition
3. **Speak in any supported language** - the app will show detected language
4. **View the translation** in English in real-time
5. **Use "Clear"** to reset and start over
6. **Tap "Stop Listening"** to end the current session

## Technical Implementation

### Key Components

#### MainActivity.kt
- Main activity handling UI interactions and speech recognition lifecycle
- Manages permissions using Dexter library
- Coordinates between speech recognition and translation services
- Handles UI state updates and error management

#### TranslationService.kt
- Service for language detection and translation
- Integrates with MyMemory translation API
- Pattern-based language detection for offline capability
- Error handling and fallback mechanisms

#### UI Components
- **Material Design 3** theming with dark mode support
- **Card-based layout** for clear separation of content
- **Progress indicators** for user feedback during operations
- **Responsive design** that works on different screen sizes

### Language Detection Algorithm

The app uses a pattern-based approach to detect languages:
1. **Dictionary matching** against common words in each language
2. **Script detection** for languages with unique scripts (Arabic, Chinese, etc.)
3. **Statistical analysis** of word patterns and frequencies
4. **Fallback to "auto"** for unknown languages

### Translation Flow

1. Speech recognition captures audio and converts to text
2. Language detection identifies the source language
3. If not English, text is sent to translation API
4. Translated result is displayed in real-time
5. Error handling provides user feedback for failures

## API Integration

### MyMemory Translation API
- **Free tier**: 1000 requests per day
- **No API key required** for basic usage
- **RESTful API** with JSON responses
- **Supports 50+ language pairs**

Example API call:
```
GET https://api.mymemory.translated.net/get?q=Hello&langpair=en|es
```

### Alternative APIs
The translation service can be easily extended to support:
- Google Translate API (paid)
- Microsoft Translator (paid)
- LibreTranslate (free, self-hosted)
- AWS Translate (paid)

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Troubleshooting

### Build Issues
- **Gradle sync fails**: Check internet connection and Android SDK installation
- **Dependencies not found**: Try cleaning project (`./gradlew clean`) and rebuilding
- **API level errors**: Update Android SDK and build tools

### Runtime Issues
- **Microphone not working**: Check permissions in device settings
- **Translation fails**: Verify internet connection and API availability
- **App crashes**: Check logs and ensure proper error handling

### Performance
- **Slow translation**: Check network speed and API response times
- **High battery usage**: Limit continuous listening sessions
- **Memory issues**: Clear text regularly and avoid long sessions

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Android Speech Recognition API
- MyMemory Translation API
- Material Design Components
- Kotlin Coroutines
- Dexter Permissions Library

## Future Enhancements

- [ ] Support for more languages (100+ languages)
- [ ] Offline translation capabilities
- [ ] Voice output (text-to-speech) for translations
- [ ] Conversation mode (bidirectional translation)
- [ ] History and favorites
- [ ] Custom language models
- [ ] Widget support
- [ ] Wear OS companion app
