@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.DoubleSupplier
import java.util.function.IntConsumer
import java.util.function.IntSupplier
import java.util.function.Supplier
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

/**
 * Chrome Client's own settings (HUD, Render, Misc, Music) inside the OneConfig menu, so Right Shift opens one menu for everything.
 * The Chrome Client mod describes its modules as plain maps (ChromeMenuApi.modules); we draw them here and write changes straight back.
 * Reached by reflection: without the mod these pages are hidden.
 */
internal object ChromeMenu {
    val tabs = listOf("HUD", "Render", "Misc", "Music")

    @Suppress("UNCHECKED_CAST")
    val modules: List<Map<String, Any?>> by lazy {
        runCatching {
            Class.forName("com.chromeclient.ChromeMenuApi").getMethod("modules").invoke(null) as List<Map<String, Any?>>
        }.getOrDefault(emptyList())
    }

    val available: Boolean get() = modules.isNotEmpty()

    fun save() {
        runCatching { Class.forName("com.chromeclient.ChromeMenuApi").getMethod("save").invoke(null) }
    }
}

@Composable
internal fun ChromeModulesPage(tab: String) {
    var refresh by remember { mutableIntStateOf(0) }
    val mods = remember(tab) { ChromeMenu.modules.filter { it["tab"] == tab } }
    DisposableEffect(Unit) { onDispose { ChromeMenu.save() } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Chrome Client - $tab")
        if (mods.isEmpty()) {
            Text("Chrome Client's settings were not found. Install the Chrome Client mod to see them here.", color = OneConfigTheme.current.mutedText)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(mods) { mod -> ModuleBlock(mod, refresh) { refresh++ } }
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ModuleBlock(mod: Map<String, Any?>, refresh: Int, changed: () -> Unit) {
    val on = mod["on"] as? BooleanSupplier
    val setOn = mod["setOn"] as? Consumer<Boolean>
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (on != null && setOn != null) {
                val state = on.asBoolean
                TextButton(
                    text = "${mod["name"]}: ${if (state) "On" else "Off"}",
                    onClick = { setOn.accept(!state); changed() },
                    style = if (state) ControlStyle.Primary else ControlStyle.Secondary,
                    modifier = Modifier.width(220.dp).height(34.dp),
                )
            } else {
                Text("${mod["name"]}")
            }
            Text("${mod["desc"]}", color = OneConfigTheme.current.mutedText)
        }
        val opts = mod["opts"] as List<Map<String, Any?>>
        if (refresh < 0) Text("")   // reads the counter so everything redraws after a change
        for (o in opts) {
            if (!(o["vis"] as BooleanSupplier).asBoolean) continue
            OptionRow(o, changed)
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun OptionRow(o: Map<String, Any?>, changed: () -> Unit) {
    val label = (o["label"] as Supplier<String>).get()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (o["kind"] as String) {
            "TOGGLE" -> {
                val get = o["getB"] as BooleanSupplier
                val set = o["setB"] as Consumer<Boolean>
                val v = get.asBoolean
                TextButton(
                    text = "$label: ${if (v) "On" else "Off"}",
                    onClick = { set.accept(!v); changed() },
                    style = if (v) ControlStyle.Primary else ControlStyle.Secondary,
                    modifier = Modifier.width(260.dp).height(30.dp),
                )
            }
            "SLIDER" -> {
                val get = o["getD"] as DoubleSupplier
                val set = o["setD"] as Consumer<Double>
                val min = o["min"] as Double
                val max = o["max"] as Double
                val fmt = o["fmt"] as String
                val step = (max - min) / 20.0
                fun put(x: Double) { set.accept(x.coerceIn(min, max)); changed() }
                val v = get.asDouble
                val shown = runCatching {
                    if (fmt.contains("%d")) String.format(fmt, Math.round(if (fmt.contains("%%")) v * 100 else v)) else String.format(fmt, v)
                }.getOrElse { v.toString() }
                Text("$label: $shown", modifier = Modifier.width(220.dp))
                TextButton(text = "-", onClick = { put(get.asDouble - step) }, modifier = Modifier.width(44.dp).height(30.dp))
                TextButton(text = "+", onClick = { put(get.asDouble + step) }, modifier = Modifier.width(44.dp).height(30.dp))
            }
            "CHOICE" -> {
                val choices = o["choices"] as Array<String>
                val get = o["getI"] as IntSupplier
                val set = o["setI"] as IntConsumer
                val i = get.asInt
                TextButton(
                    text = "$label: ${choices.getOrElse(i) { "?" }}",
                    onClick = { set.accept((get.asInt + 1) % choices.size); changed() },
                    modifier = Modifier.width(260.dp).height(30.dp),
                )
            }
            "SWATCH" -> {
                val colors = o["colors"] as IntArray
                val get = o["getI"] as IntSupplier
                val set = o["setI"] as IntConsumer
                TextButton(
                    text = "$label: ${get.asInt + 1} of ${colors.size}",
                    onClick = { set.accept((get.asInt + 1) % colors.size); changed() },
                    modifier = Modifier.width(260.dp).height(30.dp),
                )
            }
            "ACTION" -> {
                val act = o["act"] as Runnable
                TextButton(text = label, onClick = { act.run(); changed() }, modifier = Modifier.width(260.dp).height(30.dp))
            }
            else -> Text(label, color = OneConfigTheme.current.mutedText)
        }
    }
}
