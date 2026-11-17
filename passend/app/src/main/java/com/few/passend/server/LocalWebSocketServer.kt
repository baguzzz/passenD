package com.few.passend.server

import android.content.Context
import android.util.Log
import com.few.passend.data.repository.CredentialRepository
import com.few.passend.domain.FieldTemplates
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.crypto.Hotp
import com.few.passend.domain.crypto.Totp
import com.few.passend.domain.model.Credential
import com.few.passend.domain.model.CustomField
import com.few.passend.server.protocol.WsCrypto
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class LocalWebSocketServer(
    port: Int,
    private val repo: CredentialRepository,
    private val token: ByteArray,
    private val context: Context
) : NanoWSD(port) {

    private val activeSockets = mutableListOf<PassenDWebSocket>()

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val ws = PassenDWebSocket(handshake, repo, token, context)
        synchronized(activeSockets) { activeSockets.add(ws) }
        return ws
    }

    fun broadcastLock() {
        synchronized(activeSockets) {
            for (ws in activeSockets) { try { ws.sendLockPush() } catch (_: Throwable) {} }
        }
    }

    fun shutdown() {
        synchronized(activeSockets) {
            for (ws in activeSockets) {
                try { ws.close(WebSocketFrame.CloseCode.NormalClosure, "shutdown", false) } catch (_: Throwable) {}
            }
            activeSockets.clear()
        }
        try { stop() } catch (_: Throwable) {}
    }

    companion object { const val TAG = "PassenDServer" }
}

