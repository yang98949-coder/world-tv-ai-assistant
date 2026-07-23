package com.example.captiontranslator

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.captiontranslator.databinding.OverlaySubtitleBinding
import java.util.regex.Pattern

class CaptionTranslationService : AccessibilityService() {

    private val TAG = "CaptionTranslator"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var translationManager: TranslationManager
    private lateinit var windowManager: WindowManager

    private var overlayBinding: OverlaySubtitleBinding? = null
    private var overlayView: android.view.View? = null

    private var pendingText: String? = null
    private var debounceRunnable: Runnable? = null
    private val DEBOUNCE_DELAY = 800L

    private val englishPattern = Pattern.compile("[a-zA-Z]{3,}")
    private val minTextLength = 8
    private val maxTextLength = 300
    private val recentTexts = ArrayDeque<String>(10)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "服务已连接")

        translationManager = TranslationManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        translationManager.downloadModel {
            Log.d(TAG, "翻译模型准备就绪")
        }

        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ) {
            return
        }

        val source = event.source ?: return
        val text = extractText(source)
        source.recycle()

        if (text.isBlank()) return
        if (!isLikelyEnglishSubtitle(text)) return
        if (isDuplicate(text)) return

        pendingText = text
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = Runnable {
            pendingText?.let { t -> translateAndShow(t) }
        }
        handler.postDelayed(debounceRunnable!!, DEBOUNCE_DELAY)
    }

    override fun onInterrupt() {
        Log.d(TAG, "服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        translationManager.close()
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                sb.append(extractText(child))
                child.recycle()
            }
        }
        return sb.toString().trim()
    }

    private fun isLikelyEnglishSubtitle(text: String): Boolean {
        if (text.length < minTextLength || text.length > maxTextLength) return false
        if (!englishPattern.matcher(text).find()) return false
        if (text.startsWith("http") || text.startsWith("/")) return false
        if (text.count { it.isDigit() } > text.length * 0.5) return false
        val words = text.split(Regex("\s+"))
        if (words.size < 3) return false
        return true
    }

    private fun isDuplicate(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (recentTexts.contains(normalized)) return true
        recentTexts.addLast(normalized)
        if (recentTexts.size > 10) recentTexts.removeFirst()
        return false
    }

    private fun translateAndShow(englishText: String) {
        Log.d(TAG, "待翻译: $englishText")
        showLoading()
        translationManager.translate(englishText) { result ->
            handler.post {
                when (result) {
                    is TranslationResult.Success -> {
                        Log.d(TAG, "翻译结果: ${result.chinese}")
                        showSubtitle(result.chinese, englishText)
                    }
                    is TranslationResult.Error -> {
                        Log.e(TAG, "翻译失败: ${result.message}")
                        showSubtitle("[翻译失败]", englishText)
                    }
                    is TranslationResult.ModelNotReady -> {
                        showSubtitle("[正在下载翻译模型，请稍候...]", englishText)
                    }
                }
            }
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 60
        }
        overlayBinding = OverlaySubtitleBinding.inflate(LayoutInflater.from(this))
        overlayView = overlayBinding!!.root
        windowManager.addView(overlayView, params)
        overlayBinding?.subtitleContainer?.visibility = android.view.View.GONE
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
            overlayBinding = null
        }
    }

    private fun showLoading() {
        overlayBinding?.let { binding ->
            binding.chineseText.text = "翻译中..."
            binding.englishText.visibility = android.view.View.GONE
            binding.subtitleContainer.visibility = android.view.View.VISIBLE
        }
    }

    private fun showSubtitle(chinese: String, english: String) {
        overlayBinding?.let { binding ->
            binding.chineseText.text = chinese
            binding.englishText.text = english
            binding.englishText.visibility = android.view.View.VISIBLE
            binding.subtitleContainer.visibility = android.view.View.VISIBLE
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                binding.subtitleContainer.visibility = android.view.View.GONE
            }, 6000)
        }
    }
}
