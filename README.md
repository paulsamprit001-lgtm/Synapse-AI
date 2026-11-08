# Synapse AI — Android offline chatbot

This is an experimental Android app using Kotlin, Jetpack Compose, JNI, and llama.cpp for local GGUF inference.

## Build
A GitHub Actions workflow builds a debug APK on pushes to main. Open the repository's Actions tab, select the latest "Build Synapse AI APK" run, and download the "SynapseAI-debug-apk" artifact. Extract app-debug.apk from the downloaded artifact.

## Install and use
1. Install the debug APK on an Android device (allow installation from the browser/file manager if Android asks).
2. Obtain a compatible GGUF model separately; model files are not included.
3. Open Synapse AI and use Import GGUF to select the model.
4. Start chatting after the model status says it is ready.

## Important limitations
- This is an early prototype, not a finished or device-verified product.
- The app has not yet been compiled or tested on the target phone.
- The workflow builds a debug APK only; it does not produce a signed release.
- The model is not bundled. Inference runs locally after a compatible model is imported.
- Token streaming, multi-chat management, and model-specific chat templates are not implemented.
- A 0.5B model may still use too much memory on a 3 GB phone. Start with a smaller quantized model and test carefully.
- The first CI build downloads Android build dependencies and llama.cpp source.

Do not commit passwords, signing keys, personal data, or model files.
