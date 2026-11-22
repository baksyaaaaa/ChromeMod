@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.joml.Vector3f
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.ChromeCosmeticsBridge
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewLights
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
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
private const val FRONT_YAW = 180f - 16f

/** shows item [index] of category [cat] on the character (-1 = back to what is equipped) */
private var hoveredCat = -1
private var hoveredIndex = -1

/** the pointer entered / left an item: moving from one item to the next can report "entered the new one" BEFORE "left the old one",
 *  so a leave only clears the figure when it is for the item that is still being shown (otherwise it flickered back to the equipped one) */
private fun hover(cat: Int, index: Int, entered: Boolean) {
    if (entered) {
        hoveredCat = cat; hoveredIndex = index
        showOnFigure(cat, index)
    } else if (hoveredCat == cat && hoveredIndex == index) {
        hoveredCat = -1; hoveredIndex = -1
        showOnFigure(cat, -1)
    }
}

private fun showOnFigure(cat: Int, index: Int) {
    when (cat) {
        0 -> ChromeCosmeticsBridge.hat = index
        1 -> ChromeCosmeticsBridge.cape = index
        2 -> ChromeCosmeticsBridge.wings = index
    }
    ChromeCosmeticsBridge.pushLobby()
}

private object ChromeCosmetics {
    private val cls: Class<*>? = runCatching { Class.forName("com.chromeclient.Cosmetics") }.getOrNull()

    val available: Boolean get() = cls != null

    @Suppress("UNCHECKED_CAST")
    private fun items(): Array<Array<String>> = (cls!!.getField("ITEMS").get(null) as Array<Array<String>>)

    private fun chosenArray(): IntArray = cls!!.getField("chosen").get(null) as IntArray

    val categories: List<String>
        get() = if (cls == null) emptyList() else listOf("Hats", "Capes", "Wings").take(items().size)

    fun names(cat: Int): List<String> = if (cls == null) emptyList() else items()[cat].toList()

    fun chosen(cat: Int): Int = if (cls == null) 0 else chosenArray()[cat]

    fun choose(cat: Int, index: Int) {
        if (cls == null) return
        chosenArray()[cat] = index
        runCatching { cls!!.getMethod("save").invoke(null) }
    }
}

@Composable
internal fun ChromeWardrobePage(onBack: () -> Unit, embedded: Boolean = false) {
    var cat by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(0) }   // changes whenever something is equipped, so the list redraws
    val rotation = remember { PreviewRotation(FRONT_YAW) }
    DisposableEffect(Unit) { onDispose { ChromeCosmeticsBridge.hat = -1; ChromeCosmeticsBridge.cape = -1; ChromeCosmeticsBridge.wings = -1; ChromeCosmeticsBridge.pushLobby() } }
    Box(Modifier.fillMaxSize()) {
      // no scrim: the scene behind the wardrobe stays as bright as it is
      Box(if (embedded) Modifier.fillMaxSize().padding(28.dp) else centerColumn()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BackButton(onBack)
                PageTitle("Wardrobe")
            }
            if (!ChromeCosmetics.available) {
                Text("Chrome Client's cosmetics were not found. Install the Chrome Client mod to use the wardrobe.", color = OneConfigTheme.current.mutedText)
                return@Column
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChromeCosmetics.categories.forEachIndexed { i, name ->
                            TextButton(text = name, onClick = { cat = i }, style = if (i == cat) ControlStyle.Primary else ControlStyle.Secondary, modifier = Modifier.height(34.dp))
                        }
                        TextButton(
                            text = "Clear all",
                            onClick = { ChromeCosmetics.categories.indices.forEach { ChromeCosmetics.choose(it, 0) }; picked++ },
                            modifier = Modifier.height(34.dp),
                        )
                    }
                    val names = ChromeCosmetics.names(cat)
                    val current = ChromeCosmetics.chosen(cat)
                    Text(if (current == 0) "Nothing equipped" else "Equipped: ${names.getOrElse(current) { "" }}", color = OneConfigTheme.current.mutedText)
                    LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(names.indices.toList().chunked(3)) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { index ->
                                    Box(
                                        Modifier.pointerInput(cat, index) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    when (awaitPointerEvent().type) {
                                                        PointerEventType.Enter -> hover(cat, index, true)
                                                        PointerEventType.Exit -> hover(cat, index, false)
                                                        else -> Unit
                                                    }
                                                }
                                            }
                                        },
                                    ) {
                                        TextButton(
                                            text = names[index],
                                            onClick = { ChromeCosmetics.choose(cat, index); picked++ },
                                            style = if (index == current) ControlStyle.Primary else ControlStyle.Secondary,
                                            modifier = Modifier.height(36.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // the character: it wears what you have equipped, with the item you point at shown on it before you pick it
                Box(
                    Modifier.width(300.dp).fillMaxSize().pointerInput(rotation) {
                        detectDragGestures { change, drag ->
                            rotation.yaw -= drag.x * 0.5f
                            rotation.userRotated = true
                            change.consume()
                        }
                    },
                ) {
                  if (!org.polyfrost.polyplus.client.lobby.LobbyScene.showing()) {
                    PlayerPreview(
                        Modifier.fillMaxSize(),
                        source = PlayerPreviewSource.LocalLive,
                        autoSpin = false,
                        modelScale = 0.36f,
                        verticalAnchor = 0.53f,
                        initialYaw = FRONT_YAW,
                        live = true,
                        rotation = rotation,
                        fadeEdges = false,
                        cameraTiltDeg = 8f,
                        lights = PreviewLights(
                            key = Vector3f(0.45f, -0.85f, 1.0f).normalize(),
                            fill = Vector3f(-0.8f, -0.2f, 0.2f).normalize(),
                        ),
                    )
                  }
                }
            }
            if (picked < 0) Text("")   // reads the counter so the list above redraws after a pick
        }
      }
    }
}
