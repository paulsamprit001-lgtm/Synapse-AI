package com.synapseai

/**
 * JNI API contract. Keep inference on Dispatchers.Default/IO, never the UI thread.
 */
class LocalEngine {
    external fun loadModel(path: String, contextTokens: Int, threads: Int): Boolean
    external fun generate(prompt: String, maxTokens: Int): String
    external fun stopGeneration()
    external fun unloadModel()
    external fun isLoaded(): Boolean

    companion object {
        init { System.loadLibrary("synapse") }
    }
}
