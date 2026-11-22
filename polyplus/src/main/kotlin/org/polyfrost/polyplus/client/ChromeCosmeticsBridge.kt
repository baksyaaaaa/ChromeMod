package org.polyfrost.polyplus.client

import java.lang.reflect.Method

/**
 * Lets the Chrome Client mod draw its cosmetics on the menu figures (the wardrobe character, the main menu character).
 * While a preview figure is being submitted we tell the mod's cosmetics layer "this is a preview" (see CosmeticLayer.setPreview);
 * it then dresses the figure in the saved picks, or in what is set here (the item you point at in the wardrobe).
 * The mod is reached by reflection, so nothing changes when it is not installed.
 */
object ChromeCosmeticsBridge {
    /** what to show instead of the saved pick; -1 = the saved pick */
    @Volatile var hat = -1
    @Volatile var cape = -1
    @Volatile var wings = -1

    private val method: Method? by lazy {
        runCatching {
            Class.forName("com.chromeclient.CosmeticLayer").getMethod(
                "setPreview",
                java.lang.Boolean.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
            )
        }.getOrNull()
    }

    private val lobbyMethod: Method? by lazy {
        runCatching {
            Class.forName("com.chromeclient.CosmeticLayer").getMethod("setLobby", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE)
        }.getOrNull()
    }

    /** the big character in the lobby is the avatar with this entity id (MIN_VALUE = none); it wears the saved picks */
    fun lobbyAvatar(id: Int) {
        lobbyId = id
        pushLobby()
    }

    private var lobbyId = Int.MIN_VALUE

    /** sends the item being pointed at to the lobby character */
    fun pushLobby() {
        runCatching { lobbyMethod?.invoke(null, lobbyId, hat, cape, wings) }
    }

    fun begin() {
        runCatching { method?.invoke(null, true, hat, cape, wings) }
    }

    fun end() {
        runCatching { method?.invoke(null, false, -1, -1, -1) }
    }

    inline fun <T> around(block: () -> T): T {
        begin()
        try {
            return block()
        } finally {
            end()
        }
    }
}
