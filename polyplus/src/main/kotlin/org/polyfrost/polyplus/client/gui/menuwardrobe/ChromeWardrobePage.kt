@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import androidx.compose.foundation.Canvas
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

    class Box(val x: Int, val y: Int, val w: Int, val h: Int, val depth: Float, val z: Float, val color: Int)

    private val boxCache = HashMap<String, List<Box>>()

    /** the flat coloured boxes of hat / cape / wings number [index] (see Cosmetics.java in the Chrome Client mod) */
    fun boxes(cat: Int, index: Int): List<Box> {
        val owner = cls ?: return emptyList()
        if (index <= 0) return emptyList()
        val method = when (cat) {
            0 -> "hatBoxes"
            1 -> "capeBoxes"
            2 -> "wingBoxes"
            else -> return emptyList()
        }
        return boxCache.getOrPut("$method:$index") {
            runCatching {
                (owner.getMethod(method, Int::class.javaPrimitiveType).invoke(null, index) as List<*>).map { b ->
                    val c = b!!.javaClass
                    Box(
                        c.getMethod("x").invoke(b) as Int, c.getMethod("y").invoke(b) as Int,
                        c.getMethod("w").invoke(b) as Int, c.getMethod("h").invoke(b) as Int,
                        c.getMethod("d").invoke(b) as Float, c.getMethod("z").invoke(b) as Float,
                        c.getMethod("color").invoke(b) as Int,
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    fun effectColor(index: Int): Int =
        runCatching { cls!!.getMethod("effectColor", Int::class.javaPrimitiveType).invoke(null, index) as Int }.getOrDefault(0xFFFFFFFF.toInt())

    fun choose(cat: Int, index: Int) {
        if (cls == null) return
        chosenArray()[cat] = index
        runCatching { cls!!.getMethod("save").invoke(null) }
    }
}

private fun argb(c: Int): Color = Color(if ((c ushr 24) == 0) c or (0xFF shl 24) else c)

/** the little player with what is equipped (or what you point at), drawn like the Chrome Client mod's own menu preview */
@Composable
private fun AvatarPreview(hat: Int, cape: Int, wings: Int, effect: Int, modifier: Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    if (effect > 0) {
        LaunchedEffect(Unit) {
            while (true) androidx.compose.runtime.withFrameNanos { tick = it / 90_000_000L }
        }
    }
    Canvas(modifier) {
        val u = minOf(size.width / 36f, size.height / 36f)
        val left = (size.width - 16f * u) / 2f
        val top = (size.height - 32f * u) / 2f
        fun rect(x: Float, y: Float, w: Float, h: Float, c: Int) =
            drawRect(argb(c), Offset(left + x * u, top + y * u), Size(w * u, h * u))
        fun flat(list: List<ChromeCosmetics.Box>) {
            for (b in list.sortedBy { -(it.z + it.depth / 2f) }) rect(b.x / 2f, b.y / 2f, b.w / 2f, b.h / 2f, b.color)
        }
        flat(ChromeCosmetics.boxes(2, wings))
        flat(ChromeCosmetics.boxes(1, cape))
        rect(4f, 20f, 4f, 12f, 0xFF3A2F8F.toInt()); rect(8f, 20f, 4f, 12f, 0xFF3A2F8F.toInt())
        rect(4f, 30f, 4f, 2f, 0xFF55555F.toInt()); rect(8f, 30f, 4f, 2f, 0xFF55555F.toInt())
        rect(4f, 8f, 8f, 12f, 0xFF1FA6A6.toInt())
        rect(0f, 8f, 4f, 12f, 0xFFC69C6D.toInt()); rect(12f, 8f, 4f, 12f, 0xFFC69C6D.toInt())
        rect(0f, 8f, 4f, 4f, 0xFF1FA6A6.toInt()); rect(12f, 8f, 4f, 4f, 0xFF1FA6A6.toInt())
        rect(4f, 0f, 8f, 8f, 0xFFC69C6D.toInt())
        rect(4f, 0f, 8f, 2f, 0xFF4A3322.toInt())
        rect(5f, 4f, 2f, 1f, 0xFFFFFFFF.toInt()); rect(9f, 4f, 2f, 1f, 0xFFFFFFFF.toInt())
        rect(6f, 4f, 1f, 1f, 0xFF3B2A8F.toInt()); rect(9f, 4f, 1f, 1f, 0xFF3B2A8F.toInt())
        flat(ChromeCosmetics.boxes(0, hat))
        if (effect > 0) {
            val c = ChromeCosmetics.effectColor(effect)
            for (k in 0 until 14) {
                val a = k * 0.9 + tick * 0.05
                val ex = 8f + Math.cos(a * 1.3).toFloat() * 11f
                val ey = 16f + Math.sin(a * 1.7).toFloat() * 15f
                val sz = 0.8f + (k % 3) * 0.4f
                rect(ex - sz / 2f, ey - sz / 2f, sz, sz, c)
            }
        }
    }
}

@Composable
internal fun ChromeWardrobePage(onBack: () -> Unit) {
    var cat by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(0) }   // changes whenever something is equipped, so the list redraws
    var hoverCat by remember { mutableIntStateOf(-1) }   // the item you are pointing at (shown on the player before you pick it)
    var hoverIdx by remember { mutableIntStateOf(0) }
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
                                                        PointerEventType.Enter -> { hoverCat = cat; hoverIdx = index }
                                                        PointerEventType.Exit -> if (hoverCat == cat && hoverIdx == index) hoverCat = -1
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
                // the preview: what you wear now, with the item you point at swapped in
                fun shown(c: Int): Int = if (hoverCat == c) hoverIdx else ChromeCosmetics.chosen(c)
                AvatarPreview(
                    hat = shown(0), cape = shown(1), wings = shown(2), effect = shown(3),
                    modifier = Modifier.width(260.dp).fillMaxSize(),
                )
            }
            if (picked < 0) Text("")   // reads the counter so the list above redraws after a pick
        }
    }
}