class PassenDWebSocket(
    handshake: fi.iki.elonen.NanoHTTPD.IHTTPSession,
    private val repo: CredentialRepository,
    private val token: ByteArray,
    private val context: Context
) : NanoWSD.WebSocket(handshake) {

    private enum class Phase { WAITING_HELLO, WAITING_AUTH, AUTHENTICATED, DEAD }
    private var phase = Phase.WAITING_HELLO
    private var sessionKey: ByteArray? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    override fun onOpen() {}

    override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
        phase = Phase.DEAD
        sessionKey?.fill(0)
        sessionKey = null
    }

    override fun onPong(pong: WebSocketFrame?) {}
    override fun onException(e: java.io.IOException?) { Log.w(LocalWebSocketServer.TAG, "ex", e) }

    override fun onMessage(message: WebSocketFrame) {
        scope.launch {
            mutex.withLock {
                try {
                    when (phase) {
                        Phase.WAITING_HELLO -> handleHello(message.textPayload)
                        Phase.WAITING_AUTH -> handleChallengeResponse(message.textPayload)
                        Phase.AUTHENTICATED -> handleDataMessage(message.textPayload)
                        Phase.DEAD -> Unit
                    }
                } catch (e: Throwable) {
                    Log.e(LocalWebSocketServer.TAG, "err", e)
                    sendEncryptedError(e.message ?: "internal")
                }
            }
        }
    }

    private fun handleHello(payload: String) {
        val obj = JSONObject(payload)
        if (obj.optString("t") != "hello") { closeWith(4001); return }
        val cn = try { WsCrypto.unB64(obj.getString("n")) } catch (e: Throwable) { closeWith(4002); return }
        if (cn.size != 16) { closeWith(4002); return }
        val sn = WsCrypto.newNonce()
        sessionKey = WsCrypto.deriveSessionKey(token, cn, sn)
        send(JSONObject().apply { put("t", "challenge"); put("n", WsCrypto.b64(sn)) }.toString())
        phase = Phase.WAITING_AUTH
    }

    private fun handleChallengeResponse(payload: String) {
        val parsed = try { JSONObject(payload) } catch (e: Throwable) { closeWith(4003); return }
        val plain = try {
            WsCrypto.decrypt(sessionKey!!, parsed.optString("iv"), parsed.optString("c"))
        } catch (e: Throwable) { closeWith(4004); return }
        val inner = try { JSONObject(plain) } catch (e: Throwable) { closeWith(4005); return }
        val action = inner.optString("action")
        if (action != "auth" && action != "ping") { closeWith(4005); return }
        phase = Phase.AUTHENTICATED
        sendEncryptedOk(JSONObject().put("authenticated", true).put("serverVersion", 17))
    }

    private suspend fun handleDataMessage(payload: String) {
        if (!CryptoManager.isUnlocked()) {
            sendEncryptedError("vault is locked — unlock in the app")
            return
        }
        val parsed = try { JSONObject(payload) } catch (e: Throwable) { sendEncryptedError("bad envelope"); return }
        val plain = try {
            WsCrypto.decrypt(sessionKey!!, parsed.optString("iv"), parsed.optString("c"))
        } catch (e: Throwable) { sendEncryptedError("decrypt failed"); return }
        val inner = try { JSONObject(plain) } catch (e: Throwable) { sendEncryptedError("bad plaintext"); return }

        when (inner.optString("action")) {
            "ping" -> sendEncryptedOk(JSONObject().put("pong", true))
            "list" -> handleList()
            "get" -> handleGet(inner.optJSONObject("payload"))
            "save" -> handleSave(inner.optJSONObject("payload"))
            "delete" -> handleDelete(inner.optJSONObject("payload"))
            "totp" -> handleOtp(inner.optJSONObject("payload"))
            "templates.list" -> handleTemplatesList()
            else -> sendEncryptedError("unknown action")
        }
    }

    private suspend fun handleList() {
        val all = repo.observeAllOnce()
        val arr = JSONArray()
        for (c in all) {
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("site", c.site)
                put("username", c.username)
                put("hasOtp", c.otpSecret.isNotEmpty())
                put("otpType", c.otpType)
                put("fieldCount", c.customFields.size)
                put("updatedAt", c.updatedAt)
                put("strength", passwordStrength(c.password))
                put("h", shortHash(c.password))
            })
        }
        sendEncryptedOk(JSONObject().put("credentials", arr))
    }

    private suspend fun handleGet(payload: JSONObject?) {
        val id = payload?.optLong("id", 0L) ?: 0L
        val site = payload?.optString("site", "") ?: ""
        val cred = when {
            id > 0L -> repo.findById(id)
            site.isNotEmpty() -> repo.findBySite(site)
            else -> null
        } ?: run { sendEncryptedError("not found"); return }

        sendEncryptedOk(JSONObject().apply {
            put("id", cred.id)
            put("site", cred.site)
            put("username", cred.username)
            put("password", cred.password)
            put("notes", cred.notes)
            put("hasOtp", cred.otpSecret.isNotEmpty())
            put("otpType", cred.otpType)
            put("otpCounter", cred.otpCounter)
            put("customFields", JSONArray().apply {
                for (f in cred.customFields) put(JSONObject().apply {
                    put("label", f.label)
                    put("value", f.value)
                    put("sensitive", f.isSensitive)
                })
            })
        })
    }

    private suspend fun handleSave(payload: JSONObject?) {
        if (payload == null) { sendEncryptedError("missing payload"); return }
        val site = payload.optString("site", "").trim()
        val password = payload.optString("password", "")
        if (site.isEmpty() || password.isEmpty()) {
            sendEncryptedError("site & password required")
            return
        }

        val existingId = payload.optLong("id", 0L)
        val existing = if (existingId > 0L) repo.findById(existingId) else null

        val otpSecret = payload.optString("otpSecret", existing?.otpSecret ?: "")
        val otpType = payload.optString("otpType", existing?.otpType ?: "TOTP")
        val otpCounter = payload.optLong("otpCounter", existing?.otpCounter ?: 0L)

        val customFields: List<CustomField> = if (payload.has("customFields")) {
            val arr = payload.optJSONArray("customFields")
            if (arr == null) emptyList() else {
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    CustomField(
                        label = o.optString("label", ""),
                        value = o.optString("value", ""),
                        isSensitive = o.optBoolean("sensitive", false)
                    )
                }
            }
        } else existing?.customFields ?: emptyList()

        val id = repo.save(Credential(
            id = existing?.id ?: 0L,
            site = site,
            username = payload.optString("username", "").trim(),
            password = password,
            notes = payload.optString("notes", ""),
            otpSecret = otpSecret,
            otpType = otpType,
            otpCounter = otpCounter,
            customFields = customFields,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        ))
        sendEncryptedOk(JSONObject().put("id", id))
    }

    private suspend fun handleDelete(payload: JSONObject?) {
        val id = payload?.optLong("id", 0L) ?: 0L
        if (id <= 0L) { sendEncryptedError("id required"); return }
        repo.delete(id)
        sendEncryptedOk(JSONObject().put("deleted", id))
    }

    private suspend fun handleOtp(payload: JSONObject?) {
        val id = payload?.optLong("id", 0L) ?: 0L
        if (id <= 0L) { sendEncryptedError("id required"); return }
        val cred = repo.findById(id) ?: run { sendEncryptedError("not found"); return }
        if (cred.otpSecret.isEmpty()) { sendEncryptedError("no OTP configured"); return }

        try {
            if (cred.otpType == "HOTP") {
                val oldCounter = cred.otpCounter
                val code = Hotp.generate(cred.otpSecret, oldCounter)
                try { repo.updateOtpCounter(cred.id, oldCounter + 1) } catch (_: Throwable) {}
                sendEncryptedOk(JSONObject().apply {
                    put("code", code)
                    put("counter", oldCounter)
                    put("nextCounter", oldCounter + 1)
                    put("otpType", "HOTP")
                })
            } else {
                val r = Totp.generate(cred.otpSecret)
                sendEncryptedOk(JSONObject().apply {
                    put("code", r.code)
                    put("remaining", r.remaining)
                    put("period", r.period)
                    put("digits", r.digits)
                    put("otpType", "TOTP")
                })
            }
        } catch (e: Throwable) {
            sendEncryptedError("OTP error: ${e.message}")
        }
    }

    private suspend fun handleTemplatesList() {
        val templates = FieldTemplates.all(context)
        val arr = JSONArray()
        for (t in templates) {
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("fields", JSONArray().apply {
                    for (f in t.fields) put(JSONObject().apply {
                        put("label", f.label)
                        put("value", f.value)
                        put("sensitive", f.isSensitive)
                    })
                })
            })
        }
        sendEncryptedOk(JSONObject().put("templates", arr))
    }

    fun sendLockPush() {
        val key = sessionKey ?: return
        try {
            val (iv, ct) = WsCrypto.encrypt(key, JSONObject().put("push", "lock").toString())
            send(JSONObject().put("iv", iv).put("c", ct).toString())
        } catch (_: Throwable) {}
    }

    private fun passwordStrength(p: String): Int {
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.length >= 16) s++
        if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
        if (p.any { it.isDigit() }) s++
        if (p.any { !it.isLetterOrDigit() }) s++
        return s.coerceAtMost(5)
    }

    private fun shortHash(p: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(p.toByteArray(Charsets.UTF_8))
        val truncated = ByteArray(8)
        System.arraycopy(digest, 0, truncated, 0, 8)
        return truncated.joinToString("") { String.format("%02x", it) }
    }

    private fun sendEncryptedOk(data: JSONObject) =
        sendEncrypted(JSONObject().put("ok", true).put("data", data))

    private fun sendEncryptedError(msg: String) =
        sendEncrypted(JSONObject().put("ok", false).put("error", msg))

    private fun sendEncrypted(inner: JSONObject) {
        val key = sessionKey ?: return
        try {
            val (iv, ct) = WsCrypto.encrypt(key, inner.toString())
            send(JSONObject().put("iv", iv).put("c", ct).toString())
        } catch (e: Throwable) {
            Log.e(LocalWebSocketServer.TAG, "send", e)
        }
    }

    private fun closeWith(code: Int) {
        phase = Phase.DEAD
        try {
            val cc = when (code) {
                4001 -> WebSocketFrame.CloseCode.ProtocolError
                else -> WebSocketFrame.CloseCode.PolicyViolation
            }
            close(cc, "err $code", false)
        } catch (_: Throwable) {}
    }
}