package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

/**
 * Custom Keyboard View rendered ENTIRELY using Android Canvas & View.
 * Designed with a modern, high-end commercial aesthetic (Apple / Linear / Raycast / Notion style).
 * Features precise touch target calculations, subtle key elevation strokes, haptic feedback,
 * and high-performance Telex & VNI input handling.
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
        val keyBorderColor: Int,
        val keySpecialBgColor: Int,
        val keyPressedColor: Int,
        val textColor: Int,
        val textSpecialColor: Int,
        val accentColor: Int,
        val accentTextColor: Int,
        val popupBgColor: Int,
        val popupTextColor: Int,
        val keyCornerRadiusDp: Float = 7f
    ) {
        companion object {
            // Linear Dark Minimal Theme
            val DARK_SLATE = KeyboardTheme(
                name = "Dark Slate",
                backgroundColor = Color.parseColor("#121316"),
                candidateBarBg = Color.parseColor("#181A1F"),
                keyBgColor = Color.parseColor("#22252C"),
                keyBorderColor = Color.parseColor("#2C3038"),
                keySpecialBgColor = Color.parseColor("#1B1D23"),
                keyPressedColor = Color.parseColor("#323742"),
                textColor = Color.parseColor("#F1F5F9"),
                textSpecialColor = Color.parseColor("#94A3B8"),
                accentColor = Color.parseColor("#38BDF8"),
                accentTextColor = Color.parseColor("#090D16"),
                popupBgColor = Color.parseColor("#2A2E38"),
                popupTextColor = Color.parseColor("#FFFFFF")
            )

            // Apple iOS Light Minimal Theme
            val APPLE_LIGHT = KeyboardTheme(
                name = "Apple Light",
                backgroundColor = Color.parseColor("#D1D5DB"),
                candidateBarBg = Color.parseColor("#E5E7EB"),
                keyBgColor = Color.parseColor("#FFFFFF"),
                keyBorderColor = Color.parseColor("#E5E7EB"),
                keySpecialBgColor = Color.parseColor("#BFC5CE"),
                keyPressedColor = Color.parseColor("#D1D5DB"),
                textColor = Color.parseColor("#111827"),
                textSpecialColor = Color.parseColor("#374151"),
                accentColor = Color.parseColor("#2563EB"),
                accentTextColor = Color.parseColor("#FFFFFF"),
                popupBgColor = Color.parseColor("#FFFFFF"),
                popupTextColor = Color.parseColor("#111827")
            )

            // Raycast Charcoal Mono Dark Theme
            val CHARCOAL_MONO = KeyboardTheme(
                name = "Charcoal Mono",
                backgroundColor = Color.parseColor("#090A0C"),
                candidateBarBg = Color.parseColor("#111215"),
                keyBgColor = Color.parseColor("#1A1C20"),
                keyBorderColor = Color.parseColor("#262930"),
                keySpecialBgColor = Color.parseColor("#131417"),
                keyPressedColor = Color.parseColor("#2E323A"),
                textColor = Color.parseColor("#F8FAFC"),
                textSpecialColor = Color.parseColor("#A1A1AA"),
                accentColor = Color.parseColor("#F8FAFC"),
                accentTextColor = Color.parseColor("#090A0C"),
                popupBgColor = Color.parseColor("#22252B"),
                popupTextColor = Color.parseColor("#FFFFFF")
            )

            // Notion Warm Cream Theme
            val WARM_CANVAS = KeyboardTheme(
                name = "Warm Canvas",
                backgroundColor = Color.parseColor("#F5F4F0"),
                candidateBarBg = Color.parseColor("#EBE9E3"),
                keyBgColor = Color.parseColor("#FFFFFF"),
                keyBorderColor = Color.parseColor("#E3E1D9"),
                keySpecialBgColor = Color.parseColor("#E6E4DD"),
                keyPressedColor = Color.parseColor("#D8D5CC"),
                textColor = Color.parseColor("#292524"),
                textSpecialColor = Color.parseColor("#57534E"),
                accentColor = Color.parseColor("#44403C"),
                accentTextColor = Color.parseColor("#FAFAF9"),
                popupBgColor = Color.parseColor("#292524"),
                popupTextColor = Color.parseColor("#FAFAF9")
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
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySpecialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPressedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySpecialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candidateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

        keyBorderPaint.color = theme.keyBorderColor
        keyBorderPaint.style = Paint.Style.STROKE
        keyBorderPaint.strokeWidth = 1f * resources.displayMetrics.density

        keySpecialBgPaint.color = theme.keySpecialBgColor
        keySpecialBgPaint.style = Paint.Style.FILL

        keyPressedBgPaint.color = theme.keyPressedColor
        keyPressedBgPaint.style = Paint.Style.FILL

        keyTextPaint.color = theme.textColor
        keyTextPaint.style = Paint.Style.FILL
        keyTextPaint.textAlign = Paint.Align.CENTER
        keyTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        keySpecialTextPaint.color = theme.textSpecialColor
        keySpecialTextPaint.style = Paint.Style.FILL
        keySpecialTextPaint.textAlign = Paint.Align.CENTER
        keySpecialTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        accentPaint.color = theme.accentColor
        accentPaint.style = Paint.Style.FILL

        accentTextPaint.color = theme.accentTextColor
        accentTextPaint.style = Paint.Style.FILL
        accentTextPaint.textAlign = Paint.Align.CENTER
        accentTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        popupBgPaint.color = theme.popupBgColor
        popupBgPaint.style = Paint.Style.FILL

        popupTextPaint.color = theme.popupTextColor
        popupTextPaint.style = Paint.Style.FILL
        popupTextPaint.textAlign = Paint.Align.CENTER
        popupTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        candidateTextPaint.color = theme.textColor
        candidateTextPaint.style = Paint.Style.FILL
        candidateTextPaint.textAlign = Paint.Align.LEFT
        candidateTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        borderLinePaint.color = theme.keyBorderColor
        borderLinePaint.style = Paint.Style.STROKE
        borderLinePaint.strokeWidth = 1f * resources.displayMetrics.density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (260 * resources.displayMetrics.density).toInt()
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
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

        val availableHeight = totalHeight.toFloat()
        val rawRows = when (keyboardMode) {
            KeyboardMode.LOWERCASE -> getQwertyRows(uppercase = false)
            KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCK -> getQwertyRows(uppercase = true)
            KeyboardMode.NUMBERS -> getNumberRows()
            KeyboardMode.SYMBOLS -> getSymbolRows()
        }

        val rowCount = rawRows.size
        val rowHeight = availableHeight / rowCount
        val keyMarginHorizontal = 3.5f * resources.displayMetrics.density
        val keyMarginVertical = 3.5f * resources.displayMetrics.density

        var currentY = 0f

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
        val r0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            Key(it[0].code, it)
        }

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
            Key(-3, "?123", KeyType.MODE_SWITCH, weight = 1.2f),
            Key(-4, methodLabel, KeyType.METHOD_SWITCH, weight = 1.2f),
            Key(','.code, ",", KeyType.CHARACTER, weight = 0.9f),
            Key(32, "Tiếng Việt", KeyType.SPACE, weight = 3.2f),
            Key('.'.code, ".", KeyType.CHARACTER, weight = 0.9f),
            Key(10, "Nhập ↵", KeyType.ENTER, weight = 1.4f)
        )

        return listOf(r0, r1, r2, r3, r4)
    }

    private fun getNumberRows(): List<List<Key>> {
        val r0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { Key(it[0].code, it) }
        val r1 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { Key(it[0].code, it) }
        val r2 = listOf("*", "\"", "'", ":", ";", "!", "?", "\\", "%").map { Key(it[0].code, it) }

        val r3 = mutableListOf<Key>().apply {
            add(Key(-5, "=/<", KeyType.MODE_SWITCH, weight = 1.4f))
            listOf("<", ">", "[", "]", "{", "}", "~").forEach { add(Key(it[0].code, it)) }
            add(Key(-2, "⌫", KeyType.BACKSPACE, weight = 1.4f))
        }

        val methodLabel = if (currentMethod == ViEngine.METHOD_TELEX) "TELEX" else "VNI"

        val r4 = listOf(
            Key(-6, "ABC", KeyType.MODE_SWITCH, weight = 1.2f),
            Key(-4, methodLabel, KeyType.METHOD_SWITCH, weight = 1.2f),
            Key(','.code, ",", KeyType.CHARACTER, weight = 0.9f),
            Key(32, "Dấu cách", KeyType.SPACE, weight = 3.2f),
            Key('.'.code, ".", KeyType.CHARACTER, weight = 0.9f),
            Key(10, "Hoàn tất", KeyType.ENTER, weight = 1.4f)
        )

        return listOf(r0, r1, r2, r3, r4)
    }

    private fun getSymbolRows(): List<List<Key>> {
        val r0 = listOf("^", "°", "=", "•", "\\", "|", "~", "`", "÷", "×").map { Key(it[0].code, it) }
        val r1 = listOf("£", "¥", "€", "¢", "₹", "§", "¶", "∆", "√", "π").map { Key(it[0].code, it) }
        val r2 = listOf("©", "®", "™", "✓", "[", "]", "{", "}", "¡", "¿").map { Key(it[0].code, it) }

        val r3 = mutableListOf<Key>().apply {
            add(Key(-3, "123", KeyType.MODE_SWITCH, weight = 1.4f))
            listOf("<", ">", "«", "»", "°", "±", "…").forEach { add(Key(it[0].code, it)) }
            add(Key(-2, "⌫", KeyType.BACKSPACE, weight = 1.4f))
        }

        val methodLabel = if (currentMethod == ViEngine.METHOD_TELEX) "TELEX" else "VNI"

        val r4 = listOf(
            Key(-6, "ABC", KeyType.MODE_SWITCH, weight = 1.2f),
            Key(-4, methodLabel, KeyType.METHOD_SWITCH, weight = 1.2f),
            Key(','.code, ",", KeyType.CHARACTER, weight = 0.9f),
            Key(32, "Dấu cách", KeyType.SPACE, weight = 3.2f),
            Key('.'.code, ".", KeyType.CHARACTER, weight = 0.9f),
            Key(10, "Hoàn tất", KeyType.ENTER, weight = 1.4f)
        )

        return listOf(r0, r1, r2, r3, r4)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw overall keyboard canvas background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw keys
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

                // Draw key background
                canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, paintToUse)

                // Draw subtle key border stroke for sharpness
                if (!isPressed) {
                    canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, keyBorderPaint)
                }

                // Highlight active Shift / Enter / Special keys
                if (key.type == KeyType.SHIFT && (keyboardMode == KeyboardMode.UPPERCASE || keyboardMode == KeyboardMode.CAPS_LOCK)) {
                    canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, accentPaint)
                } else if (key.type == KeyType.ENTER) {
                    canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, accentPaint)
                }

                // Select text paint and text size
                val textPaintToUse = when {
                    key.type == KeyType.SHIFT && (keyboardMode == KeyboardMode.UPPERCASE || keyboardMode == KeyboardMode.CAPS_LOCK) -> accentTextPaint
                    key.type == KeyType.ENTER -> accentTextPaint
                    isSpecial -> keySpecialTextPaint
                    else -> keyTextPaint
                }

                textPaintToUse.textSize = when (key.type) {
                    KeyType.SPACE -> 13f * resources.displayMetrics.scaledDensity
                    KeyType.ENTER, KeyType.MODE_SWITCH, KeyType.METHOD_SWITCH -> 12.5f * resources.displayMetrics.scaledDensity
                    else -> 17.5f * resources.displayMetrics.scaledDensity
                }

                val centerY = key.rect.centerY() + (textPaintToUse.textSize / 3f)
                canvas.drawText(key.label, key.rect.centerX(), centerY, textPaintToUse)
            }
        }

        // 3. Key tap preview popup
        activePressedKey?.let { key ->
            if (key.type == KeyType.CHARACTER) {
                val popupWidth = key.rect.width() * 1.25f
                val popupHeight = key.rect.height() * 1.15f
                val popupX = key.rect.centerX() - (popupWidth / 2f)
                val popupY = key.rect.top - popupHeight - 6f

                val popupRect = RectF(popupX, popupY, popupX + popupWidth, popupY + popupHeight)
                canvas.drawRoundRect(popupRect, cornerRadius * 1.3f, cornerRadius * 1.3f, popupBgPaint)

                popupTextPaint.textSize = 22f * resources.displayMetrics.scaledDensity
                canvas.drawText(key.label, popupRect.centerX(), popupRect.centerY() + 8f, popupTextPaint)
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

            KeyType.BACKSPACE -> {}

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
            vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // Haptic fallback
        }
    }
}
