package com.qzz.app

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

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
                val keyFile = File(ctx.filesDir, "adbkey")
                val pubFile = File(ctx.filesDir, "adbkey.pub")

                // Se não existir a chave, gera manualmente
                if (!keyFile.exists() || !pubFile.exists()) {
                    val keyGen = KeyPairGenerator.getInstance("RSA")
                    keyGen.initialize(2048)
                    val kp = keyGen.generateKeyPair()

                    val privateKey = kp.private as RSAPrivateKey
                    val publicKey = kp.public as RSAPublicKey

                    // Formata a privada no padrão PEM do ADB
                    val privPem = "-----BEGIN RSA PRIVATE KEY-----\n" +
                            Base64.getEncoder().encodeToString(privateKey.encoded) +
                            "\n-----END RSA PRIVATE KEY-----"
                    
                    // Formata a pública no padrão do ADB (base64 da chave crua)
                    val pubBase64 = Base64.getEncoder().encodeToString(publicKey.encoded)

                    keyFile.writeText(privPem)
                    pubFile.writeText(pubBase64)
                }

                // Agora tenta ler com o Dadb (se ele suportar)
                // Se não suportar, a gente vai usar o Dadb direto com a chave no formato dele.
                // Como a API do Dadb tá bugada, vamos tentar o AdbKeyPair.read
                val keyPair = AdbKeyPair.read(keyFile, pubFile)
                dadb = Dadb.create(ip, port, keyPair)
                
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

    fun isConnected(): Boolean = dadb != null

    fun disconnect() {
        dadb?.close()
        dadb = null
    }
}