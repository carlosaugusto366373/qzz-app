package com.qzz.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdbManager {

    // Mantém a função init pro MainActivity não quebrar
    fun init(context: android.content.Context) {
        // Não precisa de contexto
    }

    fun connect(
        ip: String,
        port: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Tenta rodar o comando adb connect (vai falhar sem root/Shizuku)
                val process = Runtime.getRuntime().exec("adb connect $ip:$port")
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                
                if (output.contains("connected")) {
                    onResult(true, "Conectado via ADB")
                } else {
                    onResult(false, "Falha: $output")
                }
            } catch (e: Exception) {
                onResult(false, "O Android não permite auto-conexão ADB sem Shizuku ou Root.")
            }
        }
    }

    fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("adb shell $command")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.ifEmpty { "OK" }
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 100) {
        executeCommand("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    fun isConnected(): Boolean = false
}