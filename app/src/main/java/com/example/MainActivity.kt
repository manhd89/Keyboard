package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.example.viengines.ViEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainKeyboardApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainKeyboardApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Tổng quan, 1: Bàn phím Canvas, 2: Engine JNI

    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }

    // Check system IME activation status
    LaunchedEffect(Unit) {
        checkImeStatus(context) { enabled, selected ->
            isImeEnabled = enabled
            isImeSelected = selected
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header / Navigation Bar
        AppTopHeader(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // Main Tab Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> ImeSetupScreen(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onRefreshStatus = {
                        checkImeStatus(context) { enabled, selected ->
                            isImeEnabled = enabled
                            isImeSelected = selected
                        }
                    }
                )
                1 -> CanvasKeyboardDemoScreen()
                2 -> EngineDebuggerScreen()
            }
        }
    }
}

@Composable
fun AppTopHeader(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val isNative = ViEngine.isNativeEngineAvailable()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row Title & Engine Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VN",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    Column {
                        Text(
                            text = "Bàn Phím Tiếng Việt",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Telex & VNI • Minimalist Keyboard Engine",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Engine Badge Pill
                Surface(
                    color = if (isNative) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isNative) Color(0xFF10B981) else Color(0xFF3B82F6))
                        )
                        Text(
                            text = if (isNative) "JNI Rust Engine" else "Kotlin Engine",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Apple / Linear Segmented Control Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                val tabs = listOf("Tổng quan", "Bàn phím Canvas", "Bộ gõ Engine")
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val bgColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        label = "tabBg"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabText"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(bgColor)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 8.dp)
                            .testTag("tab_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = textColor
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImeSetupScreen(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Auto refresh status whenever user returns to this screen (e.g., from system settings or keyboard selector)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onRefreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero Section
        HeroSection(
            isImeEnabled = isImeEnabled,
            isImeSelected = isImeSelected
        )

        // Setup Steps Section Header
        Text(
            text = "Trạng thái & Hướng dẫn kích hoạt",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )

        // Step 1: Enable Input Method
        SetupStepCard(
            stepNumber = "1",
            title = "Bật Bàn phím trong Cài đặt",
            description = "Cho phép 'Bàn Phím Tiếng Việt' hoạt động làm dịch vụ nhập liệu trên thiết bị Android.",
            isCompleted = isImeEnabled,
            statusText = if (isImeEnabled) "Đã bật trong Cài đặt" else "Chưa kích hoạt",
            buttonText = if (isImeEnabled) "Mở Cài đặt hệ thống" else "Kích hoạt ngay",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        // Step 2: Select Input Method
        SetupStepCard(
            stepNumber = "2",
            title = "Chọn làm Bàn phím Mặc định",
            description = "Đặt 'Bàn Phím Tiếng Việt' làm phương thức nhập liệu chính để gõ trong mọi ứng dụng.",
            isCompleted = isImeSelected,
            statusText = if (isImeSelected) "Đã đặt làm mặc định" else "Chưa chọn làm mặc định",
            buttonText = if (isImeSelected) "Thay đổi bàn phím" else "Chọn làm mặc định",
            onAction = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        )
    }
}

@Composable
fun HeroSection(
    isImeEnabled: Boolean,
    isImeSelected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Tag Pill
            val isFullyActive = isImeEnabled && isImeSelected
            val statusBg = if (isFullyActive) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
            val statusColor = if (isFullyActive) Color(0xFF10B981) else Color(0xFFD97706)
            val statusText = when {
                isFullyActive -> "Sẵn sàng gõ trên toàn hệ thống"
                isImeEnabled -> "Cần chọn làm bàn phím mặc định (Bước 2)"
                else -> "Chưa bật trong Cài đặt hệ thống (Bước 1)"
            }

            Surface(
                color = statusBg,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Text(
                text = "Bộ gõ Tiếng Việt Telex & VNI",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Text(
                text = "Tích hợp JNI Rust Engine phản hồi tức thì với độ trễ siêu thấp. Thiết kế tối giản, tinh tế chuẩn thương mại.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean,
    statusText: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isCompleted) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Surface(
                        color = if (isCompleted) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun CanvasKeyboardDemoScreen() {
    var canvasTypedText by remember { mutableStateOf("Xin chào Việt Nam! ") }
    var activeTheme by remember { mutableStateOf(KeyboardView.KeyboardTheme.DARK_SLATE) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Output Preview Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Văn bản gõ từ Canvas View",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                if (canvasTypedText.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(canvasTypedText))
                                    Toast.makeText(context, "Đã chép vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Sao chép", modifier = Modifier.size(18.dp))
                        }

                        IconButton(onClick = { canvasTypedText = "" }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Xóa", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (canvasTypedText.isEmpty()) "Chạm phím Canvas phía dưới để gõ..." else canvasTypedText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (canvasTypedText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // Theme selector label
                Text(
                    text = "Giao diện Bàn phím:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                // Theme Selector Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf(
                        KeyboardView.KeyboardTheme.DARK_SLATE,
                        KeyboardView.KeyboardTheme.APPLE_LIGHT,
                        KeyboardView.KeyboardTheme.CHARCOAL_MONO,
                        KeyboardView.KeyboardTheme.WARM_CANVAS
                    )

                    themes.forEach { theme ->
                        val isSelected = activeTheme.name == theme.name
                        Surface(
                            onClick = { activeTheme = theme },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(theme.backgroundColor),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) Color(theme.accentColor) else Color(theme.keyBorderColor)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = theme.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(theme.textColor),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Canvas Keyboard View
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(12.dp)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            AndroidView(
                factory = { ctx ->
                    KeyboardView(ctx).apply {
                        theme = activeTheme
                        var composing = StringBuilder()

                        listener = object : KeyboardView.OnKeyboardActionListener {
                            override fun onKeyTyped(code: Int, label: String) {
                                composing.append(label)
                                val transformed = ViEngine.transformText(composing.toString(), currentMethod)
                                currentComposingText = transformed
                            }

                            override fun onBackspace() {
                                if (composing.isNotEmpty()) {
                                    composing.deleteCharAt(composing.length - 1)
                                    val transformed = ViEngine.transformText(composing.toString(), currentMethod)
                                    currentComposingText = transformed
                                } else if (canvasTypedText.isNotEmpty()) {
                                    canvasTypedText = canvasTypedText.dropLast(1)
                                }
                            }

                            override fun onSpace() {
                                if (composing.isNotEmpty()) {
                                    val transformed = ViEngine.transformText(composing.toString(), currentMethod)
                                    canvasTypedText += "$transformed "
                                    composing.clear()
                                    currentComposingText = ""
                                } else {
                                    canvasTypedText += " "
                                }
                            }

                            override fun onEnter() {
                                if (composing.isNotEmpty()) {
                                    val transformed = ViEngine.transformText(composing.toString(), currentMethod)
                                    canvasTypedText += "$transformed\n"
                                    composing.clear()
                                    currentComposingText = ""
                                } else {
                                    canvasTypedText += "\n"
                                }
                            }

                            override fun onMethodChanged(method: Int) {
                                if (composing.isNotEmpty()) {
                                    currentComposingText = ViEngine.transformText(composing.toString(), method)
                                }
                            }
                        }
                    }
                },
                update = { view ->
                    view.theme = activeTheme
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun EngineDebuggerScreen() {
    var debugInput by remember { mutableStateOf("tie2ng vie6t2 gow3 telex hoa3c vni") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val telexResult = remember(debugInput) {
        ViEngine.transformText(debugInput, ViEngine.METHOD_TELEX)
    }

    val vniResult = remember(debugInput) {
        ViEngine.transformText(debugInput, ViEngine.METHOD_VNI)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Engine Tester Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Bộ kiểm thử Engine (JNI / Kotlin)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "Nhập chuỗi ký tự gõ phím thô để kiểm tra kết quả biến đổi real-time:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = debugInput,
                    onValueChange = { debugInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chuỗi phím thô (Input buffer)") },
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ResultBox(
                        title = "TELEX Output",
                        resultText = telexResult,
                        badgeColor = MaterialTheme.colorScheme.primary,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(telexResult))
                            Toast.makeText(context, "Đã chép TELEX", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ResultBox(
                        title = "VNI Output",
                        resultText = vniResult,
                        badgeColor = Color(0xFF2563EB),
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(vniResult))
                            Toast.makeText(context, "Đã chép VNI", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Rules Cheat Sheet Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Bảng quy tắc Telex & VNI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                RuleRow(mode = "TELEX", rule = "s: sắc, f: huyền, r: hỏi, x: ngã, j: nặng, z: xóa dấu")
                RuleRow(mode = "TELEX", rule = "aa: â, aw: ă, ee: ê, oo: ô, ow: ơ, uw/w: ư, dd: đ")
                RuleRow(mode = "VNI", rule = "1: sắc, 2: huyền, 3: hỏi, 4: ngã, 5: nặng, 0: xóa dấu")
                RuleRow(mode = "VNI", rule = "6: â/ê/ô, 7: ơ/ư, 8: ă, 9: đ")
            }
        }
    }
}

@Composable
fun ResultBox(
    title: String,
    resultText: String,
    badgeColor: Color,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Sao chép", modifier = Modifier.size(14.dp))
                }
            }

            Text(
                text = if (resultText.isEmpty()) "(Rỗng)" else resultText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
fun RuleRow(mode: String, rule: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = if (mode == "TELEX") MaterialTheme.colorScheme.primary else Color(0xFF2563EB),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = mode,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
        }

        Text(
            text = rule,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

private fun checkImeStatus(
    context: Context,
    onResult: (Boolean, Boolean) -> Unit
) {
    try {
        val enabledMethods = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: ""

        val defaultMethod = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""

        val packageName = context.packageName
        val isEnabled = enabledMethods.contains(packageName)
        val isSelected = defaultMethod.contains(packageName)

        onResult(isEnabled, isSelected)
    } catch (e: Exception) {
        onResult(false, false)
    }
}
