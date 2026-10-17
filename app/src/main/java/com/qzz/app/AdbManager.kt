package com.qzz.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdbManager {

    fun init(context: Context) {
        // Não precisa de contexto sem o Dadb
    }

    fun connect(ip: String, port: Int, onResult: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Tenta rodar adb connect via terminal (vai falhar sem root/Shizuku)
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
}