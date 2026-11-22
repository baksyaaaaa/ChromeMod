@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn

/**
 * Chrome Client's wardrobe: it lists the hats, capes, wings and effects that come with the Chrome Client mod and equips
 * them through it (the mod keeps the picks and draws them). Nothing here is loaded from a store or needs an account.
 * The mod is reached by reflection, so this page works (and shows a note) even when Chrome Client is not installed.
 */
private object ChromeCosmetics {
    private val cls: Class<*>? = runCatching { Class.forName("com.chromeclient.Cosmetics") }.getOrNull()

    val available: Boolean get() = cls != null

    @Suppress("UNCHECKED_CAST")
    private fun items(): Array<Array<String>> = (cls!!.getField("ITEMS").get(null) as Array<Array<String>>)

    private fun chosenArray(): IntArray = cls!!.getField("chosen").get(null) as IntArray

    val categories: List<String>
        get() = if (cls == null) emptyList() else listOf("Hats", "Capes", "Wings", "Effects").take(items().size)

    fun names(cat: Int): List<String> = if (cls == null) emptyList() else items()[cat].toList()

    fun chosen(cat: Int): Int = if (cls == null) 0 else chosenArray()[cat]

    fun choose(cat: Int, index: Int) {
        if (cls == null) return
        chosenArray()[cat] = index
        runCatching { cls!!.getMethod("save").invoke(null) }
    }
}

@Composable
internal fun ChromeWardrobePage(onBack: () -> Unit) {
    var cat by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(0) }   // changes whenever something is equipped, so the list redraws
    Box(centerColumn()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BackButton(onBack)
                PageTitle("Wardrobe")
            }
            if (!ChromeCosmetics.available) {
                Text("Chrome Client's cosmetics were not found. Install the Chrome Client mod to use the wardrobe.", color = OneConfigTheme.current.mutedText)
                return@Column
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChromeCosmetics.categories.forEachIndexed { i, name ->
                    TextButton(text = name, onClick = { cat = i }, style = if (i == cat) ControlStyle.Primary else ControlStyle.Secondary, modifier = Modifier.height(34.dp))
                }
            }
            val names = ChromeCosmetics.names(cat)
            val current = ChromeCosmetics.chosen(cat)
            Text(if (current == 0) "Nothing equipped" else "Equipped: ${names.getOrElse(current) { "" }}", color = OneConfigTheme.current.mutedText)
            LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(names.indices.toList().chunked(4)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { index ->
                            val on = index == current
                            TextButton(
                                text = names[index],
                                onClick = { ChromeCosmetics.choose(cat, index); picked++ },
                                style = if (on) ControlStyle.Primary else ControlStyle.Secondary,
                                modifier = Modifier.height(36.dp),
                            )
                        }
                    }
                }
            }
            if (picked < 0) Text("")   // reads the counter so the list above redraws after a pick
        }
    }
}
