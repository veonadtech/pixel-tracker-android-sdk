package com.example.pixeltrackerdemo

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.example.pixeltracker.PixelTracker
import com.example.pixeltrackerdemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pixelTracker: PixelTracker
    private var totalAppearances = 0
    private var isDescriptionExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupPixelTracker()
        setupImages()
    }

    private fun setupUI() {
        binding.titleTextView.text = "Pixel Tracker Demo"
        binding.descriptionTextView.text = """
            How it works:
            1. Scroll down 1.5 screens to see the first pixel
            2. Continue scrolling to hide pixel behind second image
            3. Scroll up/down to see pixel appear/disappear
            
            Check Logcat with tag "PixelTrackerDemo" for events
        """.trimIndent()

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
        updateShowCount()

        // Кнопка показа статистики
        binding.statsButton.setOnClickListener {
            val stats = pixelTracker.getStats()
            val appearances = stats["totalAppearances"] as? Int ?: 0
            val isVisible = stats["isCurrentlyVisible"] as? Boolean ?: false

            val statsText = """
                Total Appearances: $appearances
                Currently Visible: ${if (isVisible) "Yes" else "No"}
                Pixel ID: ${stats["pixelId"]}
                Library Version: ${if (stats.containsKey("libraryVersion")) stats["libraryVersion"] else "unknown"}
            """.trimIndent()

            Toast.makeText(
                this,
                statsText,
                Toast.LENGTH_LONG
            ).show()

            // Краткая информация в статусе
            binding.pixelStatusText.text = "Stats: $appearances appearances"
        }
    }

    private fun setupPixelTracker() {
        // Создаем пиксель программно
        pixelTracker = PixelTracker.create(
            context = this,
            pixelId = "demo_pixel_${System.currentTimeMillis()}"
        ).apply {
            isDebugMode = true
            visibilityThreshold = 1

            // Кастомный логгер для демо
            setCustomLogger(object : PixelTracker.PixelLogger {
                override fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
                    Log.d("PixelTrackerDemo", "🎯 Pixel APPEARED! ID: $pixelId")

                    totalAppearances++

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "🎯 Pixel is VISIBLE!\nTotal appearances: $totalAppearances",
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.pixelStatusText.text = "✅ PIXEL VISIBLE"
                        binding.pixelStatusText.setTextColor(getColor(android.R.color.holo_green_dark))

                        // Обновляем счетчик показов
                        updateShowCount()
                    }
                }

                override fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
                    Log.d("PixelTrackerDemo", "👻 Pixel DISAPPEARED! ID: $pixelId")

                    runOnUiThread {
                        binding.pixelStatusText.text = "❌ PIXEL HIDDEN"
                        binding.pixelStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
                    }
                }

                override fun logError(pixelId: String, error: String, timestamp: String) {
                    Log.e("PixelTrackerDemo", "Error: $error")
                }
            })
        }

        // Добавляем пиксель в контейнер
        val layoutParams = android.widget.FrameLayout.LayoutParams(40, 40).apply {
            // Размещаем пиксель на 1.5 экрана ниже первой картинки
            val screenHeight = resources.displayMetrics.heightPixels
            topMargin = (screenHeight * 1.5).toInt()
            leftMargin = resources.displayMetrics.widthPixels / 2 - 20 // Центрируем по горизонтали
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        pixelTracker.setBackgroundColor(Color.RED)
        binding.imagesContainer.addView(pixelTracker, layoutParams)

        // Запускаем отслеживание
        pixelTracker.startTracking()

        binding.pixelStatusText.text = "🟡 TRACKING STARTED"
        binding.pixelStatusText.setTextColor(getColor(android.R.color.holo_orange_dark))
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
            setImageResource(R.drawable.demo_image_1) // Добавьте картинку в res/drawable
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
            setImageResource(R.drawable.demo_image_2) // Добавьте вторую картинку
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Second demo image"
        }

        // Добавляем картинки в контейнер
        binding.imagesContainer.addView(firstImage)
        binding.imagesContainer.addView(secondImage)

        // Устанавливаем высоту контейнера, чтобы можно было скроллить
        val containerHeight = resources.displayMetrics.heightPixels * 3
        binding.imagesContainer.layoutParams.height = containerHeight

        binding.bottomText.text = "Pixel location: ${(resources.displayMetrics.heightPixels * 1.5).toInt()}px from top"
    }


    private fun updateShowCount() {
        binding.showCountText.text = "Total Appearances: $totalAppearances"
    }

    private fun hasImageResource(resourceId: Int): Boolean {
        return try {
            resources.getDrawable(resourceId, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onResume() {
        super.onResume()
        if (::pixelTracker.isInitialized) {
            pixelTracker.startTracking()
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