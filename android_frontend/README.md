# VoiceCalc Pro - Android Frontend

A modern Android application (Kotlin, AndroidX, Material) implementing an AI-powered calculator with:
- Voice input via microphone button
- Real-time transcription display
- Text-based advanced calculator (sin, cos, tan, sqrt, parentheses, power)
- Calculation history
- Navigation drawer (Calculator, History, Settings)
- Theme switching (light/dark)
- Robust error handling for voice input
- Integration with an AI backend over HTTP

Design
- Colors: Primary #1976D2, Secondary #424242, Accent #FF4081
- Light theme by default with Dark theme toggle in drawer header.

Build and Run
- Uses Gradle 9 Declarative DSL sample project.
- To build: ./gradlew :app:build
- To install debug: ./gradlew :app:installDebug

Environment Variables
Provide these environment variables and ensure your build pipeline maps them to Android Manifest placeholders (manifestPlaceholders):
- AI_BASE_URL (e.g., https://your-ai-backend.example.com)
- AI_API_KEY (optional)

Example .env.example included at android_frontend/.env.example.

Backend Contract
POST {AI_BASE_URL}/v1/calc
Body: { "expression": "<string>" }
Response: { "result": "<string>", "error": "<string|null>" }

Notes
- Microphone permission is requested at runtime.
- If speech recognition is unavailable, the app will show a snackbar message.
