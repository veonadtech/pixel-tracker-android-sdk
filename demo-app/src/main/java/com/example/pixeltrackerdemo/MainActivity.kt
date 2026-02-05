package com.example.pixeltrackerdemo

import android.graphics.Color
import android.os.Bundle
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
    private var scrollCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupPixelTracker()
        setupImages()
        setupScrollListener()
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

        binding.scrollCounterText.text = "Scrolls: $scrollCounter"

        // Кнопка показа статистики
        binding.statsButton.setOnClickListener {
            val stats = pixelTracker.getStats()
            val statsText = stats.entries.joinToString("\n") { "${it.key}: ${it.value}" }

            binding.statsTextView.text = "Current Stats:\n$statsText"
            binding.statsTextView.visibility = android.view.View.VISIBLE
        }
    }

    private fun setupPixelTracker() {
        // Создаем пиксель программно
        pixelTracker = PixelTracker.create(
            context = this,
            pixelId = "demo_pixel_${System.currentTimeMillis()}"
        ).apply {
           // debounceTime = 200L
            visibilityThreshold = 1
            isDebugMode = true

            // Кастомный логгер для демо
            setCustomLogger(object : PixelTracker.PixelLogger {
                override fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
                    Log.d("PixelTrackerDemo", "🎯 Pixel APPEARED! ID: $pixelId")

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "🎯 Pixel is VISIBLE!\nTotal appearances: ${metadata["total_appearances"]}",
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.pixelStatusText.text = "✅ PIXEL VISIBLE"
                        binding.pixelStatusText.setTextColor(getColor(android.R.color.holo_green_dark))

                        // Показать позицию пикселя
                        val position = metadata["position"] as? String ?: "[?,?]"
                        binding.pixelPositionText.text = "Position: $position"
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
        // Размещаем пиксель после первой картинки, но перед второй
        // Это создаст эффект: пиксель появляется между двумя картинками
        val layoutParams = android.widget.FrameLayout.LayoutParams(40, 40).apply {
            // Размещаем пиксель на 1.5 экрана ниже первой картинки
            // Screen height * 1.5 = нужно проскроллить 1.5 экрана
            val screenHeight = resources.displayMetrics.heightPixels
            topMargin = (screenHeight * 1.5).toInt()
            leftMargin = resources.displayMetrics.widthPixels / 2
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        pixelTracker.setBackgroundColor(Color.RED)
      //  pixelTracker.visibility = android.view.View.VISIBLE
        binding.imagesContainer.addView(pixelTracker, layoutParams)

        // Запускаем отслеживание
        pixelTracker.startTracking()

        binding.pixelStatusText.text = "🟡 PIXEL TRACKING STARTED"
        binding.pixelPositionText.text = "Waiting for pixel visibility..."
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
        // 3 экрана высоты: 1 экран + 0.5 отступа + 1.5 для второй картинки
        val containerHeight = resources.displayMetrics.heightPixels * 3
        binding.imagesContainer.layoutParams.height = containerHeight

        binding.totalHeightText.text = "Total scroll height: ${containerHeight}px"
    }

    private fun setupScrollListener() {
        binding.mainScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                // Считаем скроллы
                if (scrollY > oldScrollY) {
                    scrollCounter++
                    binding.scrollCounterText.text = "Scrolls down: $scrollCounter"

                    // Подсказки при приближении к пикселю
                    val screenHeight = resources.displayMetrics.heightPixels
                    val pixelPosition = (screenHeight * 1.5).toInt()

                    if (scrollY > pixelPosition - screenHeight && scrollY < pixelPosition) {
                        binding.hintTextView.text = "⏬ Almost at the pixel! Keep scrolling!"
                        binding.hintTextView.visibility = android.view.View.VISIBLE
                    } else if (scrollY >= pixelPosition && scrollY < pixelPosition + 100) {
                        binding.hintTextView.text = "🎯 You should see the pixel NOW!"
                    } else if (scrollY > pixelPosition + 100) {
                        binding.hintTextView.text = "⬆️ Scroll up to see pixel again"
                    }
                } else if (scrollY < oldScrollY) {
                    binding.hintTextView.text = "Scrolling up..."
                }

                // Показываем текущую позицию скролла
                binding.currentScrollText.text = "Scroll position: ${scrollY}px"

                // Показываем процент скролла до пикселя
                val screenHeight = resources.displayMetrics.heightPixels
                val pixelPosition = (screenHeight * 1.5).toInt()
                if (scrollY < pixelPosition) {
                    val progress = (scrollY.toFloat() / pixelPosition * 100).toInt()
                    binding.progressText.text = "Progress to pixel: $progress%"
                } else {
                    val progress = ((scrollY - pixelPosition).toFloat() / screenHeight * 100).toInt()
                    binding.progressText.text = "Past pixel by: $progress% of screen"
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        pixelTracker.startTracking()
    }

    override fun onPause() {
        super.onPause()
        pixelTracker.stopTracking()
    }

    override fun onDestroy() {
        pixelTracker.stopTracking()
        super.onDestroy()
    }
}