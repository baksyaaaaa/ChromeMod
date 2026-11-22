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

/**
 * The Social tab's chat room. Messages go through the Chrome Client cosmetics service (the Cosmetics repo, server.js),
 * not through Polyfrost's servers. You sign in with Mojang's own check: the game's session joins a one-time id the
 * service made up, and the service asks Mojang whether that really happened - so nobody can write as somebody else.
 */
object ChromeChat {
    private const val BASE = "https://cosmetics-md7h.onrender.com"
    private const val KEEP = 200

    class Line(val id: Long, val name: String, val text: String)

    var lines by mutableStateOf<List<Line>>(emptyList())
        private set

    /** what to tell the player above the box ("" = everything is fine) */
    var status by mutableStateOf("Connecting to the Chrome Client chat...")
        private set

    var signedIn by mutableStateOf(false)
        private set

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
        try {
            mc.services().sessionService().joinServer(mc.user.profileId, mc.user.accessToken, nonce)
        } catch (_: Exception) {
            status = "Chat needs a signed-in Minecraft account."
            return false
        }
        val body = JsonObject().apply {
            addProperty("name", mc.user.name)
            addProperty("nonce", nonce)
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

    private fun poll() {
        val (code, json) = call("GET", "/chat/get?after=$lastId")
        if (code == 401) {
            token = null
            signedIn = false
            return
        }
        val messages = json?.getAsJsonArray("messages") ?: return
        if (messages.size() == 0) return
        val added = messages.map {
            val o = it.asJsonObject
            Line(o.get("id").asLong, o.get("n").asString, o.get("m").asString)
        }
        lastId = maxOf(lastId, added.maxOf { it.id })
        lines = (lines + added).takeLast(KEEP)
    }

    /** keeps the room up to date while the Social tab is open (cancel the coroutine to stop) */
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try {
                withContext(Dispatchers.IO) {
                    if (token == null) {
                        status = "Signing in to the chat..."
                        logIn()
                    }
                    if (token != null) poll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = "Can't reach the chat right now. Trying again..."
            }
            delay(if (token == null) 5000L else 2000L)
        }
    }

    suspend fun send(text: String) {
        try {
            withContext(Dispatchers.IO) {
                val body = JsonObject().apply { addProperty("text", text) }
                val (code, json) = call("POST", "/chat/send", body)
                when (code) {
                    200 -> {
                        status = ""
                        poll()
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
