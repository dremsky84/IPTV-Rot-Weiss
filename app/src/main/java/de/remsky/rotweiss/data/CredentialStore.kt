package de.remsky.rotweiss.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Speichert die Zugangsdaten verschlüsselt (Android Keystore). */
class CredentialStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "xtream_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Credentials? {
        val s = prefs.getString("server", null) ?: return null
        val u = prefs.getString("user", null) ?: return null
        val p = prefs.getString("pass", null) ?: return null
        return Credentials(s, u, p)
    }

    fun save(c: Credentials) = prefs.edit()
        .putString("server", c.server).putString("user", c.user).putString("pass", c.pass)
        .apply()

    fun clear() = prefs.edit().clear().apply()
}
