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
                // A API do Dadb 1.2.10 exige o keyPair gerado
                val keyPair: AdbKeyPair = AdbKeyPair.generate()
                
                // 1. Faz o pareamento (PairingConnection)
                val pairing = PairingConnection(keyPair, ip, pairPort)
                pairing.pair(pairCode)

                // 2. Conecta via ADB normal
                dadb = Dadb.create(ip, connectPort, keyPair)

                onResult(true, "Conectado")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Erro desconhecido")
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

    fun isConnected(): Boolean = dadb?.connected ?: false

    fun disconnect() {
        dadb?.close()
        dadb = null
    }
}