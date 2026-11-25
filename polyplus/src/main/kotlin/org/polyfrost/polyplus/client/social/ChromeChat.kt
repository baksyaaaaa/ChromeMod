package org.polyfrost.polyplus.client.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * The Social tab's friends and direct messages. Everything goes through the Chrome Client cosmetics service (the Cosmetics repo, server.js),
 * not through Polyfrost's servers. You sign in with your Mojang player certificate (the key pair Mojang signs for you, the
 * one Minecraft signs chat with): the game signs a one-time id from the service with it, and the service checks both
 * signatures by itself - so nobody can write as somebody else, and nothing depends on the service reaching Mojang.
 */
object ChromeChat {
    private const val BASE = "https://cosmetics-md7h.onrender.com"
    private const val KEEP = 200

    class Line(val id: Long, val name: String, val text: String)

    /** what to tell the player above the box ("" = everything is fine) */
    var status by mutableStateOf("Connecting to Chrome Client friends...")
        private set

    var signedIn by mutableStateOf(false)
        private set

    class Friend(val uuid: String, val name: String, val online: Boolean, val unread: Int)
    class Person(val uuid: String, val name: String)

    var friends by mutableStateOf<List<Friend>>(emptyList())
        private set
    var incoming by mutableStateOf<List<Person>>(emptyList())
        private set
    var outgoing by mutableStateOf<List<Person>>(emptyList())
        private set

    /** the friend whose messages are open (a uuid), or null when none is open */
    var selected by mutableStateOf<String?>(null)
        private set
    var dmLines by mutableStateOf<List<Line>>(emptyList())
        private set
    private var dmLast = 0L

