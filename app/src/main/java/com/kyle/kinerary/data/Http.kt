package com.kyle.kinerary.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Http {
    private const val TIMEOUT = 20_000

    fun download(url: String): ByteArray {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) throw IOException("Google answered ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    fun checkLogin(account: Account) {
        val connection = open("${account.server}/users/auth")
        connection.auth(account)
        try {
            when (connection.responseCode) {
                200 -> Unit
                401 -> throw IOException("Wrong username or password")
                else -> throw IOException("The server answered ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun sync(account: Account, body: String): String {
        val connection = open("${account.server}/kinerary/sync")
        connection.auth(account)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code != 200) {
                val message = connection.errorStream?.use { it.readBytes().decodeToString() }
                    ?.let { runCatching { JSONObject(it).optString("message") }.getOrNull() }
                throw IOException(
                    when (code) {
                        401 -> "Wrong username or password"
                        404 -> "The sync server needs updating for Kinerary"
                        else -> message?.takeIf { it.isNotBlank() } ?: "The server answered $code"
                    }
                )
            }
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT
        readTimeout = TIMEOUT
        instanceFollowRedirects = true
        // ngrok shows browsers a warning page first; this skips it
        setRequestProperty("ngrok-skip-browser-warning", "1")
    }

    private fun HttpURLConnection.auth(account: Account) {
        setRequestProperty("x-auth-user", account.username)
        setRequestProperty("x-auth-key", account.key)
        setRequestProperty("Accept", "application/json")
    }
}
