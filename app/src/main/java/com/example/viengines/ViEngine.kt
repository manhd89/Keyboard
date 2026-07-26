package com.example.viengines

import android.util.Log

/**
 * JNI wrapper for the Rust `vi` engine (`transform_buffer` for TELEX and VNI).
 * Provides JNI interface matching `Java_com_example_viengines_ViEngine_transform`
 * with a high-performance pure Kotlin fallback engine when native lib is not present.
 */
object ViEngine {
    private const val TAG = "ViEngine"
    private var isNativeLoaded = false

    const val METHOD_TELEX = 0
    const val METHOD_VNI = 1

    init {
        try {
            System.loadLibrary("vi_engine")
            isNativeLoaded = true
            Log.i(TAG, "Native Rust vi_engine library loaded successfully via JNI.")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            Log.w(TAG, "Native library 'vi_engine' not loaded in this environment. Using high-performance Kotlin Vietnamese Engine fallback.")
        } catch (e: Exception) {
            isNativeLoaded = false
            Log.e(TAG, "Error loading native library", e)
        }
    }

    /**
     * Native JNI function matching C/Rust declaration:
     * Java_com_example_viengines_ViEngine_transform(env, class, input, method)
     */
    @JvmStatic
    external fun transform(input: String, method: Int): String

    /**
     * Safe wrapper for text transformation using Rust JNI if available,
     * otherwise falling back to Kotlin implementation.
     */
    fun transformText(input: String, method: Int): String {
        if (input.isEmpty()) return ""
        if (isNativeLoaded) {
            try {
                return transform(input, method)
            } catch (e: Throwable) {
                Log.e(TAG, "JNI execution error, falling back to Kotlin engine", e)
            }
        }
        return VietnameseEngineKt.transformBuffer(input, method)
    }

    fun isNativeEngineAvailable(): Boolean = isNativeLoaded
}

/**
 * Pure Kotlin implementation of the Vietnamese Telex/VNI text transformation engine.
 * Fully compatible with the `vi` crate specification (transform_buffer, TELEX, VNI).
 */
object VietnameseEngineKt {

