package io.github.antonudovychenko.bugreport.internal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutsTest {
    @Test
    fun formIsUnmountedThroughoutTheEditorTransition() {
        assertTrue(shouldShowForm(isOpen = true, pendingEditingIndex = null, editingIndex = null))
        assertFalse(shouldShowForm(isOpen = true, pendingEditingIndex = 0, editingIndex = null))
        assertFalse(shouldShowForm(isOpen = true, pendingEditingIndex = null, editingIndex = 0))
        assertFalse(shouldShowForm(isOpen = false, pendingEditingIndex = null, editingIndex = null))
    }

    @Test
    fun openingTheEditorDropsTheKeyboardBeforeSelectingTheImage() {
        val events = mutableListOf<String>()
        var selected: Int? = null

        openImageEditor(
            index = 2,
            clearFocus = { events += "clear focus" },
            hideKeyboard = { events += "hide keyboard" },
            selectImage = {
                events += "select image"
                selected = it
            },
        )

        assertEquals(listOf("clear focus", "hide keyboard", "select image"), events)
        assertEquals(2, selected)
    }

    @Test
    fun commandOrControlEnterSubmitsOnKeyDownOnly() {
        assertTrue(isSubmitShortcut(Key.Enter, KeyEventType.KeyDown, isMetaPressed = true, isCtrlPressed = false))
        assertTrue(isSubmitShortcut(Key.Enter, KeyEventType.KeyDown, isMetaPressed = false, isCtrlPressed = true))
        assertFalse(isSubmitShortcut(Key.Enter, KeyEventType.KeyUp, isMetaPressed = true, isCtrlPressed = false))
    }

    @Test
    fun plainEnterStaysANewline() {
        assertFalse(isSubmitShortcut(Key.Enter, KeyEventType.KeyDown, isMetaPressed = false, isCtrlPressed = false))
        assertFalse(isSubmitShortcut(Key.K, KeyEventType.KeyDown, isMetaPressed = true, isCtrlPressed = false))
    }

    @Test
    fun formattingShortcutsNeedAModifierAndAKeyDown() {
        assertEquals(FormattingShortcut.Bold, formattingShortcut(Key.B, KeyEventType.KeyDown, true, false))
        assertEquals(FormattingShortcut.Italic, formattingShortcut(Key.I, KeyEventType.KeyDown, false, true))
        assertEquals(FormattingShortcut.Link, formattingShortcut(Key.K, KeyEventType.KeyDown, true, false))
        assertNull(formattingShortcut(Key.B, KeyEventType.KeyDown, false, false))
        assertNull(formattingShortcut(Key.B, KeyEventType.KeyUp, true, false))
        assertNull(formattingShortcut(Key.Enter, KeyEventType.KeyDown, true, false))
    }

    @Test
    fun linksWithoutASchemeGetHttps() {
        assertEquals("https://example.com", normalizeLinkUrl(" example.com "))
        assertEquals("http://localhost:8080", normalizeLinkUrl("http://localhost:8080"))
        assertEquals("mailto:me@example.com", normalizeLinkUrl("mailto:me@example.com"))
    }
}