    private var token: String? = null
    private var lastId = 0L

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    private fun call(method: String, path: String, body: JsonObject? = null, withToken: Boolean = true): Pair<Int, JsonObject?> {
        val builder = HttpRequest.newBuilder(URI.create(BASE + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
        if (withToken) token?.let { builder.header("Authorization", "Bearer $it") }
        if (method == "POST") {
            builder.POST(HttpRequest.BodyPublishers.ofString((body ?: JsonObject()).toString()))
        } else {
            builder.GET()
        }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val json = runCatching { JsonParser.parseString(response.body()).asJsonObject }.getOrNull()
        return response.statusCode() to json
    }

    private fun logIn(): Boolean {
        val (_, nonceJson) = call("POST", "/chat/nonce", withToken = false)
        val nonce = nonceJson?.get("nonce")?.asString
        if (nonce == null) {
            status = "The chat server did not answer. Trying again..."
            return false
        }
        val mc = Minecraft.getInstance()
        val proof = try {
            certificateProof(mc.user.accessToken, nonce)
        } catch (_: Exception) {
            null
        }
        if (proof == null) {
            status = "Chat needs a signed-in Minecraft account. Restart the game from the launcher."
            return false
        }
        proof.first.addProperty("uuid", mc.user.profileId.toString().replace("-", ""))
        val body = JsonObject().apply {
            addProperty("name", mc.user.name)
            addProperty("nonce", nonce)
            add("cert", proof.first)
            addProperty("sig", proof.second)
        }
        val (code, json) = call("POST", "/chat/login", body, withToken = false)
        val newToken = json?.get("token")?.asString
        if (code != 200 || newToken == null) {
            status = json?.get("error")?.asString?.let { "Chat sign-in failed: $it" } ?: "Chat sign-in failed."
            return false
        }
        token = newToken
        signedIn = true
        status = ""
        return true
    }

    /** the base64 inside a PEM block (Mojang's "RSA ... KEY" blocks hold PKCS#8 / X.509, the way Minecraft reads them) */
    private fun pemContents(pem: String): String =
        pem.lines().filter { !it.startsWith("-----") }.joinToString("") { it.trim() }

    /**
     * Gets the Mojang player certificate with the game's access token and signs [nonce] with its private key.
     * Returns the certificate's public part (uuid is filled in by the caller) and the signature, or null.
     */
    private fun certificateProof(accessToken: String, nonce: String): Pair<JsonObject, String>? {
        val request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/player/certificates"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $accessToken")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return null
        val cert = JsonParser.parseString(response.body()).asJsonObject
        val keyPair = cert.getAsJsonObject("keyPair") ?: return null
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pemContents(keyPair.get("privateKey").asString))))
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(nonce.toByteArray(Charsets.UTF_8))
            sign()
        }
        val publicPart = JsonObject().apply {
            addProperty("expiresAt", cert.get("expiresAt").asString)
            addProperty("publicKey", pemContents(keyPair.get("publicKey").asString))
            addProperty("keySig", cert.get("publicKeySignatureV2").asString)
        }
        return publicPart to Base64.getEncoder().encodeToString(signature)
    }

    /** name -> uuid through Mojang, from the game (the service can't reach Mojang itself) */
    private fun lookUp(name: String): String? = try {
        val request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + java.net.URLEncoder.encode(name, Charsets.UTF_8)))
            .timeout(Duration.ofSeconds(15)).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) JsonParser.parseString(response.body()).asJsonObject.get("id")?.asString else null
    } catch (_: Exception) {
        null
    }

    fun select(uuid: String?) {
        selected = uuid
        dmLines = emptyList()
        dmLast = 0L
    }

    private fun people(array: com.google.gson.JsonArray?): List<Person> =
        array?.map { Person(it.asJsonObject.get("u").asString, it.asJsonObject.get("n").asString) } ?: emptyList()

    private fun refreshState() {
        val (code, json) = call("GET", "/social/state")
        if (code == 401) {
            token = null
            signedIn = false
            return
        }
        if (json == null || code != 200) return
        friends = json.getAsJsonArray("friends")?.map {
            val o = it.asJsonObject
            Friend(o.get("u").asString, o.get("n").asString, o.get("online").asBoolean, o.get("unread").asInt)
        } ?: emptyList()
        incoming = people(json.getAsJsonArray("incoming"))
        outgoing = people(json.getAsJsonArray("outgoing"))
        if (selected != null && friends.none { it.uuid == selected }) select(null)   // removed from their list
    }

    private fun pollDm() {
        val who = selected ?: return
        val (code, json) = call("GET", "/social/dm/get?u=$who&after=$dmLast")
        if (code == 401) {
            token = null
            signedIn = false
            return
        }
        if (selected != who || json == null) return   // you switched while it loaded
        val messages = json.getAsJsonArray("messages") ?: return
        if (messages.size() == 0) return
        val added = messages.map {
            val o = it.asJsonObject
            Line(o.get("id").asLong, o.get("n").asString, o.get("m").asString)
        }
        dmLast = maxOf(dmLast, added.maxOf { it.id })
        dmLines = (dmLines + added).takeLast(KEEP)
    }

    /** keeps the room up to date while the Social tab is open (cancel the coroutine to stop) */
    suspend fun run() {
        var round = 0
        while (currentCoroutineContext().isActive) {
            try {
                withContext(Dispatchers.IO) {
                    if (token == null) {
                        status = "Signing in to the chat..."
                        logIn()
                    }
                    if (token != null) {
                        pollDm()
                        if (token != null && round % 2 == 0) refreshState()
                    }
                }
                round++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = "Can't reach the chat right now. Trying again..."
            }
            delay(if (token == null) 5000L else 2000L)
        }
    }

    private suspend fun act(path: String, body: JsonObject, done: String? = null) {
        try {
            withContext(Dispatchers.IO) {
                val (code, json) = call("POST", path, body)
                when (code) {
                    200 -> {
                        status = done ?: ""
                        refreshState()
                    }
                    401 -> {
                        token = null
                        signedIn = false
                    }
                    429 -> status = "Slow down a little."
                    else -> status = json?.get("error")?.asString ?: "That did not work."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status = "Can't reach the chat right now."
        }
    }

    private fun obj(vararg pairs: Pair<String, String>) = JsonObject().apply { pairs.forEach { addProperty(it.first, it.second) } }

    suspend fun addFriend(name: String): Unit {
        val clean = name.trim()
        val uuid = withContext(Dispatchers.IO) { lookUp(clean) }
        val body = obj("name" to clean)
        if (uuid != null) body.addProperty("u", uuid)
        act("/social/request", body, "Friend request sent to $clean.")
    }

    suspend fun accept(uuid: String) = act("/social/accept", obj("u" to uuid), "")

    suspend fun decline(uuid: String) = act("/social/decline", obj("u" to uuid), "")

    suspend fun cancel(uuid: String) = act("/social/cancel", obj("u" to uuid), "")

    suspend fun remove(uuid: String) {
        act("/social/remove", obj("u" to uuid), "")
        if (selected == uuid) select(null)
    }

    /** writes to the open conversation (a friend's messages) */
    suspend fun send(text: String) {
        val who = selected ?: return
        try {
            withContext(Dispatchers.IO) {
                val (code, json) = call("POST", "/social/dm/send", obj("u" to who, "text" to text))
                when (code) {
                    200 -> {
                        status = ""
                        pollDm()
                    }
                    401 -> {
                        token = null
                        signedIn = false
                    }
                    429 -> status = "Slow down a little."
                    else -> status = json?.get("error")?.asString ?: "Message not sent."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status = "Message not sent. Can't reach the chat."
        }
    }
}
