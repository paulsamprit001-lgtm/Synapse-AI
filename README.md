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


## Updates and data safety

- Keep the same application ID (`com.synapseai`) for every release.
- Increase `versionCode` for each new APK.
- Install updates over the existing app; **do not uninstall** it. Android preserves the app's private files and preferences during a normal in-place update.
- The imported model is stored at `filesDir/synapse-model.gguf` and is automatically detected and loaded when the app starts.
- Chat history can be exported and restored using the in-app **Backup** and **Restore** actions. Store backup JSON somewhere outside the app before testing major updates.
- A normal update cannot protect data from uninstalling the app, clearing its storage, device loss, or a broken storage migration. Keep external backups.

## Stable release signing (required for install-over updates)

The workflow now supports a stable signed release, but it falls back to a debug APK until all four GitHub Actions repository secrets are configured:

- `SYNAPSE_KEYSTORE_BASE64`: base64-encoded permanent Java keystore file.
- `SYNAPSE_KEYSTORE_PASSWORD`: keystore password.
- `SYNAPSE_KEY_ALIAS`: key alias.
- `SYNAPSE_KEY_PASSWORD`: key password.

Create the keystore once on a trusted computer, keep a secure offline copy, and never commit it or share its passwords in chat. Add the four values under GitHub repository **Settings → Secrets and variables → Actions → New repository secret**. The keystore must remain the same for every future release; losing it means future APKs cannot update installations signed with it.

**Important transition note:** the previously distributed v0.3.0 APK was debug-signed. A new stable release key will not match that signature, so the first stable release cannot be installed over that debug build. Before switching, export a Synapse chat backup and keep the original GGUF model file available. The app's private model file will be lost if the old app must be uninstalled. Once the stable release is installed, later releases signed with the same key can update in place.
