package com.example.captiontranslator

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationManager(context: Context) {

    private val TAG = "TranslationManager"

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.CHINESE)
        .build()

    private val translator = Translation.getClient(options)
    private var isModelReady = false

    fun downloadModel(onReady: () -> Unit) {
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Log.d(TAG, "翻译模型下载完成")
                isModelReady = true
                onReady()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "模型下载失败: ${e.message}")
            }
    }

    fun translate(englishText: String, callback: (TranslationResult) -> Unit) {
        if (!isModelReady) {
            callback(TranslationResult.ModelNotReady)
            return
        }

        translator.translate(englishText)
            .addOnSuccessListener { translatedText ->
                callback(TranslationResult.Success(translatedText))
            }
            .addOnFailureListener { e ->
                callback(TranslationResult.Error(e.message ?: "未知错误"))
            }
    }

    fun close() {
        translator.close()
    }
}

sealed class TranslationResult {
    data class Success(val chinese: String) : TranslationResult()
    data class Error(val message: String) : TranslationResult()
    object ModelNotReady : TranslationResult()
}
