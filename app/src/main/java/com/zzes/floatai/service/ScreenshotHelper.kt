package com.zzes.floatai.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenshotHelper(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Suppress("DEPRECATION")
    suspend fun captureScreenshot(resultCode: Int, data: Intent): Result<Bitmap> = withContext(Dispatchers.IO) {
        Log.d("ScreenshotHelper", "开始截图 - resultCode: $resultCode")
        try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                val error = "MediaProjection 为 null，请检查屏幕录制权限"
                Log.e("ScreenshotHelper", error)
                return@withContext Result.failure(Exception(error))
            }
            
            Log.d("ScreenshotHelper", "MediaProjection 创建成功")

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 获取屏幕尺寸 (API 30+)
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            val density = metrics.densityDpi
            
            Log.d("ScreenshotHelper", "屏幕尺寸: ${width}x${height}, density: $density")

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            Log.d("ScreenshotHelper", "ImageReader 创建成功")

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "Screenshot",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                Handler(Looper.getMainLooper())
            )
            
            if (virtualDisplay == null) {
                val error = "VirtualDisplay 创建失败，可能是屏幕录制权限被拒绝"
                Log.e("ScreenshotHelper", error)
                cleanup()
                return@withContext Result.failure(Exception(error))
            }
            
            Log.d("ScreenshotHelper", "VirtualDisplay 创建成功，等待截图...")

            // 使用 suspendCancellableCoroutine 等待异步结果
            suspendCancellableCoroutine<Result<Bitmap>> { continuation ->
                // 等待一帧
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d("ScreenshotHelper", "尝试获取图片...")
                        val image = imageReader?.acquireLatestImage()
                        if (image == null) {
                            val error = "acquireLatestImage 返回 null，截图失败"
                            Log.e("ScreenshotHelper", error)
                            cleanup()
                            continuation.resume(Result.failure(Exception(error)))
                            return@postDelayed
                        }
                        
                        Log.d("ScreenshotHelper", "获取到图片，开始处理...")
                        val bitmap = processImage(image, width, height)
                        image.close()
                        
                        cleanup()
                        if (bitmap != null) {
                            Log.d("ScreenshotHelper", "截图处理完成，bitmap: ${bitmap.width}x${bitmap.height}")
                            continuation.resume(Result.success(bitmap))
                        } else {
                            val error = "processImage 返回 null"
                            Log.e("ScreenshotHelper", error)
                            continuation.resume(Result.failure(Exception(error)))
                        }
                    } catch (e: Exception) {
                        Log.e("ScreenshotHelper", "处理截图时异常: ${e.message}", e)
                        cleanup()
                        continuation.resume(Result.failure(Exception("处理截图异常: ${e.message}", e)))
                    }
                }, 500)

                continuation.invokeOnCancellation {
                    Log.d("ScreenshotHelper", "截图被取消")
                    cleanup()
                }
            }

        } catch (e: Exception) {
            val error = "截图过程异常: ${e.message}"
            Log.e("ScreenshotHelper", error, e)
            cleanup()
            Result.failure(Exception(error, e))
        }
    }

    private fun processImage(image: Image, width: Int, height: Int): Bitmap? {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // 裁剪到实际屏幕尺寸
        return if (bitmap.width > width) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } else {
            bitmap
        }
    }

    fun cleanup() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    companion object {
        fun bitmapToByteArray(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 85): ByteArray {
            val stream = ByteArrayOutputStream()
            bitmap.compress(format, quality, stream)
            return stream.toByteArray()
        }
    }
}
