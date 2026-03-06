package com.zzes.floatai.ui.floatwindow

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import kotlin.math.roundToInt

@Composable
fun FloatWindowContent(
    onClose: () -> Unit,
    onScreenshot: () -> Unit,
    windowManager: WindowManager,
    layoutParams: WindowManager.LayoutParams,
    floatView: ComposeView,
    useOcrMode: Boolean = true,
    onOcrModeChange: (Boolean) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    // 使用全局状态
    val aiResponse = FloatWindowState.aiResponse
    val isLoading = FloatWindowState.isLoading
    val error = FloatWindowState.error
    val screenshot = FloatWindowState.screenshot
    val showScreenshot = FloatWindowState.showScreenshot
    val statusMessage = FloatWindowState.statusMessage
    val debugLog = FloatWindowState.debugLog
    
    // 拖拽状态
    var offsetX by remember { mutableFloatStateOf(layoutParams.x.toFloat()) }
    var offsetY by remember { mutableFloatStateOf(layoutParams.y.toFloat()) }

    // 显示截图放大查看
    if (showScreenshot && screenshot != null) {
        ScreenshotViewer(
            screenshot = screenshot,
            onClose = { FloatWindowState.setShowScreenshot(false) }
        )
    }

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    
                    layoutParams.x = offsetX.roundToInt()
                    layoutParams.y = offsetY.roundToInt()
                    windowManager.updateViewLayout(floatView, layoutParams)
                }
            }
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 56.dp, max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Column {
                // 标题栏（可拖拽区域）
                FloatWindowHeader(
                    isExpanded = isExpanded,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onClose = onClose,
                    onScreenshot = {
                        FloatWindowState.setLoading(true)
                        onScreenshot()
                    },
                    isLoading = isLoading,
                    hasScreenshot = screenshot != null,
                    onViewScreenshot = { FloatWindowState.setShowScreenshot(true) },
                    useOcrMode = useOcrMode,
                    onOcrModeChange = onOcrModeChange
                )
                
                // 展开的内容区域
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // 截图缩略图显示（只要有截图就显示）
                        if (screenshot != null) {
                            ScreenshotThumbnail(
                                screenshot = screenshot,
                                onClick = { FloatWindowState.setShowScreenshot(true) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // 错误信息显示
                        if (error != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // 调试日志显示区域（临时调试用）
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = debugLog.ifEmpty { "等待操作..." },
                                modifier = Modifier.padding(8.dp),
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // AI 响应显示区域（带滚动条，支持Markdown）
                        if (aiResponse.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                val scrollState = rememberScrollState()
                                val parser = remember { CommonmarkAstNodeParser() }
                                val markdownAst = remember(aiResponse) { parser.parse(aiResponse) }

                                SelectionContainer {
                                    RichText(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .verticalScroll(scrollState),
                                        style = RichTextStyle()
                                    ) {
                                        BasicMarkdown(markdownAst)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // 提示文本
                        if (aiResponse.isEmpty() && !isLoading && error == null) {
                            Text(
                                text = "点击相机图标截图识别",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        
                        // 加载指示器
                        if (isLoading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI 识别中...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (statusMessage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = statusMessage,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatWindowHeader(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    onScreenshot: () -> Unit,
    isLoading: Boolean,
    hasScreenshot: Boolean = false,
    onViewScreenshot: () -> Unit = {},
    useOcrMode: Boolean = true,
    onOcrModeChange: (Boolean) -> Unit = {}
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        label = "arrow_rotation"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 拖拽指示器
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "拖拽移动",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // 标题
        Text(
            text = "FloatAI v2.1",
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        
        // OCR 开关
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OCR",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Switch(
                checked = useOcrMode,
                onCheckedChange = onOcrModeChange,
                modifier = Modifier.size(32.dp),
                thumbContent = if (useOcrMode) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // 查看截图按钮（如果有截图）
        if (hasScreenshot) {
            IconButton(
                onClick = onViewScreenshot,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "查看截图",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // 截图按钮
        IconButton(
            onClick = onScreenshot,
            modifier = Modifier.size(28.dp),
            enabled = !isLoading
        ) {
            Icon(
                imageVector = Icons.Default.AddCircle,
                contentDescription = "截图识别",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        // 展开/折叠按钮
        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) 
                    Icons.Default.KeyboardArrowDown 
                else 
                    Icons.Default.KeyboardArrowUp,
                contentDescription = if (isExpanded) "折叠" else "展开",
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        // 关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// 截图缩略图
@Composable
private fun ScreenshotThumbnail(
    screenshot: android.graphics.Bitmap,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = screenshot.asImageBitmap(),
                contentDescription = "截图缩略图",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Text(
                text = "点击查看大图",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

// 截图查看器（全屏查看）
@Composable
private fun ScreenshotViewer(
    screenshot: android.graphics.Bitmap,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = screenshot.asImageBitmap(),
                contentDescription = "截图",
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "点击任意位置关闭",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 14.sp
            )
        }
    }
}
