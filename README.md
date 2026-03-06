# FloatAI - 智能悬浮助手(这是一个纯AI实现的小项目)
![420f51abd521e091b0022fcb5b19298c](https://github.com/user-attachments/assets/18b4ad0e-cfde-4a81-9df0-e079ae5d4e94)

一款 Android 系统级悬浮窗应用，支持截图并通过 AI 智能识别内容。无论是题目解答、代码分析还是文档总结，一键截图即可获得答案。

## 功能特性

- 🎯 **系统级悬浮窗**：可在任何界面上使用，不干扰当前操作
- 📸 **一键截图识别**：启动时授权一次，之后可随时截图识别
- 🤖 **智能内容识别**：
  - 题目自动解答（选择题、填空题、问答题等）
  - 代码错误分析与修复建议
  - 文档内容快速总结
  - 界面内容智能描述
- 🔤 **OCR 模式切换**：支持 OCR 文字识别和图像识别两种模式
- 🖐️ **可拖拽可折叠**：悬浮窗支持自由拖拽位置，可折叠为简洁模式
- ⚡ **快速响应**：优化的截图和 AI 分析流程，响应迅速

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构组件**：ViewModel、Lifecycle、Service
- **截图技术**：MediaProjection API
- **悬浮窗**：WindowManager + ComposeView
- **网络**：原生 HttpURLConnection

## 系统要求

- Android 11 (API 30) 及以上
- 需要悬浮窗权限和屏幕录制权限

## 安装使用

### 1. 配置 AI API

在项目根目录创建 `local.properties` 文件（参考 `local.properties.example`），填入你的 AI 配置：

```properties
ai.api.key=你的API Key
ai.api.url=https://api.siliconflow.cn/v1/chat/completions
ai.model=Pro/moonshotai/Kimi-K2.5
```

支持的 AI 服务商：
- SiliconFlow (Kimi-K2.5、DeepSeek 等)
- OpenAI (GPT-4o, GPT-4o-mini)
- 阿里云百炼 (qwen-vl-plus)
- 其他 OpenAI 格式兼容的 API

### 2. 编译安装

```bash
./gradlew assembleDebug
```

### 3. 使用步骤

1. 打开应用，点击"启动悬浮窗"按钮
2. 授予悬浮窗权限
3. **授予屏幕录制权限**（启动时授权一次，之后可多次截图）
4. 切换到需要识别的界面（如微信、题目、代码、文档等）
5. 点击悬浮窗的相机图标截图
6. 等待 AI 分析结果，答案将显示在悬浮窗中

## 项目结构

```
app/src/main/java/com/zzes/floatai/
├── MainActivity.kt              # 主界面，权限申请和服务管理
├── service/
│   ├── FloatWindowService.kt    # 悬浮窗服务（前台服务）
│   ├── ScreenshotHelper.kt      # 截图功能（MediaProjection）
│   ├── AiService.kt             # AI 识别服务
│   └── OcrService.kt            # OCR 文字识别服务
├── ui/
│   ├── floatwindow/
│   │   ├── FloatWindowContent.kt    # 悬浮窗 UI（Compose）
│   │   ├── FloatWindowState.kt      # 悬浮窗状态管理
│   │   └── FloatWindowViewModel.kt  # 悬浮窗 ViewModel
│   └── theme/                   # 主题配置
└── AndroidManifest.xml          # 权限和服务声明
```

## 核心实现

### 权限授权机制

启动悬浮窗时一次性请求 MediaProjection 权限，授权结果保存在 Service 中，之后可多次使用：

```kotlin
// MainActivity 中请求权限
private val mediaProjectionLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        FloatWindowService.instance?.setProjectionResult(result.resultCode, result.data!!)
    }
}

// FloatWindowService 中保存和使用权限
fun setProjectionResult(resultCode: Int, data: Intent) {
    this.projectionResultCode = resultCode
    this.projectionData = data
}
```

### 悬浮窗服务

使用 `WindowManager` 创建系统级悬浮窗，支持拖拽定位：

```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    PixelFormat.TRANSLUCENT
)
```

### 截图功能

基于 `MediaProjection` API 实现高质量屏幕截图：

```kotlin
val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
    as MediaProjectionManager
val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
```

### AI 分析

使用多模态 AI 模型分析截图内容，智能识别场景并给出针对性回答。

## 注意事项

1. **权限要求**：首次使用需要授予悬浮窗权限和屏幕录制权限
2. **权限时效**：MediaProjection 权限可能在一断时间后失效，如截图失败请重新启动悬浮窗
3. **前台服务**：截图功能依赖前台服务，请确保允许应用后台运行
4. **网络连接**：AI 分析需要网络连接
5. **API 费用**：使用真实 AI API 会产生相应费用

## 开发计划

- [x] 优化截图授权流程，支持多次截图
- [ ] 支持自定义提示词模板
- [ ] 历史记录功能
- [ ] 多语言支持
- [ ] 离线 OCR 识别
- [ ] 悬浮窗样式自定义

## License

MIT License

## 致谢

- Jetpack Compose 团队
- Android Open Source Project
