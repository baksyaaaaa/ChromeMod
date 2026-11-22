@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MessageComposer
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn
import org.polyfrost.polyplus.client.social.ChromeChat

/** The Social tab: one chat room for everyone using Chrome Client, run through the Chrome Client cosmetics service. */
@Composable
internal fun ChromeChatPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ChromeChat.run() }
    Box(Modifier.fillMaxSize()) {
        PageScrim()
        Column(centerColumn(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BackButton(onBack)
                PageTitle("Chrome Client chat")
            }
            if (ChromeChat.status.isNotEmpty()) {
                Text(ChromeChat.status, color = OneConfigTheme.current.mutedText)
            }
            ChatLines(ChromeChat.lines.map { ChatLine(it.name, it.text) }, Modifier.weight(1f).fillMaxWidth())
            MessageComposer(
                placeholder = "Message everyone using Chrome Client...",
                disabledReason = if (ChromeChat.signedIn) null else "Signing in...",
            ) { text -> scope.launch { ChromeChat.send(text) } }
        }
    }
}
