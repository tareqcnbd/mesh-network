package com.example.core.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Manages database encryption passphrases backed by the Android Keystore / MasterKey.
 */
object DatabasePassphraseManager {
    private const val PREFS_FILE = "mesh_secure_storage"
    private const val KEY_DB_PASSPHRASE = "key_sqlcipher_passphrase"
    private const val PASSPHRASE_LENGTH_BYTES = 32

    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existingHex = sharedPreferences.getString(KEY_DB_PASSPHRASE, null)
        if (existingHex != null) {
            return hexToBytes(existingHex)
        }

        val randomBytes = ByteArray(PASSPHRASE_LENGTH_BYTES)
        SecureRandom().nextBytes(randomBytes)
        val newHex = bytesToHex(randomBytes)

        sharedPreferences.edit()
            .putString(KEY_DB_PASSPHRASE, newHex)
            .apply()

        return randomBytes
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
