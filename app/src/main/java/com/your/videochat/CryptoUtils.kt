package com.your.videochat

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import org.json.JSONObject

/**
 * Утилиты для шифрования сигнальных сообщений
 * Использует AES-256-GCM для максимальной безопасности
 */
class CryptoUtils {
    
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_LENGTH = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        
        // Генерируем случайный ключ при запуске приложения
        private val secretKey = generateSecretKey()
        
        private fun generateSecretKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(KEY_LENGTH)
            return keyGenerator.generateKey()
        }
        
        /**
         * Шифрует сообщение с AES-256-GCM
         */
        fun encryptMessage(message: String): EncryptedMessage {
            return try {
                val cipher = Cipher.getInstance(ALGORITHM)
                val iv = ByteArray(GCM_IV_LENGTH)
                Random.nextBytes(iv)
                
                val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
                
                val messageBytes = message.toByteArray(Charsets.UTF_8)
                val encryptedData = cipher.doFinal(messageBytes)
                
                // В AES-GCM тег аутентификации находится в конце зашифрованных данных
                val ciphertext = encryptedData.copyOfRange(0, encryptedData.size - GCM_TAG_LENGTH)
                val authTag = encryptedData.copyOfRange(encryptedData.size - GCM_TAG_LENGTH, encryptedData.size)
                
                EncryptedMessage(
                    encryptedData = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                    iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                    authTag = Base64.encodeToString(authTag, Base64.NO_WRAP)
                )
            } catch (e: Exception) {
                throw SecurityException("Encryption failed", e)
            }
        }
        
        /**
         * Расшифровывает сообщение с проверкой целостности
         */
        fun decryptMessage(encryptedMessage: EncryptedMessage): String? {
            return try {
                val cipher = Cipher.getInstance(ALGORITHM)
                val iv = Base64.decode(encryptedMessage.iv, Base64.NO_WRAP)
                val encryptedData = Base64.decode(encryptedMessage.encryptedData, Base64.NO_WRAP)
                val authTag = Base64.decode(encryptedMessage.authTag, Base64.NO_WRAP)
                
                val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
                cipher.updateAAD("video-chat".toByteArray())
                
                val decryptedData = cipher.doFinal(encryptedData + authTag)
                String(decryptedData, Charsets.UTF_8)
            } catch (e: Exception) {
                null // Дешифрация не удалась - возможно атака
            }
        }
        
        /**
         * Проверяет целостность сообщения
         */
        fun verifyMessageIntegrity(originalMessage: String, decryptedMessage: String?): Boolean {
            return decryptedMessage != null && decryptedMessage == originalMessage
        }
        
        /**
         * Создает безопасный JSON payload с шифрованием
         */
        fun createSecurePayload(type: String, data: JSONObject): JSONObject {
            return try {
                val message = JSONObject().apply {
                    put("type", type)
                    put("data", data)
                }
                
                val encryptedData = encryptMessage(message.toString())
                JSONObject().apply {
                    put("type", type)
                    put("encrypted", JSONObject().apply {
                        put("encryptedData", encryptedData.encryptedData)
                        put("iv", encryptedData.iv)
                        put("authTag", encryptedData.authTag)
                    })
                }
            } catch (e: Exception) {
                JSONObject().apply {
                    put("type", type)
                    put("error", "encryption_failed")
                }
            }
        }
        
        /**
         * Расшифровывает безопасный JSON payload
         */
        fun parseSecurePayload(payload: JSONObject): JSONObject? {
            return try {
                if (!payload.has("encrypted")) {
                    return payload // Нешифрованное сообщение
                }
                
                val encrypted = payload.getJSONObject("encrypted")
                val encryptedMessage = EncryptedMessage(
                    encryptedData = encrypted.getString("encryptedData"),
                    iv = encrypted.getString("iv"),
                    authTag = encrypted.getString("authTag")
                )
                
                val decryptedMessage = decryptMessage(encryptedMessage)
                if (decryptedMessage != null) {
                    JSONObject(decryptedMessage)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Класс для хранения зашифрованных данных
     */
    data class EncryptedMessage(
        val encryptedData: String,
        val iv: String,
        val authTag: String
    )
}
