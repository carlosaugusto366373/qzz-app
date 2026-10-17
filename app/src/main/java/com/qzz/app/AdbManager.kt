package com.qzz.app

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.PairingConnection
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

    fun pairAndConnect(
        ip: String,
        pairPort: Int,
        pairCode: String,
        connectPort: Int,
        onResult: (Boolean, String) -> Unit
    ) {
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
                    val kp = AdbKeyPair.generate()
                    kp.write(keyFile, pubFile)
                    kp
                }

                // 1. Pareamento com o código de 6 dígitos
                val pairing = PairingConnection(keyPair, ip, pairPort)
                pairing.pair(pairCode)

                // 2. Conecta na porta de conexão (ex: 5555)
                dadb = Dadb.create(ip, connectPort, keyPair)

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