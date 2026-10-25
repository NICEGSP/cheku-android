package com.cheku.app

import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test

class InputSanitizerTest {
    @Test
    fun numericInputsRejectIllegalCharactersAndRanges() {
        assertEquals("123", sanitizeTextFieldInput("-1a2 3", KeyboardType.Number, null))
        assertEquals("12.3456", sanitizeTextFieldInput("12..3a4567", KeyboardType.Decimal, null))
        assertEquals("100", sanitizeTextFieldInput("101", KeyboardType.Number, 100.0))
    }

    @Test
    fun textInputsStaySingleLineAndBounded() {
        assertEquals("第一行第二行", sanitizeTextFieldInput("第一行\n第二行", KeyboardType.Text, null))
        assertEquals(120, sanitizeTextFieldInput("a".repeat(200), KeyboardType.Text, null).length)
    }
}
