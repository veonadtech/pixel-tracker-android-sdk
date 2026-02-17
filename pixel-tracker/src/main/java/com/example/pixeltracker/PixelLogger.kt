package com.example.pixeltracker

/**
 * Интерфейс для логирования событий пикселя.
 * Реализации могут логировать в консоль, отправлять на сервер, или комбинировать.
 */
interface PixelLogger {

    /**
     * Логирует появление пикселя на экране
     * @param pixelId ID пикселя
     * @param timestamp Временная метка события
     * @param metadata Дополнительные метаданные
     */
    fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>)

    /**
     * Логирует исчезновение пикселя с экрана
     * @param pixelId ID пикселя
     * @param timestamp Временная метка события
     * @param metadata Дополнительные метаданные
     */
    fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>)

    /**
     * Логирует повторный показ пикселя (refresh)
     * @param pixelId ID пикселя
     * @param timestamp Временная метка события
     * @param metadata Дополнительные метаданные
     */
    fun logRefresh(pixelId: String, timestamp: String, metadata: Map<String, Any>)

    /**
     * Логирует ошибку, связанную с пикселем
     * @param pixelId ID пикселя
     * @param error Сообщение об ошибке
     * @param timestamp Временная метка события
     */
    fun logError(pixelId: String, error: String, timestamp: String)
}