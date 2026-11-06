package com.mikapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Client minimal de l'API RouterOS (port 8728) - RouterOS v6.43+ et v7. */
class RouterOs {
    private var socket: Socket? = null
    private lateinit var input: DataInputStream
    private lateinit var output: OutputStream

    fun connect(host: String, port: Int, user: String, pass: String) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 6000)
        s.soTimeout = 15000
        socket = s
        input = DataInputStream(s.getInputStream().buffered())
        output = s.getOutputStream().buffered()
        talk("/login", "=name=$user", "=password=$pass")
    }

    fun close() { try { socket?.close() } catch (_: Exception) {} }

    suspend fun call(vararg words: String): List<Map<String, String>> =
        withContext(Dispatchers.IO) { synchronized(this@RouterOs) { talk(*words) } }

    private fun talk(vararg words: String): List<Map<String, String>> {
        for (w in words) writeWord(w)
        writeLen(0)
        output.flush()
        val result = mutableListOf<Map<String, String>>()
        var error: String? = null
        while (true) {
            val sentence = readSentence()
            if (sentence.isEmpty()) continue
            val type = sentence[0]
            val attrs = HashMap<String, String>()
            for (i in 1 until sentence.size) {
                val w = sentence[i]
                if (w.startsWith("=")) {
                    val idx = w.indexOf('=', 1)
                    if (idx > 0) attrs[w.substring(1, idx)] = w.substring(idx + 1)
                }
            }
            when (type) {
                "!re" -> result.add(attrs)
                "!trap" -> error = attrs["message"] ?: "Erreur RouterOS"
                "!fatal" -> throw RuntimeException(sentence.getOrNull(1) ?: "Erreur fatale")
                "!done" -> {
                    if (error != null) throw RuntimeException(error)
                    return result
                }
            }
        }
    }

    private fun readSentence(): List<String> {
        val list = mutableListOf<String>()
        while (true) {
            val len = readLen()
            if (len == 0) return list
            val buf = ByteArray(len)
            input.readFully(buf)
            list.add(String(buf, Charsets.UTF_8))
        }
    }

    private fun writeWord(w: String) {
        val b = w.toByteArray(Charsets.UTF_8)
        writeLen(b.size)
        output.write(b)
    }

    private fun writeLen(l: Int) {
        when {
            l < 0x80 -> output.write(l)
            l < 0x4000 -> { val v = l or 0x8000; output.write(v shr 8); output.write(v and 0xFF) }
            l < 0x200000 -> {
                val v = l or 0xC00000
                output.write(v shr 16); output.write((v shr 8) and 0xFF); output.write(v and 0xFF)
            }
            else -> {
                output.write(0xE0 or ((l shr 24) and 0x1F))
                output.write((l shr 16) and 0xFF); output.write((l shr 8) and 0xFF); output.write(l and 0xFF)
            }
        }
    }

    private fun readLen(): Int {
        val c = input.readUnsignedByte()
        return when {
            c < 0x80 -> c
            c < 0xC0 -> ((c and 0x3F) shl 8) or input.readUnsignedByte()
            c < 0xE0 -> {
                val b1 = input.readUnsignedByte(); val b2 = input.readUnsignedByte()
                ((c and 0x1F) shl 16) or (b1 shl 8) or b2
            }
            else -> {
                val b1 = input.readUnsignedByte(); val b2 = input.readUnsignedByte(); val b3 = input.readUnsignedByte()
                ((c and 0x0F) shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }
    }
}
