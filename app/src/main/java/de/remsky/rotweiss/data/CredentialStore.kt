package de.remsky.rotweiss.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/** Speichert die Zugangsdaten verschlüsselt (Android Keystore). */
class CredentialStore(private val context: Context) {

    private val prefs: SharedPreferences = openOrRepair()

    private fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * Passt der Keystore-Schlüssel nicht mehr zur gespeicherten Datei (z. B. nach Neuinstallation
     * mit wiederhergestellten App-Daten), stürzt die Entschlüsselung ab. Dann alte Daten und
     * Schlüssel verwerfen und neu anlegen – der Nutzer meldet sich einfach neu an.
     */
    private fun openOrRepair(): SharedPreferences = try {
        open().also { it.all }   // erzwingt Entschlüsselung
    } catch (e: Exception) {
        context.deleteSharedPreferences(FILE)
        context.deleteSharedPreferences("__androidx_security_crypto_encrypted_prefs__")
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        }
        open()
    }

    fun load(): Credentials? = try {
        val s = prefs.getString("server", null)
        val u = prefs.getString("user", null)
        val p = prefs.getString("pass", null)
        if (s == null || u == null || p == null) null else Credentials(s, u, p)
    } catch (e: Exception) {
        null
    }

    fun save(c: Credentials) = prefs.edit()
        .putString("server", c.server).putString("user", c.user).putString("pass", c.pass)
        .apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val FILE = "xtream_credentials"
        const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    }
}
