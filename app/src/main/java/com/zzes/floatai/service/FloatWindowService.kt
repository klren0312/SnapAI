package com.zzes.floatai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zzes.floatai.MainActivity
import com.zzes.floatai.R
import com.zzes.floatai.ui.floatwindow.FloatWindowContent
import com.zzes.floatai.ui.floatwindow.FloatWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val binder = LocalBinder()
    private lateinit var windowManager: WindowManager
    private var floatView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val screenshotHelper by lazy { ScreenshotHelper(this) }
    private val aiService by lazy { AiService() }
    private val ocrService by lazy { OcrService() }

    var useOcrMode: Boolean = true
    var onAiResult: ((String) -> Unit)? = null

    // 保存的 MediaProjection 权限结果
    private var projectionResultCode: Int = 0
    private var projectionData: Intent? = null

    inner class LocalBinder : Binder() {
        fun getService(): FloatWindowService = this@FloatWindowService
    }

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var lastAiResult: String = ""

    fun updateAiResult(result: String) {
        lastAiResult = result
        onAiResult?.invoke(result)
    }

    fun getLastAiResult(): String = lastAiResult

    /**
     * 设置 MediaProjection 权限结果（由 MainActivity 调用）
     */
    fun setProjectionResult(resultCode: Int, data: Intent) {
        this.projectionResultCode = resultCode
        this.projectionData = data
        Log.d(TAG, "MediaProjection 权限已保存")
    }

    /**
     * 检查是否有有效的权限
     */
    fun hasProjectionPermission(): Boolean = projectionData != null

    override fun onCreate() {
        super.onCreate()
        instance = this
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isFloatWindowShowing()) {
            showFloatWindow()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        removeFloatWindow()
        ocrService.close()
        // 清理权限
        projectionData = null
        screenshotHelper.cleanup()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮窗运行"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FloatAI 悬浮窗")
            .setContentText("悬浮窗服务正在运行")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun showFloatWindow() {
        if (floatView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }
        layoutParams = params

        floatView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatWindowService)

            setContent {
                FloatWindowContent(
                    onClose = { removeFloatWindow() },
                    onScreenshot = {
                        performScreenshot()
                    },
                    windowManager = windowManager,
                    layoutParams = params,
                    floatView = this,
                    useOcrMode = useOcrMode,
                    onOcrModeChange = { newValue ->
                        useOcrMode = newValue
                        FloatWindowState.addDebugLog("OCR 模式: ${if (newValue) "开启" else "关闭"}")
                    }
                )
            }
        }

        windowManager.addView(floatView, params)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * 执行截图
     */
    private fun performScreenshot() {
        val data = projectionData
        if (data == null) {
            FloatWindowState.addDebugLog("没有截图权限，请重新启动悬浮窗")
            FloatWindowState.setError("没有截图权限，请重新启动悬浮窗")
            return
        }

        FloatWindowState.addDebugLog("点击截图按钮")
        FloatWindowState.setLoading(true)
        FloatWindowState.setStatusMessage("正在截图...")

        // 直接使用保存的权限进行截图
        onProjectionGranted(projectionResultCode, data)
    }

    /**
     * 权限授予成功后执行截图和AI分析
     */
    private fun onProjectionGranted(resultCode: Int, data: Intent) {
        Log.d(TAG, "onProjectionGranted: resultCode=$resultCode")
        FloatWindowState.addDebugLog("开始截图")

        serviceScope.launch(Dispatchers.IO) {
            var bitmap: Bitmap? = null

            try {
                // 隐藏悬浮窗
                withContext(Dispatchers.Main) {
                    removeFloatWindow()
                }

                Log.d(TAG, "开始截图...")
                FloatWindowState.addDebugLog("正在捕获屏幕...")

                val screenshotResult = screenshotHelper.captureScreenshot(resultCode, data)

                FloatWindowState.addDebugLog("截图结果: ${screenshotResult.isSuccess}")

                screenshotResult
                    .onSuccess { capturedBitmap ->
                        bitmap = capturedBitmap
                        Log.d(TAG, "截图成功: ${capturedBitmap.width}x${capturedBitmap.height}")
                        FloatWindowState.addDebugLog("截图成功: ${capturedBitmap.width}x${capturedBitmap.height}")
                        saveBitmapToGallery(capturedBitmap)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "截图失败: ${error.message}")
                        FloatWindowState.addDebugLog("截图失败: ${error.message}")
                        withContext(Dispatchers.Main) {
                            FloatWindowState.setError("截图失败: ${error.message}")
                            FloatWindowState.setLoading(false)
                            showFloatWindow()
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "截图过程异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    FloatWindowState.setError("截图异常: ${e.message}")
                    FloatWindowState.setLoading(false)
                    showFloatWindow()
                }
                return@launch
            }

            // 如果截图失败，不需要继续执行
            if (bitmap == null) {
                return@launch
            }

            // 确保截图成功后再执行AI分析
            bitmap?.let { capturedBitmap ->
                withContext(Dispatchers.Main) {
                    FloatWindowState.setScreenshot(capturedBitmap)
                    showFloatWindow()
                }

                // 调用 AI 分析
                Log.d(TAG, "开始 AI 分析...")
                FloatWindowState.setStatusMessage("正在连接 AI 服务...")

                try {
                    val aiResult = if (useOcrMode) {
                        Log.d(TAG, "使用 OCR 模式")
                        FloatWindowState.addDebugLog("使用 OCR 模式识别文字...")
                        FloatWindowState.setStatusMessage("正在识别图片文字...")

                        val ocrResult = ocrService.recognizeText(capturedBitmap)

                        ocrResult.fold(
                            onSuccess = { ocrText ->
                                Log.d(TAG, "OCR 识别成功，文字长度: ${ocrText.length}")
                                FloatWindowState.addDebugLog("OCR 识别成功，文字长度: ${ocrText.length}")

                                if (ocrText.isBlank()) {
                                    FloatWindowState.addDebugLog("OCR文字为空，切换图像模式")
                                    FloatWindowState.setStatusMessage("使用图像模式...")
                                    aiService.analyzeImage(capturedBitmap)
                                } else {
                                    FloatWindowState.addDebugLog("识别内容: ${ocrText.take(100)}...")
                                    FloatWindowState.setStatusMessage("正在分析文字内容...")
                                    aiService.analyzeText(ocrText)
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "OCR 识别失败: ${error.message}")
                                FloatWindowState.addDebugLog("OCR 失败，切换图像模式: ${error.message}")
                                FloatWindowState.setStatusMessage("OCR 失败，使用图像模式...")
                                aiService.analyzeImage(capturedBitmap)
                            }
                        )
                    } else {
                        Log.d(TAG, "使用图像模式")
                        FloatWindowState.addDebugLog("使用图像模式")
                        aiService.analyzeImage(capturedBitmap)
                    }

                    withContext(Dispatchers.Main) {
                        aiResult.onSuccess { response ->
                            Log.d(TAG, "AI 分析成功")
                            FloatWindowState.updateResponse(response)
                            updateAiResult(response)
                            Toast.makeText(this@FloatWindowService, "识别完成", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Log.e(TAG, "AI 分析失败: ${error.message}")
                            FloatWindowState.setError("识别失败: ${error.message}")
                            Toast.makeText(this@FloatWindowService, "识别失败: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "AI 分析异常: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        FloatWindowState.setError("AI 分析异常: ${e.message}")
                    }
                }
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            val filename = "SnapAI_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }

                Log.d(TAG, "截图已保存到相册: $filename")
                FloatWindowState.addDebugLog("截图已保存到相册")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存截图失败: ${e.message}")
        }
    }

    fun removeFloatWindow() {
        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun isFloatWindowShowing(): Boolean = floatView != null

    fun updateFloatWindowPosition(x: Int, y: Int) {
        layoutParams?.let { params ->
            params.x = x
            params.y = y
            floatView?.let {
                windowManager.updateViewLayout(it, params)
            }
        }
    }

    companion object {
        private const val TAG = "FloatWindowService"
        private const val CHANNEL_ID = "float_window_channel"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var instance: FloatWindowService? = null
            private set
    }
}
