package org.polyfrost.polyplus.client

import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.UUID

/** Polyfrost's cosmetic options are not shown any more (Chrome Client has its own); these are just fixed defaults. */
object PolyPlusCosmeticsConfig {
    @JvmStatic
    var hideHeadCosmeticsWithHelmet = false

    @JvmStatic
    var hideFeetCosmeticsWithBoots = true

    @JvmStatic
    var forcePolyPlusCapes = false

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun isVisible(slot: BodySlot, owner: UUID): Boolean = true

    fun preload() {}

    fun save() {}
}
