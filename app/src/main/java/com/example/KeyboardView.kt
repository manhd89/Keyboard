package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.viengines.ViEngine
import kotlin.math.max
import kotlin.math.min

/**
 * Custom Keyboard View rendered ENTIRELY using Android Canvas & View.
 * Implements high-performance custom key layouts, touch handling, key popups,
 * visual feedback, and mode/method switching (Telex/VNI).
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class KeyboardMode {
        LOWERCASE,
        UPPERCASE,
        CAPS_LOCK,
        NUMBERS,
        SYMBOLS
    }

    enum class KeyType {
        CHARACTER,
        BACKSPACE,
        SPACE,
        ENTER,
        SHIFT,
        MODE_SWITCH,
        METHOD_SWITCH,
        ACTION
    }

    data class Key(
        val code: Int,
        val label: String,
        val type: KeyType = KeyType.CHARACTER,
        val weight: Float = 1.0f,
        var rect: RectF = RectF()
    )

    data class KeyboardTheme(
        val name: String,
        val backgroundColor: Int,
        val candidateBarBg: Int,
        val keyBgColor: Int,
        val keySpecialBgColor: Int,
        val keyPressedColor: Int,
        val textColor: Int,
        val textSpecialColor: Int,
        val accentColor: Int,
        val popupBgColor: Int,
        val popupTextColor: Int,
        val keyCornerRadiusDp: Float = 6f
    ) {
        companion object {
            val DARK_SLATE = KeyboardTheme(
                name = "Dark Slate",
                backgroundColor = Color.parseColor("#0F172A"),
                candidateBarBg = Color.parseColor("#1E293B"),
                keyBgColor = Color.parseColor("#334155"),
                keySpecialBgColor = Color.parseColor("#1E293B"),
                keyPressedColor = Color.parseColor("#475569"),
                textColor = Color.parseColor("#F8FAFC"),
                textSpecialColor = Color.parseColor("#94A3B8"),
                accentColor = Color.parseColor("#0D9488"),
                popupBgColor = Color.parseColor("#0F766E"),
                popupTextColor = Color.parseColor("#FFFFFF")
            )

            val EMERALD_LIGHT = KeyboardTheme(
                name = "Emerald Light",
                backgroundColor = Color.parseColor("#F1F5F9"),
                candidateBarBg = Color.parseColor("#E2E8F0"),
                keyBgColor = Color.parseColor("#FFFFFF"),
                keySpecialBgColor = Color.parseColor("#CBD5E1"),
                keyPressedColor = Color.parseColor("#94A3B8"),
                textColor = Color.parseColor("#0F172A"),
                textSpecialColor = Color.parseColor("#334155"),
                accentColor = Color.parseColor("#059669"),
                popupBgColor = Color.parseColor("#047857"),
                popupTextColor = Color.parseColor("#FFFFFF")
            )

            val CYBER_NEON = KeyboardTheme(
                name = "Cyber Neon",
                backgroundColor = Color.parseColor("#090D16"),
                candidateBarBg = Color.parseColor("#121829"),
                keyBgColor = Color.parseColor("#1A2238"),
                keySpecialBgColor = Color.parseColor("#121829"),
                keyPressedColor = Color.parseColor("#253354"),
                textColor = Color.parseColor("#00F0FF"),
                textSpecialColor = Color.parseColor("#FF007A"),
                accentColor = Color.parseColor("#00F0FF"),
                popupBgColor = Color.parseColor("#FF007A"),
                popupTextColor = Color.parseColor("#FFFFFF")
            )

            val SUNSET_WARM = KeyboardTheme(
                name = "Sunset Warm",
                backgroundColor = Color.parseColor("#1C1917"),
                candidateBarBg = Color.parseColor("#292524"),
                keyBgColor = Color.parseColor("#44403C"),
                keySpecialBgColor = Color.parseColor("#292524"),
                keyPressedColor = Color.parseColor("#78716C"),
                textColor = Color.parseColor("#FFEDD5"),
                textSpecialColor = Color.parseColor("#F97316"),
                accentColor = Color.parseColor("#EA580C"),
                popupBgColor = Color.parseColor("#C2410C"),
                popupTextColor = Color.parseColor("#FFFFFF")
            )
        }
    }

    interface OnKeyboardActionListener {
        fun onKeyTyped(code: Int, label: String)
        fun onBackspace()
        fun onSpace()
        fun onEnter()
        fun onMethodChanged(method: Int) // 0: TELEX, 1: VNI
    }

    var listener: OnKeyboardActionListener? = null

    // State properties
    var keyboardMode: KeyboardMode = KeyboardMode.LOWERCASE
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var currentMethod: Int = ViEngine.METHOD_TELEX
        set(value) {
            field = value
            listener?.onMethodChanged(value)
            invalidate()
        }

    var theme: KeyboardTheme = KeyboardTheme.DARK_SLATE
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var currentComposingText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var enableHapticFeedback: Boolean = true

    // Touched key tracking
    private var activePressedKey: Key? = null
    private var pressedKeyRect: RectF? = null

    // Backspace auto-repeat
    private val handler = Handler(Looper.getMainLooper())
    private var isBackspaceHolding = false
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (isBackspaceHolding && activePressedKey?.type == KeyType.BACKSPACE) {
                listener?.onBackspace()
                triggerHaptic()
                handler.postDelayed(this, 60)
            }
        }
    }

    // Key collections per layout
    private var keyRows: MutableList<List<Key>> = mutableListOf()

    // Paints for Canvas rendering
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candidateBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySpecialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPressedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySpecialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candidateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        updatePaints()
    }

    private fun updatePaints() {
        bgPaint.color = theme.backgroundColor
        bgPaint.style = Paint.Style.FILL

        candidateBgPaint.color = theme.candidateBarBg
        candidateBgPaint.style = Paint.Style.FILL

        keyBgPaint.color = theme.keyBgColor
        keyBgPaint.style = Paint.Style.FILL

        keySpecialBgPaint.color = theme.keySpecialBgColor
        keySpecialBgPaint.style = Paint.Style.FILL

        keyPressedBgPaint.color = theme.keyPressedColor
        keyPressedBgPaint.style = Paint.Style.FILL

        keyTextPaint.color = theme.textColor
        keyTextPaint.style = Paint.Style.FILL
        keyTextPaint.textAlign = Paint.Align.CENTER
        keyTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        keySpecialTextPaint.color = theme.textSpecialColor
        keySpecialTextPaint.style = Paint.Style.FILL
        keySpecialTextPaint.textAlign = Paint.Align.CENTER
        keySpecialTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        accentPaint.color = theme.accentColor
        accentPaint.style = Paint.Style.FILL

        popupBgPaint.color = theme.popupBgColor
        popupBgPaint.style = Paint.Style.FILL

        popupTextPaint.color = theme.popupTextColor
        popupTextPaint.style = Paint.Style.FILL
        popupTextPaint.textAlign = Paint.Align.CENTER
        popupTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        candidateTextPaint.color = theme.textColor
        candidateTextPaint.style = Paint.Style.FILL
        candidateTextPaint.textAlign = Paint.Align.LEFT
        candidateTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        borderPaint.color = theme.candidateBarBg
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 2f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (280 * resources.displayMetrics.density).toInt()
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)

        val width = if (widthSize > 0) widthSize else resources.displayMetrics.widthPixels

        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> if (heightSize > 0) Math.min(desiredHeight, heightSize) else desiredHeight
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildLayouts(w, h)
    }

    private fun buildLayouts(totalWidth: Int, totalHeight: Int) {
        keyRows.clear()

        val candidateBarHeight = 44f * resources.displayMetrics.density
        val availableHeight = totalHeight - candidateBarHeight
        val rowCount = 4
        val rowHeight = availableHeight / rowCount
        val keyMarginHorizontal = 3f * resources.displayMetrics.density
        val keyMarginVertical = 3.5f * resources.displayMetrics.density

        val rawRows = when (keyboardMode) {
            KeyboardMode.LOWERCASE -> getQwertyRows(uppercase = false)
            KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCK -> getQwertyRows(uppercase = true)
            KeyboardMode.NUMBERS -> getNumberRows()
            KeyboardMode.SYMBOLS -> getSymbolRows()
        }

        var currentY = candidateBarHeight

        for (row in rawRows) {
            val totalRowWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val availableRowWidth = totalWidth - (row.size + 1) * keyMarginHorizontal
            var currentX = keyMarginHorizontal

            for (key in row) {
                val keyWidth = (key.weight / totalRowWeight) * availableRowWidth
                key.rect = RectF(
                    currentX,
                    currentY + keyMarginVertical,
                    currentX + keyWidth,
                    currentY + rowHeight - keyMarginVertical
                )
                currentX += keyWidth + keyMarginHorizontal
            }
            keyRows.add(row)
            currentY += rowHeight
        }
    }

    private fun getQwertyRows(uppercase: Boolean): List<List<Key>> {
        val r1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map {
            val label = if (uppercase) it.uppercase() else it
            Key(label[0].code, label)
        }

        val r2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map {
            val label = if (uppercase) it.uppercase() else it
            Key(label[0].code, label)
        }

        val shiftLabel = when (keyboardMode) {
            KeyboardMode.CAPS_LOCK -> "⇪"
            KeyboardMode.UPPERCASE -> "⇧"
            else -> "⇧"
        }

        val r3 = mutableListOf<Key>().apply {
            add(Key(-1, shiftLabel, KeyType.SHIFT, weight = 1.4f))
            listOf("z", "x", "c", "v", "b", "n", "m").forEach {
                val label = if (uppercase) it.uppercase() else it
                add(Key(label[0].code, label))
            }
            add(Key(-2, "⌫", KeyType.BACKSPACE, weight = 1.4f))
        }

        val methodLabel = if (currentMethod == ViEngine.METHOD_TELEX) "TELEX" else "VNI"

        val r4 = listOf(
            Key(-3, "?123", KeyType.MODE_SWITCH, weight = 1.3f),
            Key(-4, methodLabel, KeyType.METHOD_SWITCH, weight = 1.3f),
            Key(32, "Tiếng Việt", KeyType.SPACE, weight = 4.0f),
            Key(10, "Nhập ↵", KeyType.ENTER, weight = 1.5f)
        )

        return listOf(r1, r2, r3, r4)
    }

    private fun getNumberRows(): List<List<Key>> {
        val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { Key(it[0].code, it) }
        val r2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { Key(it[0].code, it) }

        val r3 = mutableListOf<Key>().apply {
            add(Key(-5, "=/<", KeyType.MODE_SWITCH, weight = 1.4f))
            listOf("*", "\"", "'", ":", ";", "!", "?").forEach { add(Key(it[0].code, it)) }
            add(Key(-2, "⌫", KeyType.BACKSPACE, weight = 1.4f))
        }

        val methodLabel = if (currentMethod == ViEngine.METHOD_TELEX) "TELEX" else "VNI"

        val r4 = listOf(
            Key(-6, "ABC", KeyType.MODE_SWITCH, weight = 1.3f),
            Key(-4, methodLabel, KeyType.METHOD_SWITCH, weight = 1.3f),
            Key(32, "Dấu cách", KeyType.SPACE, weight = 4.0f),
            Key(10, "Hoàn tất", KeyType.ENTER, weight = 1.5f)
        )

        return listOf(r1, r2, r3, r4)
    }

    private fun getSymbolRows(): List<List<Key>> {
        val r1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { Key(it[0].code, it) }
        val r2 = listOf("£", "¥", "€", "¢", "^", "°", "=", "{", "}", "\\").map { Key(it[0].code, it) }

        val r3 = mutableListOf<Key>().apply {
            add(Key(-3, "123", KeyType.MODE_SWITCH, weight = 1.4f))
            listOf("%", "©", "®", "™", "✓", "[", "]").forEach { add(Key(it[0].code, it)) }
            add(Key(-2, "⌫", KeyType.BACKSPACE, weight = 1.4f))
        }

        val methodLabel = if (currentMethod == ViEngine.METHOD_TELEX) "TELEX" else "VNI"

        val r4 = listOf(
            Key(-6, "ABC", KeyType.MODE_SWITCH, weight = 1.3f),
            Key(-4, methodLabel, KeyType.METHOD_SWITCH, weight = 1.3f),
            Key(32, "Dấu cách", KeyType.SPACE, weight = 4.0f),
            Key(10, "Hoàn tất", KeyType.ENTER, weight = 1.5f)
        )

        return listOf(r1, r2, r3, r4)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw overall keyboard background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw top candidate & method bar
        val candidateBarHeight = 44f * resources.displayMetrics.density
        val candidateRect = RectF(0f, 0f, width.toFloat(), candidateBarHeight)
        canvas.drawRect(candidateRect, candidateBgPaint)

        // Draw active input method badge pill
        val pillPaint = if (currentMethod == ViEngine.METHOD_TELEX) accentPaint else keySpecialBgPaint
        val pillRect = RectF(12f, 8f, 130f, candidateBarHeight - 8f)
        canvas.drawRoundRect(pillRect, 16f, 16f, pillPaint)

        popupTextPaint.textSize = 13f * resources.displayMetrics.scaledDensity
        val methodBadge = if (currentMethod == ViEngine.METHOD_TELEX) "⚡ TELEX" else "🔢 VNI"
        canvas.drawText(methodBadge, pillRect.centerX(), pillRect.centerY() + 5f, popupTextPaint)

        // Draw current composing buffer or hint
        candidateTextPaint.textSize = 16f * resources.displayMetrics.scaledDensity
        if (currentComposingText.isNotEmpty()) {
            canvas.drawText(
                "Đang gõ: \"$currentComposingText\"",
                145f,
                candidateBarHeight / 2f + 6f,
                candidateTextPaint
            )
        } else {
            keySpecialTextPaint.textSize = 13f * resources.displayMetrics.scaledDensity
            keySpecialTextPaint.textAlign = Paint.Align.LEFT
            val engineStatus = if (ViEngine.isNativeEngineAvailable()) "Engine Rust JNI" else "Engine Kotlin"
            canvas.drawText(
                "Chạm để nhập • $engineStatus",
                145f,
                candidateBarHeight / 2f + 5f,
                keySpecialTextPaint
            )
            keySpecialTextPaint.textAlign = Paint.Align.CENTER
        }

        // Draw separator line under candidate bar
        canvas.drawLine(0f, candidateBarHeight, width.toFloat(), candidateBarHeight, borderPaint)

        // 3. Draw all keys in rows
        val cornerRadius = theme.keyCornerRadiusDp * resources.displayMetrics.density

        for (row in keyRows) {
            for (key in row) {
                val isPressed = activePressedKey == key
                val isSpecial = key.type != KeyType.CHARACTER

                val paintToUse = when {
                    isPressed -> keyPressedBgPaint
                    isSpecial -> keySpecialBgPaint
                    else -> keyBgPaint
                }

                // Draw key background shadow / rounded rect
                canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, paintToUse)

                // Highlight active Shift / Method keys
                if (key.type == KeyType.SHIFT && (keyboardMode == KeyboardMode.UPPERCASE || keyboardMode == KeyboardMode.CAPS_LOCK)) {
                    canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, accentPaint)
                }

                // Draw label text
                val textPaintToUse = when {
                    key.type == KeyType.SHIFT && (keyboardMode == KeyboardMode.UPPERCASE || keyboardMode == KeyboardMode.CAPS_LOCK) -> popupTextPaint
                    key.type == KeyType.ENTER || key.type == KeyType.METHOD_SWITCH -> popupTextPaint
                    isSpecial -> keySpecialTextPaint
                    else -> keyTextPaint
                }

                textPaintToUse.textSize = when (key.type) {
                    KeyType.SPACE -> 14f * resources.displayMetrics.scaledDensity
                    KeyType.ENTER, KeyType.MODE_SWITCH, KeyType.METHOD_SWITCH -> 13f * resources.displayMetrics.scaledDensity
                    else -> 18f * resources.displayMetrics.scaledDensity
                }

                if (key.type == KeyType.ENTER) {
                    canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, accentPaint)
                }

                val centerY = key.rect.centerY() + (textPaintToUse.textSize / 3f)
                canvas.drawText(key.label, key.rect.centerX(), centerY, textPaintToUse)
            }
        }

        // 4. Draw key tap preview popup bubble floating above pressed key
        activePressedKey?.let { key ->
            if (key.type == KeyType.CHARACTER) {
                val popupWidth = key.rect.width() * 1.3f
                val popupHeight = key.rect.height() * 1.2f
                val popupX = key.rect.centerX() - (popupWidth / 2f)
                val popupY = key.rect.top - popupHeight - 8f

                val popupRect = RectF(popupX, popupY, popupX + popupWidth, popupY + popupHeight)
                canvas.drawRoundRect(popupRect, cornerRadius * 1.5f, cornerRadius * 1.5f, popupBgPaint)

                popupTextPaint.textSize = 24f * resources.displayMetrics.scaledDensity
                canvas.drawText(key.label, popupRect.centerX(), popupRect.centerY() + 10f, popupTextPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchedKey = findKeyAt(x, y)
                if (touchedKey != null) {
                    activePressedKey = touchedKey
                    pressedKeyRect = touchedKey.rect
                    triggerHaptic()
                    handleKeyPressDown(touchedKey)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val currentKey = findKeyAt(x, y)
                if (currentKey != activePressedKey) {
                    if (activePressedKey?.type == KeyType.BACKSPACE) {
                        stopBackspaceRepeat()
                    }
                    activePressedKey = currentKey
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                activePressedKey?.let { key ->
                    handleKeyPressUp(key)
                }
                stopBackspaceRepeat()
                activePressedKey = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                stopBackspaceRepeat()
                activePressedKey = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyAt(x: Float, y: Float): Key? {
        for (row in keyRows) {
            for (key in row) {
                if (key.rect.contains(x, y)) {
                    return key
                }
            }
        }
        return null
    }

    private fun handleKeyPressDown(key: Key) {
        if (key.type == KeyType.BACKSPACE) {
            listener?.onBackspace()
            isBackspaceHolding = true
            handler.postDelayed(backspaceRepeatRunnable, 350)
        }
    }

    private fun handleKeyPressUp(key: Key) {
        when (key.type) {
            KeyType.CHARACTER -> {
                listener?.onKeyTyped(key.code, key.label)
                if (keyboardMode == KeyboardMode.UPPERCASE) {
                    keyboardMode = KeyboardMode.LOWERCASE
                }
            }

            KeyType.SPACE -> listener?.onSpace()
            KeyType.ENTER -> listener?.onEnter()

            KeyType.BACKSPACE -> {
                // First stroke handled in DOWN, repeat stopped in UP
            }

            KeyType.SHIFT -> {
                keyboardMode = when (keyboardMode) {
                    KeyboardMode.LOWERCASE -> KeyboardMode.UPPERCASE
                    KeyboardMode.UPPERCASE -> KeyboardMode.CAPS_LOCK
                    KeyboardMode.CAPS_LOCK -> KeyboardMode.LOWERCASE
                    else -> KeyboardMode.LOWERCASE
                }
                buildLayouts(width, height)
            }

            KeyType.MODE_SWITCH -> {
                keyboardMode = when (key.label) {
                    "?123", "123" -> KeyboardMode.NUMBERS
                    "=/<" -> KeyboardMode.SYMBOLS
                    "ABC" -> KeyboardMode.LOWERCASE
                    else -> KeyboardMode.LOWERCASE
                }
                buildLayouts(width, height)
            }

            KeyType.METHOD_SWITCH -> {
                currentMethod = if (currentMethod == ViEngine.METHOD_TELEX) ViEngine.METHOD_VNI else ViEngine.METHOD_TELEX
                buildLayouts(width, height)
            }

            KeyType.ACTION -> {}
        }
    }

    private fun stopBackspaceRepeat() {
        isBackspaceHolding = false
        handler.removeCallbacks(backspaceRepeatRunnable)
    }

    private fun triggerHaptic() {
        if (!enableHapticFeedback) return
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // Haptic fallback for older API levels
        }
    }
}