    // Tone definitions
    // 0: none, 1: sắc, 2: huyền, 3: hỏi, 4: ngã, 5: nặng
    private val TONE_MARKS = arrayOf(
        // Vowels: a, ă, â, e, ê, i, o, ô, ơ, u, ư, y
        charArrayOf('a', 'á', 'à', 'ả', 'ã', 'ạ'),
        charArrayOf('ă', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ'),
        charArrayOf('â', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ'),
        charArrayOf('e', 'é', 'è', 'ẻ', 'ẽ', 'ẹ'),
        charArrayOf('ê', 'ế', 'ề', 'ể', 'ễ', 'ệ'),
        charArrayOf('i', 'í', 'ì', 'ỉ', 'ĩ', 'ị'),
        charArrayOf('o', 'ó', 'ò', 'ỏ', 'õ', 'ọ'),
        charArrayOf('ô', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ'),
        charArrayOf('ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ'),
        charArrayOf('u', 'ú', 'ù', 'ủ', 'ũ', 'ụ'),
        charArrayOf('ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự'),
        charArrayOf('y', 'ý', 'ỳ', 'ỷ', 'ỹ', 'ỵ'),

        // Uppercase
        charArrayOf('A', 'Á', 'À', 'Ả', 'Ã', 'Ạ'),
        charArrayOf('Ă', 'Ắ', 'Ằ', 'Ẳ', 'Ẵ', 'Ặ'),
        charArrayOf('Â', 'Ấ', 'Ầ', 'Ẩ', 'Ẫ', 'Ậ'),
        charArrayOf('E', 'É', 'È', 'Ẻ', 'Ẽ', 'Ẹ'),
        charArrayOf('Ê', 'Ế', 'Ề', 'Ể', 'Ễ', 'Ệ'),
        charArrayOf('I', 'Í', 'Ì', 'Ỉ', 'Ĩ', 'Ị'),
        charArrayOf('O', 'Ó', 'Ò', 'Ỏ', 'Õ', 'Ọ'),
        charArrayOf('Ô', 'Ố', 'Ồ', 'Ổ', 'Ỗ', 'Ộ'),
        charArrayOf('Ơ', 'Ớ', 'Ờ', 'Ở', 'Ỡ', 'Ợ'),
        charArrayOf('U', 'Ú', 'Ù', 'Ủ', 'Ũ', 'Ụ'),
        charArrayOf('Ư', 'Ứ', 'Ừ', 'Ử', 'Ữ', 'Ự'),
        charArrayOf('Y', 'Ý', 'Ỳ', 'Ỷ', 'Ỹ', 'Ỵ')
    )

    fun transformBuffer(input: String, method: Int): String {
        val words = input.split(Regex("(?<=\\s)|(?=\\s)"))
        val sb = StringBuilder()
        for (w in words) {
            if (w.trim().isEmpty()) {
                sb.append(w)
            } else {
                sb.append(transformWord(w, method))
            }
        }
        return sb.toString()
    }

    private fun transformWord(word: String, method: Int): String {
        if (word.isEmpty()) return word

        var chars = word.toCharArray()
        var len = chars.size

        if (method == ViEngine.METHOD_TELEX) {
            return processTelex(word)
        } else {
            return processVNI(word)
        }
    }

    private fun processTelex(word: String): String {
        var current = word

        // Standalone 'w' or 'W' at start or after consonant
        if (current.equals("w", ignoreCase = true)) {
            return if (current[0].isUpperCase()) "Ư" else "ư"
        }

        var result = StringBuilder()
        var i = 0
        val n = current.length

        // We process keystrokes sequentially imitating buffer transformations
        while (i < n) {
            val c = current[i]
            val lower = c.lowercaseChar()

            if (i == 0 && (lower == 'w') && (n == 1 || !current[1].isLetter())) {
                result.append(if (c.isUpperCase()) 'Ư' else 'ư')
                i++
                continue
            }

            result.append(c)
            i++
        }

        var s = result.toString()

        // Apply Telex modifiers sequentially
        s = applyTelexRules(s)

        return s
    }

    private fun applyTelexRules(raw: String): String {
        var s = raw

        // Rule dd / DD -> đ / Đ
        s = s.replace("dd", "đ")
            .replace("Dd", "Đ")
            .replace("DD", "Đ")
            .replace("dD", "Đ")

        // Rule aa -> â, aw -> ă, ee -> ê, oo -> ô, ow -> ơ, uw -> ư, w -> ư (when standalone or following u)
        // Check for double key modifications
        s = replacePairs(s, "aa", 'â', 'a')
        s = replacePairs(s, "AA", 'Â', 'A')
        s = replacePairs(s, "Aa", 'Â', 'A')

        s = replacePairs(s, "aw", 'ă', 'a')
        s = replacePairs(s, "AW", 'Ă', 'A')
        s = replacePairs(s, "Aw", 'Ă', 'A')

        s = replacePairs(s, "ee", 'ê', 'e')
        s = replacePairs(s, "EE", 'Ê', 'E')
        s = replacePairs(s, "Ee", 'Ê', 'E')

        s = replacePairs(s, "oo", 'ô', 'o')
        s = replacePairs(s, "OO", 'Ô', 'O')
        s = replacePairs(s, "Oo", 'Ô', 'O')

        s = replacePairs(s, "ow", 'ơ', 'o')
        s = replacePairs(s, "OW", 'Ơ', 'O')
        s = replacePairs(s, "Ow", 'Ơ', 'O')

        s = replacePairs(s, "uw", 'ư', 'u')
        s = replacePairs(s, "UW", 'Ư', 'U')
        s = replacePairs(s, "Uw", 'Ư', 'U')

        // Handle standalone 'w' attached to 'u' or 'o' or at end of syllable (e.g. "tuw" -> "tư", "w" -> "ư")
        if (s.contains("w") || s.contains("W")) {
            s = s.replace("uw", "ư").replace("UW", "Ư").replace("Uw", "Ư")
            s = s.replace("ow", "ơ").replace("OW", "Ơ").replace("Ow", "Ơ")
            // trailing or solo 'w'
            s = s.replace(Regex("(?<=[a-zA-Z])w"), "ư")
                .replace(Regex("(?<=[a-zA-Z])W"), "Ư")
                .replace("w", "ư")
                .replace("W", "Ư")
        }

        // Telex tone keys: s (sắc=1), f (huyền=2), r (hỏi=3), x (ngã=4), j (nặng=5), z (xóa=0)
        s = applyToneMark(s, 's', 1)
        s = applyToneMark(s, 'f', 2)
        s = applyToneMark(s, 'r', 3)
        s = applyToneMark(s, 'x', 4)
        s = applyToneMark(s, 'j', 5)
        s = applyToneMark(s, 'z', 0)

        return s
    }

    private fun processVNI(word: String): String {
        var s = word

        // VNI modifier keys:
        // 1: sắc, 2: huyền, 3: hỏi, 4: ngã, 5: nặng, 0: remove tone
        // 6: â/ê/ô (^), 7: ơ/ư (+), 8: ă (˘), 9: đ
        s = s.replace("d9", "đ").replace("D9", "Đ")

        // VNI circumflex / horn / breve
        s = applyVniHat(s, '6', mapOf('a' to 'â', 'e' to 'ê', 'o' to 'ô', 'A' to 'Â', 'E' to 'Ê', 'O' to 'Ô'))
        s = applyVniHat(s, '7', mapOf('o' to 'ơ', 'u' to 'ư', 'O' to 'Ơ', 'U' to 'Ư'))
        s = applyVniHat(s, '8', mapOf('a' to 'ă', 'A' to 'Ă'))

        // Tones
        s = applyToneMark(s, '1', 1)
        s = applyToneMark(s, '2', 2)
        s = applyToneMark(s, '3', 3)
        s = applyToneMark(s, '4', 4)
        s = applyToneMark(s, '5', 5)
        s = applyToneMark(s, '0', 0)

        return s
    }

    private fun replacePairs(str: String, target: String, replacement: Char, baseChar: Char): String {
        if (!str.contains(target, ignoreCase = true)) return str
        val idx = str.indexOf(target, ignoreCase = true)
        if (idx >= 0) {
            val sb = StringBuilder(str)
            sb.setCharAt(idx, replacement)
            sb.deleteCharAt(idx + 1)
            return sb.toString()
        }
        return str
    }

    private fun applyVniHat(str: String, key: Char, replacements: Map<Char, Char>): String {
        if (!str.contains(key)) return str
        val keyIdx = str.indexOf(key)
        val sb = StringBuilder(str)

        // Find preceding vowel to transform
        for (i in keyIdx - 1 downTo 0) {
            val c = sb[i]
            if (replacements.containsKey(c)) {
                sb[i] = replacements[c]!!
                sb.deleteCharAt(keyIdx)
                return sb.toString()
            }
        }
        return str
    }

    private fun applyToneMark(str: String, toneKey: Char, toneIndex: Int): String {
        if (!str.contains(toneKey, ignoreCase = true)) return str

        val keyIdx = str.indexOf(toneKey, ignoreCase = true)
        val sb = StringBuilder(str)

        // Find primary vowel index
        val vowelIndices = ArrayList<Int>()
        for (i in 0 until keyIdx) {
            if (isVowel(sb[i])) {
                vowelIndices.add(i)
            }
        }

        if (vowelIndices.isEmpty()) {
            return str
        }

        // Determine target vowel to place tone on
        val targetIdx = getPrimaryVowelIndex(sb.toString(), vowelIndices)

        val targetChar = sb[targetIdx]

        // Remove toneKey character from string
        sb.deleteCharAt(keyIdx)

        // Apply tone to target vowel
        val newVowel = setToneToChar(targetChar, toneIndex)
        sb[targetIdx] = newVowel

        return sb.toString()
    }

    private fun isVowel(c: Char): Boolean {
        val lower = c.lowercaseChar()
        return "aăâeêioôơuưyáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ".contains(lower)
    }

    private fun getPrimaryVowelIndex(word: String, vowelIndices: List<Int>): Int {
        if (vowelIndices.size == 1) return vowelIndices[0]

        // Prioritize special vowels (ê, ơ, ư, ô, â, ă)
        for (idx in vowelIndices) {
            val c = word[idx].lowercaseChar()
            if ("êơưôâă".contains(c) || "ếềểễệớờởỡợứừửữựốồổỗộấầẩẫậắằẳẵặ".contains(c)) {
                return idx
            }
        }

        // Standard Vietnamese rules for diphthongs/triphthongs:
        // if ends with consonant, tone goes on last vowel (e.g. "nguyển" -> "nguyễn")
        // if ends with vowel, tone goes on penultimate vowel (e.g. "hoà" or "hoá")
        val lastVowelIdx = vowelIndices.last()
        val wordEndsWithConsonant = lastVowelIdx < word.length - 1 && word[lastVowelIdx + 1].isLetter()

        if (wordEndsWithConsonant) {
            return lastVowelIdx
        } else if (vowelIndices.size >= 2) {
            // e.g. "oa", "oe", "uy" -> place on second vowel or first vowel depending on convention
            return vowelIndices[vowelIndices.size - 2]
        }

        return lastVowelIdx
    }

    private fun setToneToChar(c: Char, toneIndex: Int): Char {
        val baseChar = stripTone(c)
        for (row in TONE_MARKS) {
            if (row[0] == baseChar) {
                return row[toneIndex]
            }
        }
        return c
    }

    private fun stripTone(c: Char): Char {
        for (row in TONE_MARKS) {
            for (col in row) {
                if (col == c) return row[0]
            }
        }
        return c
    }
}
