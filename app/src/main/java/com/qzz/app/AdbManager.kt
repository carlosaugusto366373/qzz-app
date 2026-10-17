package com.qzz.app

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object AdbManager {
    private var dadb: Dadb? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun connect(
        ip: String,
        port: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        val ctx = appContext ?: run {
            onResult(false, "Contexto não inicializado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Usa a chave pública/privada padrão
                val keyFile = File(ctx.filesDir, "adbkey")
                val pubFile = File(ctx.filesDir, "adbkey.pub")

                val keyPair = if (keyFile.exists() && pubFile.exists()) {
                    AdbKeyPair.read(keyFile, pubFile)
                } else {
                    AdbKeyPair.generate().also {
                        it.write(keyFile, pubFile)
                    }
                }

                dadb = Dadb.create(ip, port, keyPair)
                onResult(true, "Conectado")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Erro")
            }
        }
    }

    fun executeCommand(command: String): String {
        return try {
            dadb?.shell(command)?.output ?: "Desconectado"
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 100) {
        executeCommand("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    fun isConnected(): Boolean = dadb != null

    fun disconnect() {
        dadb?.close()
        dadb = null
    }
}