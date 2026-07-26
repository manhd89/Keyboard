package com.example

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.viengines.ViEngine

/**
 * Android Input Method Editor (IME) Service for Vietnamese Typing (Telex & VNI).
 * Manages InputConnection, active word buffer, and JNI/Kotlin text transformation.
 */
class VietnameseIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private val composingBuffer = StringBuilder()
    private var currentMethod = ViEngine.METHOD_TELEX

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this).apply {
            listener = this@VietnameseIME
            currentMethod = this@VietnameseIME.currentMethod
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (280 * resources.displayMetrics.density).toInt()
            )
        }
        return keyboardView
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingBuffer.clear()
        if (::keyboardView.isInitialized) {
            keyboardView.currentComposingText = ""
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composingBuffer.clear()
        if (::keyboardView.isInitialized) {
            keyboardView.currentComposingText = ""

            // Adjust keyboard layout according to input type
            info?.let { editorInfo ->
                val inputType = editorInfo.inputType
                val variation = inputType and InputType.TYPE_MASK_VARIATION

                if (variation == InputType.TYPE_CLASS_NUMBER || variation == InputType.TYPE_NUMBER_FLAG_DECIMAL) {
                    keyboardView.keyboardMode = KeyboardView.KeyboardMode.NUMBERS
                } else if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                    keyboardView.keyboardMode = KeyboardView.KeyboardMode.CAPS_LOCK
                } else if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0 || (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                    keyboardView.keyboardMode = KeyboardView.KeyboardMode.UPPERCASE
                } else {
                    keyboardView.keyboardMode = KeyboardView.KeyboardMode.LOWERCASE
                }
            }
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        commitComposingText()
    }

    // --- KeyboardView.OnKeyboardActionListener implementation ---

    override fun onKeyTyped(code: Int, label: String) {
        val ic: InputConnection = currentInputConnection ?: return

        // Punctuation characters commit current word first
        if (label.length == 1 && isPunctuation(label[0])) {
            commitComposingText()
            ic.commitText(label, 1)
            return
        }

        // Append typed character to uncommitted word buffer
        composingBuffer.append(label)

        // Transform buffer using JNI / Kotlin Vietnamese Engine
        val transformedText = ViEngine.transformText(composingBuffer.toString(), currentMethod)

        // Update active composing text in the target text view
        ic.setComposingText(transformedText, 1)
        keyboardView.currentComposingText = transformedText
    }

    override fun onBackspace() {
        val ic: InputConnection = currentInputConnection ?: return

        if (composingBuffer.isNotEmpty()) {
            composingBuffer.deleteCharAt(composingBuffer.length - 1)

            if (composingBuffer.isEmpty()) {
                ic.setComposingText("", 1)
                keyboardView.currentComposingText = ""
            } else {
                val transformedText = ViEngine.transformText(composingBuffer.toString(), currentMethod)
                ic.setComposingText(transformedText, 1)
                keyboardView.currentComposingText = transformedText
            }
        } else {
            // No active word buffer, delete surrounding character from field
            ic.deleteSurroundingText(1, 0)
        }
    }

    override fun onSpace() {
        val ic: InputConnection = currentInputConnection ?: return

        if (composingBuffer.isNotEmpty()) {
            val transformedText = ViEngine.transformText(composingBuffer.toString(), currentMethod)
            ic.commitText("$transformedText ", 1)
            composingBuffer.clear()
            keyboardView.currentComposingText = ""
        } else {
            ic.commitText(" ", 1)
        }
    }

    override fun onEnter() {
        val ic: InputConnection = currentInputConnection ?: return

        commitComposingText()

        val editorInfo = currentInputEditorInfo
        if (editorInfo != null && editorInfo.actionId != 0) {
            ic.performEditorAction(editorInfo.actionId)
        } else {
            sendKeyChar('\n')
        }
    }

    override fun onMethodChanged(method: Int) {
        currentMethod = method
        if (composingBuffer.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            val transformedText = ViEngine.transformText(composingBuffer.toString(), currentMethod)
            ic.setComposingText(transformedText, 1)
            keyboardView.currentComposingText = transformedText
        }
    }

    private fun commitComposingText() {
        if (composingBuffer.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            val transformedText = ViEngine.transformText(composingBuffer.toString(), currentMethod)
            ic.commitText(transformedText, 1)
            composingBuffer.clear()
            if (::keyboardView.isInitialized) {
                keyboardView.currentComposingText = ""
            }
        }
    }

    private fun isPunctuation(c: Char): Boolean {
        return ",.?!:;()[]{}<>/\\@#$%^&*-+=\"'~`".contains(c)
    }
}
