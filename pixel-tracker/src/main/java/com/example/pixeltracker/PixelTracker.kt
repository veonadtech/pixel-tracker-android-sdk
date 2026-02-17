package com.example.pixeltracker

import android.content.Context
import android.util.Log
import com.example.pixeltracker.network.PixelNetworkManager

/**
 * Фасад для инициализации и создания PixelTrackerView.
 * Инициализация сети должна быть вызвана один раз в Application.
 */
object PixelTracker {

    // Внутренний сетевой менеджер
    private var networkManager: PixelNetworkManager? = null

    // Глобальный debug режим для всей библиотеки
    private var isDebugMode: Boolean = false

    /**
     * Инициализация сетевого слоя (вызывается один раз в Application)
     * @param baseUrl URL сервера для отправки событий
     * @param isDebugMode Глобальный debug режим для всех пикселей
     */
    fun initialize(baseUrl: String, isDebugMode: Boolean = false) {
        if (networkManager != null) {
            Log.d("PixelTracker", "Already initialized")
            return
        }

        this.isDebugMode = isDebugMode
        networkManager = PixelNetworkManager(baseUrl, isDebugMode)
        Log.d("PixelTracker", "Initialized with URL: $baseUrl, debugMode: $isDebugMode")
    }

    /**
     * Создание View для отслеживания
     * @param isDebugMode Переопределяет глобальный debug режим для конкретного пикселя (опционально)
     */
    fun createView(
        context: Context,
        pixelId: String,
        refreshTimeSeconds: Long = 0L,
        pixelSize: Int = 1
    ): PixelTrackerView {
        // Проверяем инициализацию
        if (networkManager == null) {
            Log.w("PixelTracker", "Network manager not initialized. Events will not be sent.")
        }

        return PixelTrackerView(context, pixelId).apply {
            this.refreshTime = refreshTimeSeconds * 1000
            this.pixelSize = pixelSize
            this.isDebugMode = this@PixelTracker.isDebugMode
        }
    }

    /**
     * Для тестирования или внутреннего использования
     */
    internal fun getNetworkManager(): PixelNetworkManager? = networkManager

    /**
     * Завершает работу сетевого слоя
     */
    fun shutdown() {
        networkManager?.shutdown()
        networkManager = null
        Log.d("PixelTracker", "Shutdown complete")
    }
}