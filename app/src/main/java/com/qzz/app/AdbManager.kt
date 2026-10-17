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

    fun connect(ip: String, port: Int, onResult: (Boolean, String) -> Unit) {
        val ctx = appContext ?: run {
            onResult(false, "Contexto não inicializado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val keyFile = File(ctx.filesDir, "adbkey")
                val pubFile = File(ctx.filesDir, "adbkey.pub")

                val keyPair = if (keyFile.exists() && pubFile.exists()) {
                    AdbKeyPair.read(keyFile, pubFile)
                } else {
                    // A API do Dadb 1.2.7 exige gerar e salvar as duas chaves
                    val kp = AdbKeyPair.generate()
                    kp.write(keyFile, pubFile)
                    kp
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
}