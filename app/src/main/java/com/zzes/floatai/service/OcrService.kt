package com.zzes.floatai.service

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * OCR 服务 - 使用 Google ML Kit 进行文字识别
 */
class OcrService {

    companion object {
        private const val TAG = "OcrService"
    }

    // 中文文本识别器
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    
    // 拉丁文（英文）文本识别器
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * 识别图片中的文字
     * @param bitmap 待识别的图片
     * @param preferChinese 是否优先使用中文识别器（默认true）
     * @return 识别结果，包含所有提取的文字
     */
    suspend fun recognizeText(bitmap: Bitmap, preferChinese: Boolean = true): Result<String> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "开始 OCR 识别，图片尺寸: ${bitmap.width}x${bitmap.height}")
            
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = if (preferChinese) chineseRecognizer else latinRecognizer
            
            val result = suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val extractedText = visionText.text
                        Log.d(TAG, "OCR 识别成功，提取文字长度: ${extractedText.length}")
                        continuation.resume(Result.success(extractedText))
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR 识别失败: ${e.message}", e)
                        continuation.resume(Result.failure(e))
                    }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "OCR 处理异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 识别图片中的文字，并返回结构化信息
     * @param bitmap 待识别的图片
     * @param preferChinese 是否优先使用中文识别器
     * @return 识别结果，包含文字块、行、元素等信息
     */
    suspend fun recognizeTextDetailed(bitmap: Bitmap, preferChinese: Boolean = true): Result<RecognizedText> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "开始详细 OCR 识别，图片尺寸: ${bitmap.width}x${bitmap.height}")
            
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = if (preferChinese) chineseRecognizer else latinRecognizer
            
            val result = suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val textBlocks = mutableListOf<TextBlock>()
                        
                        for (block in visionText.textBlocks) {
                            val lines = mutableListOf<Line>()
                            
                            for (line in block.lines) {
                                val elements = mutableListOf<Element>()
                                
                                for (element in line.elements) {
                                    elements.add(
                                        Element(
                                            text = element.text,
                                            boundingBox = element.boundingBox,
                                            confidence = element.confidence
                                        )
                                    )
                                }
                                
                                lines.add(
                                    Line(
                                        text = line.text,
                                        boundingBox = line.boundingBox,
                                        confidence = line.confidence,
                                        elements = elements
                                    )
                                )
                            }
                            
                            textBlocks.add(
                                TextBlock(
                                    text = block.text,
                                    boundingBox = block.boundingBox,
                                    lines = lines
                                )
                            )
                        }
                        
                        val recognizedText = RecognizedText(
                            fullText = visionText.text,
                            textBlocks = textBlocks
                        )
                        
                        Log.d(TAG, "详细 OCR 识别成功，共 ${textBlocks.size} 个文本块")
                        continuation.resume(Result.success(recognizedText))
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "详细 OCR 识别失败: ${e.message}", e)
                        continuation.resume(Result.failure(e))
                    }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "详细 OCR 处理异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 释放资源
     */
    fun close() {
        chineseRecognizer.close()
        latinRecognizer.close()
        Log.d(TAG, "OCR 服务已关闭")
    }
}

/**
 * 识别结果数据类
 */
data class RecognizedText(
    val fullText: String,
    val textBlocks: List<TextBlock>
) {
    /**
     * 获取按行组织的文本
     */
    fun getLines(): List<String> {
        return textBlocks.flatMap { it.lines.map { line -> line.text } }
    }

    /**
     * 获取格式化后的文本（按块分隔）
     */
    fun getFormattedText(): String {
        return textBlocks.joinToString("\n\n") { it.text }
    }
}

/**
 * 文本块
 */
data class TextBlock(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val lines: List<Line>
)

/**
 * 行
 */
data class Line(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val confidence: Float,
    val elements: List<Element>
)

/**
 * 元素（单词/字符）
 */
data class Element(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val confidence: Float
)
