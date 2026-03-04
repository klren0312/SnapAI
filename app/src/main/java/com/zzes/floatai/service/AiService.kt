package com.zzes.floatai.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.zzes.floatai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AiService {

    // 从 BuildConfig 读取配置（由 local.properties 生成）
    private var apiKey: String = BuildConfig.AI_API_KEY
    private var apiUrl: String = BuildConfig.AI_API_URL
    private var model: String = BuildConfig.AI_MODEL

    fun configure(apiKey: String, apiUrl: String, model: String) {
        this.apiKey = apiKey
        this.apiUrl = apiUrl
        this.model = model
    }

    /**
     * 分析图片（使用图像模式 - 直接发送图片）
     */
    suspend fun analyzeImage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        Log.d("AiService", "开始 AI 图像分析，bitmap: ${bitmap.width}x${bitmap.height}")
        
        // 检查 API Key 是否配置
        if (apiKey.isBlank()) {
            Log.e("AiService", "API Key 未配置")
            return@withContext Result.failure(Exception("API Key 未配置，请在代码中设置你的 API Key"))
        }
        
        try {
            // 将图片转换为 Base64
            Log.d("AiService", "转换图片为 Base64...")
            val base64Image = bitmapToBase64(bitmap)
            Log.d("AiService", "Base64 转换完成，长度: ${base64Image.length}")
            
            // 构建提示词
            val prompt = """
                请直接描述这张截图的内容：
                
                1. 如果是普通界面/页面，请直接描述显示的内容和关键信息
                2. 如果是题目（选择题、填空题、问答题等），请直接给出答案和简要解析
                3. 如果是代码/错误信息，请说明问题所在和解决方法
                4. 如果是文档/文章，请总结核心内容
                
                要求：
                - 直接给出结果，不要废话
                - 如果是题目，先给答案再给解析
                - 保持简洁明了
            """.trimIndent()
            
            // 构建请求体（多模态 - 包含图片）
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/png;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 1000)
            }

            Log.d("AiService", "发送请求到: $apiUrl")
            Log.d("AiService", "使用模型: $model")
            
            // 更新状态并记录请求
            com.zzes.floatai.ui.floatwindow.FloatWindowState.setStatusMessage("正在发送图片到 AI...")
            com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("请求URL: $apiUrl")
            com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("请求模型: $model")
            
            // 发送请求
            val response = sendRequest(requestBody)
            
            parseResponse(response)

        } catch (e: Exception) {
            Log.e("AiService", "AI 分析异常: ${e.message}", e)
            com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("异常: ${e.message}")
            Result.failure(Exception("AI 分析失败: ${e.message}"))
        }
    }

    /**
     * 分析文本（使用 OCR 结果 - 只发送文字）
     * 更快、更省流量、成本更低
     */
    suspend fun analyzeText(ocrText: String): Result<String> = withContext(Dispatchers.IO) {
        Log.d("AiService", "开始 AI 文本分析，文字长度: ${ocrText.length}")
        
        // 检查 API Key 是否配置
        if (apiKey.isBlank()) {
            Log.e("AiService", "API Key 未配置")
            return@withContext Result.failure(Exception("API Key 未配置，请在代码中设置你的 API Key"))
        }
        
        // 检查 OCR 结果是否为空
        if (ocrText.isBlank()) {
            Log.w("AiService", "OCR 文本为空")
            return@withContext Result.failure(Exception("未能从图片中识别出文字"))
        }
        
        try {
            // 构建提示词
            val prompt = """
                以下是从截图中识别出的文字内容：
                
                ```
                $ocrText
                ```
                
                请根据以上内容：
                
                1. 如果是题目（选择题、填空题、问答题等），请直接给出答案和简要解析
                2. 如果是代码/错误信息，请说明问题所在和解决方法
                3. 如果是文档/文章，请总结核心内容
                4. 如果是普通界面/页面，请描述显示的内容和关键信息
                
                要求：
                - 直接给出结果，不要废话
                - 如果是题目，先给答案再给解析
                - 保持简洁明了
            """.trimIndent()
            
            // 构建请求体（纯文本 - 不包含图片）
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", 1000)
            }

            Log.d("AiService", "发送文本请求到: $apiUrl")
            Log.d("AiService", "使用模型: $model")
            
            // 更新状态并记录请求
            com.zzes.floatai.ui.floatwindow.FloatWindowState.setStatusMessage("正在发送文字到 AI...")
            com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("请求URL: $apiUrl")
            com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("请求模型: $model (文本模式)")
            
            // 发送请求
            val response = sendRequest(requestBody)
            
            parseResponse(response)

        } catch (e: Exception) {
            Log.e("AiService", "AI 文本分析异常: ${e.message}", e)
            com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("异常: ${e.message}")
            Result.failure(Exception("AI 分析失败: ${e.message}"))
        }
    }

    /**
     * 解析 AI 响应
     */
    private fun parseResponse(response: String): Result<String> {
        Log.d("AiService", "收到响应，开始解析...")
        com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("收到响应，长度: ${response.length}")
        
        return try {
            val jsonResponse = JSONObject(response)
            
            // 检查是否有错误
            if (jsonResponse.has("error")) {
                val errorObj = jsonResponse.getJSONObject("error")
                val errorMessage = errorObj.optString("message", "未知错误")
                Log.e("AiService", "API 返回错误: $errorMessage")
                com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("API错误: $errorMessage")
                return Result.failure(Exception("AI API 错误: $errorMessage"))
            }
            
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val content = message.getString("content")
                Log.d("AiService", "解析成功，内容长度: ${content.length}")
                com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("解析成功，长度: ${content.length}")
                Result.success(content)
            } else {
                Log.e("AiService", "AI 返回结果为空")
                com.zzes.floatai.ui.floatwindow.FloatWindowState.addDebugLog("AI返回为空")
                Result.failure(Exception("AI 返回结果为空"))
            }
        } catch (e: Exception) {
            Log.e("AiService", "解析响应异常: ${e.message}", e)
            Result.failure(Exception("解析 AI 响应失败: ${e.message}"))
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减小大小
        val scaledBitmap = if (bitmap.width > 1024 || bitmap.height > 1024) {
            val scale = 1024f / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun sendRequest(requestBody: JSONObject): String {
        Log.d("AiService", "开始发送 HTTP 请求...")
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }

            Log.d("AiService", "写入请求体...")
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
                os.flush()
            }

            Log.d("AiService", "等待响应...")
            com.zzes.floatai.ui.floatwindow.FloatWindowState.setStatusMessage("等待 AI 响应...")
            
            val responseCode = connection.responseCode
            Log.d("AiService", "响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("AiService", "响应内容长度: ${response.length}")
                response
            } else {
                val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e("AiService", "HTTP 错误: $responseCode, $errorMessage")
                throw Exception("HTTP $responseCode: $errorMessage")
            }
        } catch (e: Exception) {
            Log.e("AiService", "HTTP 请求异常: ${e.message}")
            throw e
        } finally {
            connection.disconnect()
            Log.d("AiService", "HTTP 连接已关闭")
        }
    }

    // 模拟 AI 响应（用于测试）
    suspend fun mockAnalyzeImage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        // 模拟网络延迟
        kotlinx.coroutines.delay(1500)
        Result.success("这是一张屏幕截图。图片显示了 Android 设备的界面内容。我可以帮助你分析图片中的文字、界面元素或其他可见内容。")
    }
}
