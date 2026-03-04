package com.zzes.floatai.ui.floatwindow

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 悬浮窗状态管理单例对象
 * 用于在服务和 UI 之间共享状态
 */
object FloatWindowState {
    private var _aiResponse by mutableStateOf("")
    private var _isLoading by mutableStateOf(false)
    private var _error by mutableStateOf<String?>(null)
    private var _screenshot by mutableStateOf<Bitmap?>(null)
    private var _showScreenshot by mutableStateOf(false)
    private var _statusMessage by mutableStateOf("")
    private var _debugLog by mutableStateOf("")
    
    // 使用 getter 暴露，避免与 setter 方法冲突
    val aiResponse: String get() = _aiResponse
    val isLoading: Boolean get() = _isLoading
    val error: String? get() = _error
    val screenshot: Bitmap? get() = _screenshot
    val showScreenshot: Boolean get() = _showScreenshot
    val statusMessage: String get() = _statusMessage
    val debugLog: String get() = _debugLog
    
    fun updateResponse(response: String) {
        _aiResponse = response
        _isLoading = false
        _error = null
    }
    
    fun setLoading(loading: Boolean) {
        _isLoading = loading
        if (loading) {
            _error = null
            _aiResponse = ""
            _statusMessage = "正在发送请求..."
            // 不清空 debugLog，保留调试信息
        }
    }
    
    fun setStatusMessage(message: String) {
        _statusMessage = message
    }
    
    fun addDebugLog(log: String) {
        _debugLog = "${_debugLog}\n${System.currentTimeMillis() % 10000}: $log".trimStart()
        // 限制日志长度
        if (_debugLog.length > 500) {
            _debugLog = _debugLog.substring(_debugLog.length - 500)
        }
    }
    
    fun setError(errorMsg: String) {
        _error = errorMsg
        _isLoading = false
    }
    
    fun setScreenshot(bitmap: Bitmap?) {
        _screenshot = bitmap
    }
    
    fun setShowScreenshot(show: Boolean) {
        _showScreenshot = show
    }
    
    fun clear() {
        _aiResponse = ""
        _isLoading = false
        _error = null
        _screenshot = null
        _showScreenshot = false
        _statusMessage = ""
        _debugLog = ""
    }
}
