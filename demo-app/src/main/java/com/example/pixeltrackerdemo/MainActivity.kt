package com.example.pixeltrackerdemo

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pixeltracker.PixelTracker
import com.example.pixeltrackerdemo.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pixelTracker: PixelTracker
    private var isDescriptionExpanded = false
    private var refreshTimeSeconds: Long = 5L // По умолчанию 5 секунд
    private val pixelSize: Int = 40

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupPixelTracker()
        setupImages()
        setupRefreshControl()
    }

    private fun setupUI() {
        binding.titleTextView.text = "Pixel Tracker Demo"
        updateDescriptionText()

        // Сворачиваемое/разворачиваемое описание
        binding.descriptionTextView.setOnClickListener {
            isDescriptionExpanded = !isDescriptionExpanded

            if (isDescriptionExpanded) {
                // Разворачиваем
                binding.descriptionTextView.maxLines = Integer.MAX_VALUE
                binding.descriptionTextView.ellipsize = null
            } else {
                // Сворачиваем
                binding.descriptionTextView.maxLines = 3
                binding.descriptionTextView.ellipsize = TextUtils.TruncateAt.END
            }
        }

        // Обновляем текст с текущим refreshTime
        updateRefreshTimeText()
    }

    private fun updateDescriptionText() {
        val refreshInfo = if (refreshTimeSeconds > 0) {
            "2. Keep pixel on screen for $refreshTimeSeconds seconds to count another view"
        } else {
            "2. Refresh disabled - pixel counts only on appearance"
        }

        binding.descriptionTextView.text = """
            How it works:
            1. Scroll down 1.5 screens to see the red pixel
            $refreshInfo
            3. Scroll up to hide pixel, then down again to restart counting
        """.trimIndent()
    }

    private fun setupRefreshControl() {
        // Кнопка уменьшения времени refresh
        binding.btnDecreaseRefresh.setOnClickListener {
            if (refreshTimeSeconds > 0) { // Не позволяем уменьшить меньше 0
                refreshTimeSeconds--
                updateRefreshTimeText()
                updateDescriptionText()
                updatePixelTrackerRefreshTime()
            }
        }

        // Кнопка увеличения времени refresh
        binding.btnIncreaseRefresh.setOnClickListener {
            refreshTimeSeconds++
            updateRefreshTimeText()
            updateDescriptionText()
            updatePixelTrackerRefreshTime()
        }
    }

    private fun updateRefreshTimeText() {
        // Показываем "Off" если refreshTime = 0
        val displayText = if (refreshTimeSeconds == 0L) "Off" else "${refreshTimeSeconds}s"
        binding.tvRefreshTime.text = displayText

        // Меняем цвет кнопки уменьшения, если достигли минимума
        if (refreshTimeSeconds == 0L) {
            binding.btnDecreaseRefresh.setBackgroundColor(Color.parseColor("#CCCCCC"))
            binding.btnDecreaseRefresh.isEnabled = false
        } else {
            binding.btnDecreaseRefresh.setBackgroundColor(Color.parseColor("#4CAF50"))
            binding.btnDecreaseRefresh.isEnabled = true
        }
    }

    private fun updatePixelTrackerRefreshTime() {
        if (::pixelTracker.isInitialized) {
            pixelTracker.refreshTime = TimeUnit.SECONDS.toMillis(refreshTimeSeconds)
            Log.d("PixelTrackerDemo", "Refresh time updated to ${refreshTimeSeconds}s (${pixelTracker.refreshTime}ms)")
        }
    }

    private fun setupPixelTracker() {
        // Создаем пиксель программно
        pixelTracker = PixelTracker.create(
            context = this,
            pixelId = "demo_pixel_${System.currentTimeMillis()}",
            refreshTimeSeconds = refreshTimeSeconds
        ).apply {
            isDebugMode = true
            visibilityThreshold = 1

            // Кастомный логгер для демо
            setCustomLogger(object : PixelTracker.PixelLogger {
                override fun logAppearance(
                    pixelId: String,
                    timestamp: String,
                    metadata: Map<String, Any>
                ) {
                    runOnUiThread {
                        val message = if (refreshTimeSeconds > 0) {
                            "🎯 Pixel is visible (refresh every ${refreshTimeSeconds}s)"
                        } else {
                            "🎯 Pixel is visible"
                        }

                        Toast.makeText(
                            this@MainActivity,
                            message,
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.pixelStatusText.text = "✅ VISIBLE"
                        binding.pixelStatusText.setTextColor(getColor(android.R.color.holo_green_dark))

                        // Обновляем счетчик показов
                        updateShowCount()
                    }
                }

                override fun logDisappearance(
                    pixelId: String,
                    timestamp: String,
                    metadata: Map<String, Any>
                ) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "👻 Pixel hidden",
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.pixelStatusText.text = "❌ HIDDEN"
                        binding.pixelStatusText.setTextColor(getColor(android.R.color.holo_red_dark))

                        updateShowCount()
                    }
                }

                override fun logRefresh(
                    pixelId: String,
                    timestamp: String,
                    metadata: Map<String, Any>
                ) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "🔄 New view counted (refresh)",
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.pixelStatusText.text = "🔄 NEW VIEW"
                        binding.pixelStatusText.setTextColor(Color.parseColor("#FF9800"))

                        // Обновляем счетчик показов
                        updateShowCount()
                    }
                }

                override fun logError(pixelId: String, error: String, timestamp: String) {
                    Log.e("PixelTrackerDemo", "Error: $error")
                }
            })
        }

        // Добавляем пиксель в контейнер
        val layoutParams = android.widget.FrameLayout.LayoutParams(pixelSize, pixelSize).apply {
            // Размещаем пиксель на 1.5 экрана ниже первой картинки
            val screenHeight = resources.displayMetrics.heightPixels
            topMargin = (screenHeight * 1.5).toInt()
            leftMargin = resources.displayMetrics.widthPixels / 2 - pixelSize // Центрируем по горизонтали
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        pixelTracker.setBackgroundColor(Color.RED)
        binding.imagesContainer.addView(pixelTracker, layoutParams)

        // Запускаем отслеживание
        pixelTracker.startTracking()

        binding.pixelStatusText.text = "🟡 TRACKING"
        binding.pixelStatusText.setTextColor(getColor(android.R.color.holo_orange_dark))

        // Устанавливаем начальную подсказку
        binding.hintTextView.text = "Scroll down to find the red pixel"
    }

    private fun setupImages() {
        // Первая картинка (занимает весь экран)
        val firstImage = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.heightPixels
            ).apply {
                topMargin = 0
            }
            setImageResource(R.drawable.demo_image_1)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "First demo image"
        }

        // Вторая картинка (размещаем ее после пикселя, чтобы пиксель можно было скрыть)
        val secondImage = ImageView(this).apply {
            val screenHeight = resources.displayMetrics.heightPixels
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                screenHeight
            ).apply {
                // Размещаем на 2 экрана ниже (1.5 экрана + 0.5 экрана для плавности)
                topMargin = (screenHeight * 2).toInt()
            }
            setImageResource(R.drawable.demo_image_2)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Second demo image"
        }

        // Добавляем картинки в контейнер
        binding.imagesContainer.addView(firstImage)
        binding.imagesContainer.addView(secondImage)

        // Устанавливаем высоту контейнера, чтобы можно было скроллить
        val containerHeight = resources.displayMetrics.heightPixels * 3
        binding.imagesContainer.layoutParams.height = containerHeight

        binding.bottomText.text =
            "Pixel location: ${(resources.displayMetrics.heightPixels * 1.5).toInt()}px from top"
    }

    private fun updateShowCount() {
        if (::pixelTracker.isInitialized) {
            val stats = pixelTracker.getStats()
            val appearances = stats["totalAppearances"] as? Int ?: 0
            val nextRefreshIn = stats["nextRefreshIn"] as? Long ?: 0
            val isVisible = stats["isCurrentlyVisible"] as? Boolean ?: false
            val refreshEnabled = stats["refreshEnabled"] as? Boolean ?: false

            // Обновляем основной счетчик
            binding.showCountText.text = "Total Appearances: $appearances"

            // Показываем время до следующего показа, если пиксель виден и refresh включен
            if (isVisible && refreshEnabled && nextRefreshIn > 0) {
                val secondsToNext = nextRefreshIn / 1000
                binding.hintTextView.text = "Next view in: ${secondsToNext}s"
            } else if (refreshEnabled) {
                binding.hintTextView.text = "Refresh: ${refreshTimeSeconds}s"
            } else {
                binding.hintTextView.text = "Refresh: Off"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::pixelTracker.isInitialized) {
            pixelTracker.startTracking()
            updateShowCount()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::pixelTracker.isInitialized) {
            pixelTracker.stopTracking()
        }
    }

    override fun onDestroy() {
        if (::pixelTracker.isInitialized) {
            pixelTracker.stopTracking()
        }
        super.onDestroy()
    }
}