package org.polyfrost.polyplus.client

import org.polyfrost.polyplus.client.gui.gamesocial.SocialMenuSize
import org.polyfrost.polyplus.client.party.HostJoinPolicy
import org.polyfrost.polyplus.client.party.SceneSharing

/** Party options are not shown anywhere any more; these are just the fixed defaults the party code reads. */
object PolyPlusPartyConfig {
    @JvmStatic
    var partyShareServer = true
    @JvmStatic
    var partyKeepOwnBackdrop = false
    @JvmStatic
    var partySceneSharing: SceneSharing = SceneSharing.ASK
    @JvmStatic
    var partyHostJoin: HostJoinPolicy = HostJoinPolicy.ASK
    @JvmStatic
    var partyPlayTogetherJoin = true
    @JvmStatic
    var socialMenuSize: SocialMenuSize = SocialMenuSize.MEDIUM
    @JvmStatic
    var pingsEnabled = true
    @JvmStatic
    var pingsShowOthers = true
    @JvmStatic
    var pingsSound = true
    @JvmStatic
    var pingMarkerScale = 100f
    @JvmStatic
    var partySceneConsents = ""

    fun preload() {}

    fun save() {}
}
