package com.example.captiontranslator

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.captiontranslator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateStatus()

        binding.btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请找到「实时字幕翻译」并开启", Toast.LENGTH_LONG).show()
        }

        binding.btnTestTranslate.setOnClickListener {
            if (isServiceEnabled()) {
                Toast.makeText(this, "服务已运行！请打开任意英文视频测试", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "设置功能开发中", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        if (isServiceEnabled()) {
            binding.tvStatus.text = "状态：服务已开启"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.tvHint.text = "请打开任意带英文的视频（YouTube、Netflix 等），字幕将自动翻译为中文显示在屏幕底部。\n\n注意：翻译会有 2-4 秒延迟，这是技术限制。"
        } else {
            binding.tvStatus.text = "状态：服务未开启"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            binding.tvHint.text = "请点击「开启无障碍服务」按钮，在系统设置中找到「实时字幕翻译」并开启。\n\n重要说明：\n本应用通过 Android 无障碍功能捕获屏幕上的英文文本并翻译。由于系统限制，无法直接读取视频内置字幕，只能捕获部分应用暴露的文本内容。"
        }
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
