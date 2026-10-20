package io.github.antonudovychenko.bugreport.internal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor

private val ToolbarButtonSize = 40.dp
private val ToolbarIconSize = 20.dp

/**
 * A bordered rich-text field whose content is Markdown, with a formatting toolbar underneath.
 * Shortcuts: Cmd/Ctrl+B bold, Cmd/Ctrl+I italic, Cmd/Ctrl+K link (with a selection).
 */
@Composable
internal fun MarkdownField(
    state: RichTextState,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    minLines: Int = 5,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme

    // The link dialog is hoisted here so the toolbar button and Cmd/Ctrl+K share it. Opening it
    // blurs the editor, which collapses its selection, so the range is captured up front.
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkSelection by remember { mutableStateOf(TextRange.Zero) }
    val focusManager = LocalFocusManager.current
    val openLinkDialog = {
        linkSelection = state.selection
        focusManager.clearFocus(force = true)
        showLinkDialog = true
    }

    Column(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) colors.primary else colors.outline), shape),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (state.annotatedString.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            BasicRichTextEditor(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        when (formattingShortcut(event)) {
                            FormattingShortcut.Bold -> state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            FormattingShortcut.Italic -> state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            FormattingShortcut.Link ->
                                if (!state.selection.collapsed) openLinkDialog() else return@onPreviewKeyEvent false
                            null -> return@onPreviewKeyEvent false
                        }
                        true
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
                singleLine = false,
                minLines = minLines,
                interactionSource = interactionSource,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        FormattingToolbar(state = state, onInsertLinkClick = openLinkDialog)
    }

    if (showLinkDialog) {
        LinkDialog(state = state, initialSelection = linkSelection, onDismiss = { showLinkDialog = false })
    }
}

internal enum class FormattingShortcut { Bold, Italic, Link }

internal fun formattingShortcut(event: KeyEvent): FormattingShortcut? =
    formattingShortcut(event.key, event.type, event.isMetaPressed, event.isCtrlPressed)

internal fun formattingShortcut(
    key: Key,
    eventType: KeyEventType,
    isMetaPressed: Boolean,
    isCtrlPressed: Boolean,
): FormattingShortcut? {
    if (eventType != KeyEventType.KeyDown || !(isMetaPressed || isCtrlPressed)) return null
    return when (key) {
        Key.B -> FormattingShortcut.Bold
        Key.I -> FormattingShortcut.Italic
        Key.K -> FormattingShortcut.Link
        else -> null
    }
}

@Composable
private fun FormattingToolbar(state: RichTextState, onInsertLinkClick: () -> Unit) {
    val span = state.currentSpanStyle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarToggle(
            icon = Icons.Default.FormatBold,
            description = bugReportStrings().bold,
            selected = span.fontWeight == FontWeight.Bold,
            onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
        )
        ToolbarToggle(
            icon = Icons.Default.FormatItalic,
            description = bugReportStrings().italic,
            selected = span.fontStyle == FontStyle.Italic,
            onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
        )
        ToolbarToggle(
            icon = Icons.Default.StrikethroughS,
            description = bugReportStrings().strikethrough,
            selected = span.textDecoration?.contains(TextDecoration.LineThrough) == true,
            onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) },
        )
        ToolbarToggle(
            icon = Icons.Default.Code,
            description = bugReportStrings().code,
            selected = state.isCodeSpan,
            onClick = { state.toggleCodeSpan() },
        )
        ToolbarDivider()
        ToolbarToggle(
            icon = Icons.Default.Link,
            description = bugReportStrings().insertLink,
            selected = state.isLink,
            onClick = onInsertLinkClick,
        )
        if (state.isLink) {
            ToolbarToggle(
                icon = Icons.Default.LinkOff,
                description = bugReportStrings().removeLink,
                selected = false,
                onClick = { state.removeLink() },
            )
        }
        ToolbarDivider()
        ToolbarToggle(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            description = bugReportStrings().bulletedList,
            selected = state.isUnorderedList,
            onClick = { state.toggleUnorderedList() },
        )
        ToolbarToggle(
            icon = Icons.Default.FormatListNumbered,
            description = bugReportStrings().numberedList,
            selected = state.isOrderedList,
            onClick = { state.toggleOrderedList() },
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(width = 1.dp, height = ToolbarButtonSize * 0.6f)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun ToolbarToggle(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(ToolbarButtonSize)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.surfaceVariant else Color.Transparent)
            // Unfocusable so a click keeps the editor's focus and selection.
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.size(ToolbarIconSize),
        )
    }
}

@Composable
private fun LinkDialog(state: RichTextState, initialSelection: TextRange, onDismiss: () -> Unit) {
    val selectedText = remember {
        if (initialSelection.collapsed) "" else state.annotatedString.text.substring(initialSelection.min, initialSelection.max)
    }
    var text by remember { mutableStateOf(selectedText) }
    var url by remember { mutableStateOf("") }
    val isEditingExisting = state.isLink
    val textFieldEnabled = selectedText.isEmpty() && !isEditingExisting
    val canSubmit = url.isNotBlank()
    val submit = {
        val finalUrl = normalizeLinkUrl(url)
        when {
            isEditingExisting -> state.updateLink(finalUrl)
            !initialSelection.collapsed -> state.addLinkToTextRange(finalUrl, initialSelection)
            else -> state.addLink(text = text.ifBlank { finalUrl }, url = finalUrl)
        }
        onDismiss()
    }

    // The toolbar never takes focus, so the editor's text-input session would otherwise stay
    // active behind the dialog and a paste would land in the editor instead of this field.
    val urlFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { urlFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditingExisting) bugReportStrings().editLink else bugReportStrings().insertLink)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(bugReportStrings().linkText) },
                    enabled = textFieldEnabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(bugReportStrings().linkUrl) },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(urlFocusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = submit) { Text(bugReportStrings().ok) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(bugReportStrings().cancel) }
        },
    )
}

/** Adds `https://` to a scheme-less URL so "example.com" becomes a working link. */
internal fun normalizeLinkUrl(url: String): String {
    val trimmed = url.trim()
    return if ("://" in trimmed || trimmed.startsWith("mailto:")) trimmed else "https://$trimmed"
}
