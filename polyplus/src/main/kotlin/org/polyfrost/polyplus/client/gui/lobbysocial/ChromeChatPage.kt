@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MessageComposer
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn
import org.polyfrost.polyplus.client.social.ChromeChat

/**
 * The Social tab: friends and direct messages.
 * All of it runs through the Chrome Client cosmetics service (see ChromeChat).
 */
@Composable
internal fun ChromeChatPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ChromeChat.run() }
    val selected = ChromeChat.selected
    val friend = ChromeChat.friends.firstOrNull { it.uuid == selected }
    Box(Modifier.fillMaxSize()) {
        PageScrim()
        Column(centerColumn(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BackButton(onBack)
                PageTitle(if (friend != null) friend.name else "Friends")
                if (friend != null) {
                    TextButton(text = "Remove friend", onClick = { scope.launch { ChromeChat.remove(friend.uuid) } }, modifier = Modifier.height(34.dp))
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // left: the room, your friends, requests
                Column(Modifier.width(280.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Text("Friends (${ChromeChat.friends.size})", color = OneConfigTheme.current.mutedText) }
                        if (ChromeChat.friends.isEmpty()) item { Text("Add someone below.", color = OneConfigTheme.current.mutedText) }
                        items(ChromeChat.friends) { f ->
                            val dot = if (f.online) "● " else "○ "
                            val unread = if (f.unread > 0) "  (${f.unread})" else ""
                            TextButton(
                                text = dot + f.name + unread,
                                onClick = { ChromeChat.select(f.uuid) },
                                style = if (selected == f.uuid) ControlStyle.Primary else ControlStyle.Secondary,
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                            )
                        }
                        if (ChromeChat.incoming.isNotEmpty()) {
                            item { Text("Requests", color = OneConfigTheme.current.mutedText) }
                            items(ChromeChat.incoming) { r ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(r.name)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(text = "Accept", onClick = { scope.launch { ChromeChat.accept(r.uuid) } }, style = ControlStyle.Primary, modifier = Modifier.height(30.dp))
                                        TextButton(text = "Decline", onClick = { scope.launch { ChromeChat.decline(r.uuid) } }, modifier = Modifier.height(30.dp))
                                    }
                                }
                            }
                        }
                        if (ChromeChat.outgoing.isNotEmpty()) {
                            item { Text("Sent", color = OneConfigTheme.current.mutedText) }
                            items(ChromeChat.outgoing) { r ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(r.name)
                                    TextButton(text = "Cancel", onClick = { scope.launch { ChromeChat.cancel(r.uuid) } }, modifier = Modifier.height(28.dp))
                                }
                            }
                        }
                    }
                    MessageComposer(
                        placeholder = "Add a friend by username...",
                        disabledReason = if (ChromeChat.signedIn) null else "Signing in...",
                    ) { name -> scope.launch { ChromeChat.addFriend(name) } }
                }
                // right: the open conversation
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ChromeChat.status.isNotEmpty()) {
                        Text(ChromeChat.status, color = OneConfigTheme.current.mutedText)
                    }
                    if (friend == null) {
                        Text("Pick a friend to message, or add someone with their username.", color = OneConfigTheme.current.mutedText)
                    } else {
                        ChatLines(ChromeChat.dmLines.map { ChatLine(it.name, it.text) }, Modifier.weight(1f).fillMaxWidth())
                        MessageComposer(
                            placeholder = "Message ${friend.name}...",
                            disabledReason = if (ChromeChat.signedIn) null else "Signing in...",
                        ) { text -> scope.launch { ChromeChat.send(text) } }
                    }
                }
            }
        }
    }
}
