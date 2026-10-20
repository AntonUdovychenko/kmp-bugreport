package io.github.antonudovychenko.bugreport.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.antonudovychenko.bugreport.resources.Res
import io.github.antonudovychenko.bugreport.resources.bugreport_add_image
import io.github.antonudovychenko.bugreport.resources.bugreport_attach_logs
import io.github.antonudovychenko.bugreport.resources.bugreport_bold
import io.github.antonudovychenko.bugreport.resources.bugreport_bulleted_list
import io.github.antonudovychenko.bugreport.resources.bugreport_cancel
import io.github.antonudovychenko.bugreport.resources.bugreport_capture_failed
import io.github.antonudovychenko.bugreport.resources.bugreport_close
import io.github.antonudovychenko.bugreport.resources.bugreport_code
import io.github.antonudovychenko.bugreport.resources.bugreport_crop
import io.github.antonudovychenko.bugreport.resources.bugreport_crop_hint
import io.github.antonudovychenko.bugreport.resources.bugreport_description_placeholder
import io.github.antonudovychenko.bugreport.resources.bugreport_done
import io.github.antonudovychenko.bugreport.resources.bugreport_draw
import io.github.antonudovychenko.bugreport.resources.bugreport_draw_hint
import io.github.antonudovychenko.bugreport.resources.bugreport_edit_link
import io.github.antonudovychenko.bugreport.resources.bugreport_image_unreadable
import io.github.antonudovychenko.bugreport.resources.bugreport_insert_link
import io.github.antonudovychenko.bugreport.resources.bugreport_italic
import io.github.antonudovychenko.bugreport.resources.bugreport_link_text
import io.github.antonudovychenko.bugreport.resources.bugreport_link_url
import io.github.antonudovychenko.bugreport.resources.bugreport_numbered_list
import io.github.antonudovychenko.bugreport.resources.bugreport_ok
import io.github.antonudovychenko.bugreport.resources.bugreport_remove_image
import io.github.antonudovychenko.bugreport.resources.bugreport_remove_link
import io.github.antonudovychenko.bugreport.resources.bugreport_reset
import io.github.antonudovychenko.bugreport.resources.bugreport_screenshot
import io.github.antonudovychenko.bugreport.resources.bugreport_send
import io.github.antonudovychenko.bugreport.resources.bugreport_send_failed
import io.github.antonudovychenko.bugreport.resources.bugreport_sent_message
import io.github.antonudovychenko.bugreport.resources.bugreport_sent_title
import io.github.antonudovychenko.bugreport.resources.bugreport_strikethrough
import io.github.antonudovychenko.bugreport.resources.bugreport_title
import io.github.antonudovychenko.bugreport.resources.bugreport_undo
import org.jetbrains.compose.resources.stringResource

/**
 * Every user-facing string of the library, resolved once.
 *
 * [BugReportHost][io.github.antonudovychenko.bugreport.BugReportHost] resolves them in the app's own
 * composition and provides them to its dialogs, so nothing under a dialog calls `stringResource`.
* A data class so an unchanged set does not recompose every dialog. Keeping it out of dialogs is
 * deliberate, not tidiness: on web, `stringResource` launches a coroutine from inside
 * composition, and doing that while the rich-text editor recomposes on a keystroke (type with Bold
 * toggled on) crashed the Wasm runtime with "dereferencing a null pointer" and froze the form.
 */
@Immutable
internal data class BugReportStrings(
    val addImage: String,
    val attachLogs: String,
    val bold: String,
    val bulletedList: String,
    val cancel: String,
    val captureFailed: String,
    val close: String,
    val code: String,
    val crop: String,
    val cropHint: String,
    val descriptionPlaceholder: String,
    val done: String,
    val draw: String,
    val drawHint: String,
    val editLink: String,
    val imageUnreadable: String,
    val insertLink: String,
    val italic: String,
    val linkText: String,
    val linkUrl: String,
    val numberedList: String,
    val ok: String,
    val removeImage: String,
    val removeLink: String,
    val reset: String,
    val screenshot: String,
    val send: String,
    val sendFailed: String,
    val sentMessage: String,
    val sentTitle: String,
    val strikethrough: String,
    val title: String,
    val undo: String,
)

internal val LocalBugReportStrings = staticCompositionLocalOf<BugReportStrings?> { null }

@Composable
internal fun rememberBugReportStrings(): BugReportStrings = BugReportStrings(
    addImage = stringResource(Res.string.bugreport_add_image),
    attachLogs = stringResource(Res.string.bugreport_attach_logs),
    bold = stringResource(Res.string.bugreport_bold),
    bulletedList = stringResource(Res.string.bugreport_bulleted_list),
    cancel = stringResource(Res.string.bugreport_cancel),
    captureFailed = stringResource(Res.string.bugreport_capture_failed),
    close = stringResource(Res.string.bugreport_close),
    code = stringResource(Res.string.bugreport_code),
    crop = stringResource(Res.string.bugreport_crop),
    cropHint = stringResource(Res.string.bugreport_crop_hint),
    descriptionPlaceholder = stringResource(Res.string.bugreport_description_placeholder),
    done = stringResource(Res.string.bugreport_done),
    draw = stringResource(Res.string.bugreport_draw),
    drawHint = stringResource(Res.string.bugreport_draw_hint),
    editLink = stringResource(Res.string.bugreport_edit_link),
    imageUnreadable = stringResource(Res.string.bugreport_image_unreadable),
    insertLink = stringResource(Res.string.bugreport_insert_link),
    italic = stringResource(Res.string.bugreport_italic),
    linkText = stringResource(Res.string.bugreport_link_text),
    linkUrl = stringResource(Res.string.bugreport_link_url),
    numberedList = stringResource(Res.string.bugreport_numbered_list),
    ok = stringResource(Res.string.bugreport_ok),
    removeImage = stringResource(Res.string.bugreport_remove_image),
    removeLink = stringResource(Res.string.bugreport_remove_link),
    reset = stringResource(Res.string.bugreport_reset),
    screenshot = stringResource(Res.string.bugreport_screenshot),
    send = stringResource(Res.string.bugreport_send),
    sendFailed = stringResource(Res.string.bugreport_send_failed),
    sentMessage = stringResource(Res.string.bugreport_sent_message),
    sentTitle = stringResource(Res.string.bugreport_sent_title),
    strikethrough = stringResource(Res.string.bugreport_strikethrough),
    title = stringResource(Res.string.bugreport_title),
    undo = stringResource(Res.string.bugreport_undo),
)

/** The provided strings, or freshly resolved ones when a component is used outside the host. */
@Composable
internal fun bugReportStrings(): BugReportStrings = LocalBugReportStrings.current ?: rememberBugReportStrings()
