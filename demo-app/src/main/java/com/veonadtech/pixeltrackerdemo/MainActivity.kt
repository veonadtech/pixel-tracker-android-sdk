package com.veonadtech.pixeltrackerdemo

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.veonadtech.pixeltracker.PixelTracker
import com.veonadtech.pixeltracker.api.PixelConfig
import com.veonadtech.pixeltracker.api.PixelEventListener
import com.veonadtech.pixeltracker.api.PixelHandle
import com.veonadtech.pixeltrackerdemo.databinding.ActivityMainBinding
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pixelTracker: PixelHandle
    private var isDescriptionExpanded = false
    private var refreshTimeSeconds: Long = 5L
    private val visibilityCheckInterval: Long = 3L
    private val debugPixelSize: Int = 40

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
        binding.titleTextView.text = getString(R.string.app_name)
        updateDescriptionText()

        binding.descriptionTextView.setOnClickListener {
            isDescriptionExpanded = !isDescriptionExpanded

            if (isDescriptionExpanded) {
                binding.descriptionTextView.maxLines = Integer.MAX_VALUE
                binding.descriptionTextView.ellipsize = null
            } else {
                binding.descriptionTextView.maxLines = 3
                binding.descriptionTextView.ellipsize = TextUtils.TruncateAt.END
            }
        }

        updateRefreshTimeText()
    }

    private fun updateDescriptionText() {
        val refreshInfo = if (refreshTimeSeconds > 0) {
            getString(R.string.refresh_instruction, refreshTimeSeconds)
        } else {
            getString(R.string.refresh_disabled)
        }

        val sizeInfo = getString(R.string.pixel_size_info, debugPixelSize, debugPixelSize)

        binding.descriptionTextView.text = getString(
            R.string.description_template,
            refreshInfo,
            sizeInfo
        ).trimIndent()
    }

    private fun setupRefreshControl() {
        binding.btnDecreaseRefresh.setOnClickListener {
            if (refreshTimeSeconds > 0) {
                refreshTimeSeconds--
                updateRefreshTimeText()
                updateDescriptionText()
                updatePixelTrackerRefreshTime()
            }
        }

        binding.btnIncreaseRefresh.setOnClickListener {
            refreshTimeSeconds++
            updateRefreshTimeText()
            updateDescriptionText()
            updatePixelTrackerRefreshTime()
        }
    }

    private fun updateRefreshTimeText() {
        val displayText = if (refreshTimeSeconds == 0L) "Off" else "${refreshTimeSeconds}s"
        binding.tvRefreshTime.text = displayText

        if (refreshTimeSeconds == 0L) {
            binding.btnDecreaseRefresh.setBackgroundColor("#CCCCCC".toColorInt())
            binding.btnDecreaseRefresh.isEnabled = false
        } else {
            binding.btnDecreaseRefresh.setBackgroundColor("#4CAF50".toColorInt())
            binding.btnDecreaseRefresh.isEnabled = true
        }
    }

    private fun updatePixelTrackerRefreshTime() {
        if (::pixelTracker.isInitialized) {
            pixelTracker.updateRefreshTime(refreshTimeSeconds)
            Log.d("PixelTrackerDemo", "Refresh time updated to ${refreshTimeSeconds}s")
        }
    }

    private fun setupPixelTracker() {

        val pixelTrackerContainer = FrameLayout(this)

        val layoutParams = FrameLayout.LayoutParams(
            debugPixelSize,
            debugPixelSize
        ).apply {
            val screenHeight = resources.displayMetrics.heightPixels
            topMargin = (screenHeight * 1.5).toInt()
            leftMargin = resources.displayMetrics.widthPixels / 2 - debugPixelSize / 2
            gravity = Gravity.TOP or Gravity.START
        }

        binding.imagesContainer.addView(pixelTrackerContainer, layoutParams)

        pixelTracker = PixelTracker.attach(
            context = this,
            container = pixelTrackerContainer,
            config = PixelConfig(
                pixelId = "demo_pixel_${System.currentTimeMillis()}",
                refreshTimeSeconds = refreshTimeSeconds,
                pixelSize = debugPixelSize,
                visibilityThreshold = 30,
                color = Color.RED
            )
        )

        pixelTracker.setEventListener(object : PixelEventListener {

            override fun onAppearance(pixelId: String, timestamp: String) {
                runOnUiThread {
                    val message = if (refreshTimeSeconds > 0) {
                        getString(R.string.pixel_visible_refresh, refreshTimeSeconds)
                    } else {
                        getString(R.string.pixel_visible)
                    }

                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    binding.pixelStatusText.text = getString(R.string.visible)
                    binding.pixelStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))

                    updateShowCount()
                }
            }

            override fun onDisappearance(pixelId: String, timestamp: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "👻 Pixel hidden", Toast.LENGTH_SHORT).show()

                    binding.pixelStatusText.text = getString(R.string.hidden)
                    binding.pixelStatusText.setTextColor(ContextCompat.getColor(this@MainActivity,android.R.color.holo_red_dark))

                    updateShowCount()
                }
            }

            override fun onRefresh(pixelId: String, timestamp: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "🔄 New view counted (refresh)", Toast.LENGTH_SHORT).show()

                    binding.pixelStatusText.text = getString(R.string.new_view)
                    binding.pixelStatusText.setTextColor("#FF9800".toColorInt())

                    updateShowCount()
                }
            }

            override fun onError(pixelId: String, error: String, timestamp: String) {
                Log.e("PixelTrackerDemo", "Error: $error")
            }
        })

        pixelTracker.setVisibilityCheckInterval(visibilityCheckInterval)

        pixelTracker.start()

        binding.pixelStatusText.text = getString(R.string.tracking)
        binding.pixelStatusText.setTextColor(ContextCompat.getColor(this@MainActivity,android.R.color.holo_orange_dark))
        binding.hintTextView.text = getString(R.string.scroll_hint)
    }

    private fun setupImages() {
        val firstImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.heightPixels
            )
            setImageResource(R.drawable.demo_image_1)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val secondImage = ImageView(this).apply {
            val screenHeight = resources.displayMetrics.heightPixels
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                screenHeight
            ).apply {
                topMargin = (screenHeight * 2)
            }
            setImageResource(R.drawable.demo_image_2)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        binding.imagesContainer.addView(firstImage)
        binding.imagesContainer.addView(secondImage)

        binding.imagesContainer.layoutParams.height =
            resources.displayMetrics.heightPixels * 3
    }

    private fun updateShowCount() {
        if (::pixelTracker.isInitialized) {
            val stats = pixelTracker.getStats()

            binding.showCountText.text =
                getString(R.string.total_appearances, stats.totalAppearances.get())

            when {
                stats.isCurrentlyVisible && stats.refreshEnabled && stats.nextRefreshInMs > 0 -> {
                    val secondsToNext = stats.nextRefreshInMs / 1000 + 1
                    binding.hintTextView.text = getString(R.string.next_view_in, secondsToNext)
                }
                stats.refreshEnabled -> {
                    binding.hintTextView.text = getString(R.string.refresh_seconds, refreshTimeSeconds)
                }
                else -> {
                    binding.hintTextView.text = getString(R.string.refresh_off)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::pixelTracker.isInitialized) {
            pixelTracker.start()
            updateShowCount()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::pixelTracker.isInitialized) {
            pixelTracker.stop()
        }
    }

    override fun onDestroy() {
        if (::pixelTracker.isInitialized) {
            pixelTracker.destroy()
        }
        super.onDestroy()
    }
}
