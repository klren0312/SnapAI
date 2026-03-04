package com.zzes.floatai

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zzes.floatai.service.FloatWindowService
import com.zzes.floatai.ui.theme.FloatAiTheme

class MainActivity : ComponentActivity() {

    private var floatWindowService: FloatWindowService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FloatWindowService.LocalBinder
            floatWindowService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            floatWindowService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            FloatAiTheme {
                MainScreen(
                    onStartFloatWindow = { checkAndStartFloatWindow() },
                    onStopFloatWindow = { stopFloatWindow() },
                    onRequestScreenshotPermission = { requestScreenshotPermission() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun checkAndStartFloatWindow() {
        when {
            !Settings.canDrawOverlays(this) -> {
                requestOverlayPermission()
            }
            !hasStoragePermission() -> {
                requestStoragePermission()
            }
            else -> {
                startFloatWindowService()
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkAndStartFloatWindow()
        } else {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true // Android 13+ 不需要存储权限进行截图
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkAndStartFloatWindow()
        } else {
            Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenshotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            FloatWindowService.instance?.onScreenshotPermissionGranted(
                result.resultCode, result.data!!
            )
            Toast.makeText(this, "截图权限已获取", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要截图权限才能使用AI识别功能", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestScreenshotPermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenshotPermissionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startFloatWindowService() {
        val intent = Intent(this, FloatWindowService::class.java)
        
        // 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // 绑定服务
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatWindow() {
        floatWindowService?.removeFloatWindow()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        val intent = Intent(this, FloatWindowService::class.java)
        stopService(intent)
        Toast.makeText(this, "悬浮窗已停止", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(
    onStartFloatWindow: () -> Unit,
    onStopFloatWindow: () -> Unit,
    onRequestScreenshotPermission: () -> Unit
) {
    val context = LocalContext.current
    var isFloatWindowRunning by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FloatAI 智能悬浮助手",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 版本号
            Text(
                text = "v1.8",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "点击按钮启动系统级悬浮框，可以在任何界面上使用 AI 识别功能",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (!isFloatWindowRunning) {
                Button(
                    onClick = {
                        onStartFloatWindow()
                        isFloatWindowRunning = true
                    }
                ) {
                    Text("启动悬浮窗")
                }
            } else {
                Button(
                    onClick = {
                        onStopFloatWindow()
                        isFloatWindowRunning = false
                    }
                ) {
                    Text("停止悬浮窗")
                }
            }

            Button(
                onClick = { onRequestScreenshotPermission() }
            ) {
                Text("请求截图权限 (首次使用必点)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "使用说明:\n1. 先点击请求截图权限并授权\n2. 再点击启动悬浮窗\n3. 在其他界面点击悬浮框截图按钮\n4. AI 将自动识别图片内容",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    FloatAiTheme {
        MainScreen(
            onStartFloatWindow = {},
            onStopFloatWindow = {},
            onRequestScreenshotPermission = {}
        )
    }
}
