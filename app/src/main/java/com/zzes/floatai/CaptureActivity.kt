package com.zzes.floatai

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.zzes.floatai.service.FloatWindowService

/**
 * 透明Activity，仅用于请求MediaProjection权限
 */
class CaptureActivity : ComponentActivity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "权限结果: ${result.resultCode}")

        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            FloatWindowService.instance?.onProjectionGranted(result.resultCode, result.data!!)
        } else {
            FloatWindowService.instance?.onProjectionCancelled()
        }

        // 延迟finish，让权限对话框动画完成
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 100)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        private const val TAG = "CaptureActivity"

        fun start(context: Context) {
            val intent = Intent(context, CaptureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // 使用 ActivityOptions 避免动画
            val options = ActivityOptions.makeBasic().apply {
                // 禁止过渡动画
            }
            context.startActivity(intent, options.toBundle())
        }
    }
}
