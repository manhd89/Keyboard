package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                    modifier = Modifier.fillMaxSize()
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Setup, 1: Canvas Keyboard, 2: Debugger

    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }

    // Check IME status periodically
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
        // App Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gõ Tiếng Việt",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Bộ gõ Telex & VNI (JNI Rust Engine + Canvas)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                    }

                    // Engine Badge
                    val isNative = ViEngine.isNativeEngineAvailable()
                    Surface(
                        color = if (isNative) Color(0xFF047857) else Color(0xFF0284C7),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isNative) "⚡ JNI Rust" else "⚙️ Kotlin",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    val tabs = listOf("Thiết lập", "Bàn phím Canvas", "Giao diện")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val bgColor by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "tabBg"
                        )
                        val textColor by animateColorAsState(
                            if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "tabText"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp)
                                .testTag("tab_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                            )
                        }
                    }
                }
            }
        }

        // Tab Content
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
fun ImeSetupScreen(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current
    var testInputText by remember { mutableStateOf("") }
    var showEmbeddedKeyboard by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Alert Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                isImeEnabled && isImeSelected -> Color(0xFFE6F4EA)
                isImeEnabled -> Color(0xFFFEF7E0)
                else -> Color(0xFFFCE8E6)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isImeEnabled && isImeSelected -> Icons.Default.CheckCircle
                        isImeEnabled -> Icons.Default.Info
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when {
                        isImeEnabled && isImeSelected -> Color(0xFF137333)
                        isImeEnabled -> Color(0xFFB06000)
                        else -> Color(0xFFC5221F)
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = when {
                            isImeEnabled && isImeSelected -> "Bàn phím hệ thống đã sẵn sàng!"
                            isImeEnabled -> "Bước 1 xong: Đã bật dịch vụ. Vui lòng làm Bước 2 để chọn Bàn phím làm mặc định."
                            else -> "Chưa bật Bàn phím hệ thống. Vui lòng thực hiện Bước 1 & 2 bên dưới."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isImeEnabled && isImeSelected -> Color(0xFF137333)
                                isImeEnabled -> Color(0xFFB06000)
                                else -> Color(0xFFC5221F)
                            }
                        )
                    )
                }
            }
        }

        // Step 1: Enable Input Method
        SetupStepCard(
            stepNumber = "1",
            title = "Bật Bàn phím trong Cài đặt",
            description = "Cho phép ứng dụng 'Bàn Phím Tiếng Việt' hoạt động như một phương thức nhập liệu hệ thống.",
            isCompleted = isImeEnabled,
            buttonText = if (isImeEnabled) "Đã kích hoạt ✓" else "Mở Cài đặt Bàn phím",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        // Step 2: Select Input Method
        SetupStepCard(
            stepNumber = "2",
            title = "Chọn làm Bàn phím Mặc định",
            description = "Chuyển đổi phương thức nhập hiện tại sang 'Bàn Phím Tiếng Việt (Telex/VNI)'.",
            isCompleted = isImeSelected,
            buttonText = if (isImeSelected) "Đã chọn làm mặc định ✓" else "Chọn Bàn phím",
            onAction = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        )

        // Refresh status button
        OutlinedButton(
            onClick = onRefreshStatus,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Làm mới trạng thái")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kiểm tra lại trạng thái Bàn phím")
        }

        Spacer(modifier = Modifier.height(4.dp))

        // System Input Testing Area
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thử nghiệm gõ văn bản hệ thống",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "Nhấp vào ô bên dưới hoặc bấm nút 'Hiển thị Bàn Phím Hệ Thống' để nảy bàn phím:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nhập thử tiếng Việt tại đây...") },
                    placeholder = { Text("Ví dụ: Tiếng Việt gõ Telex hoặc VNI") },
                    trailingIcon = {
                        if (testInputText.isNotEmpty()) {
                            IconButton(onClick = { testInputText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa")
                            }
                        }
                    }
                )

                // Quick Action Buttons to trigger Soft Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Hiển thị Bàn Phím Hệ Thống", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showInputMethodPicker()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Đổi Bàn Phím", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Switch to toggle embedded direct keyboard for guaranteed preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hiện bàn phím trực tiếp trên màn hình",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Switch(
                        checked = showEmbeddedKeyboard,
                        onCheckedChange = { showEmbeddedKeyboard = it }
                    )
                }

                if (showEmbeddedKeyboard) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        tonalElevation = 4.dp
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                KeyboardView(ctx).apply {
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
                                            } else if (testInputText.isNotEmpty()) {
                                                testInputText = testInputText.dropLast(1)
                                            }
                                        }

                                        override fun onSpace() {
                                            if (composing.isNotEmpty()) {
                                                val transformed = ViEngine.transformText(composing.toString(), currentMethod)
                                                testInputText += "$transformed "
                                                composing.clear()
                                                currentComposingText = ""
                                            } else {
                                                testInputText += " "
                                            }
                                        }

                                        override fun onEnter() {
                                            if (composing.isNotEmpty()) {
                                                val transformed = ViEngine.transformText(composing.toString(), currentMethod)
                                                testInputText += transformed
                                                composing.clear()
                                                currentComposingText = ""
                                            }
                                            testInputText += "\n"
                                        }

                                        override fun onMethodChanged(method: Int) {
                                            if (composing.isNotEmpty()) {
                                                val transformed = ViEngine.transformText(composing.toString(), method)
                                                currentComposingText = transformed
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                if (testInputText.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Kết quả gõ: $testInputText",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean,
    buttonText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) Color(0xFF047857).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isCompleted) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF047857)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) Color(0xFF047857) else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                } else {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Button(
                    onClick = onAction,
                    enabled = !isCompleted || true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) Color(0xFF047857) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
fun CanvasKeyboardDemoScreen() {
    var canvasTypedText by remember { mutableStateOf("Xin chào Việt Nam! ") }
    var activeTheme by remember { mutableStateOf(KeyboardView.KeyboardTheme.DARK_SLATE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Output Preview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Văn bản đã gõ từ Canvas View:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    IconButton(onClick = { canvasTypedText = "" }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa toàn bộ")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (canvasTypedText.isEmpty()) "Chạm vào bàn phím Canvas phía dưới để bắt đầu gõ..." else canvasTypedText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = if (canvasTypedText.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Theme selector
                Text(
                    text = "Đổi giao diện Canvas:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf(
                        KeyboardView.KeyboardTheme.DARK_SLATE,
                        KeyboardView.KeyboardTheme.EMERALD_LIGHT,
                        KeyboardView.KeyboardTheme.CYBER_NEON,
                        KeyboardView.KeyboardTheme.SUNSET_WARM
                    )

                    themes.forEach { t ->
                        val isSelected = activeTheme.name == t.name
                        Surface(
                            onClick = { activeTheme = t },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(t.backgroundColor),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(t.accentColor)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = t.name.split(" ")[0],
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(t.textColor),
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
                .height(290.dp)
                .clip(RoundedCornerShape(12.dp)),
            tonalElevation = 6.dp
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⚡ Engine JNI Transformation Debugger",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "Thử nghiệm chuỗi ký tự gõ thô để kiểm tra kết quả biến đổi của hàm transform_buffer (Rust vi / Kotlin Engine):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = debugInput,
                    onValueChange = { debugInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chuỗi phím gõ thô (Input buffer)") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ResultBox(
                        title = "TELEX Output",
                        resultText = telexResult,
                        badgeColor = Color(0xFF0D9488),
                        modifier = Modifier.weight(1f)
                    )

                    ResultBox(
                        title = "VNI Output",
                        resultText = vniResult,
                        badgeColor = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Rules Cheat Sheet Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📖 Bảng quy tắc Telex & VNI",
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
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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

            Text(
                text = if (resultText.isEmpty()) "(Rỗng)" else resultText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
fun RuleRow(mode: String, rule: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = if (mode == "TELEX") Color(0xFF0D9488) else Color(0xFF2563EB),
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
            style = MaterialTheme.typography.bodySmall
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
