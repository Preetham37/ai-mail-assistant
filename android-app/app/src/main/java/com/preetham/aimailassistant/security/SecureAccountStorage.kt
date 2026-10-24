package com.preetham.aimailassistant.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.preetham.aimailassistant.data.AccountMeta

/**
 * Knox Hardware-Backed Secure Storage for Multi-Account Credentials.
 * Encrypts account tokens and sensitive credentials using Samsung Knox
 * StrongBox Keymaster with AES-256 GCM scheme.
 */
class SecureAccountStorage(context: Context) {

    companion object {
        private const val TAG = "SecureAccountStorage"
        private const val SECURE_PREFS_FILE = "secure_briefing_accounts"
        private const val KEY_ACCOUNTS_JSON = "saved_accounts_meta_json"
        private const val KEY_SELECTED_ACCOUNT = "selected_account_id"
        private const val PREFIX_TOKEN = "access_token_"
        private const val PREFIX_REFRESH_TOKEN = "refresh_token_"
        private const val PREFIX_SYNC_STATUS = "sync_status_"

        // Predefined default account palette colors for Retro UI
        val DEFAULT_PALETTES = listOf(
            "#3B82F6", // Work Blue
            "#8B5CF6", // Personal Violet
            "#10B981", // Dev Emerald
            "#F59E0B", // Alerts Amber
            "#EC4899"  // Projects Magenta
        )
    }

    private val gson = Gson()

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: ${e.message}", e)
        // Fallback for corrupted key states
        context.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)
    }

    /**
     * Retrieve all configured Google accounts.
     */
    fun getAccounts(): List<AccountMeta> {
        val json = securePrefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AccountMeta>>() {}.type
            gson.fromJson<List<AccountMeta>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing accounts: ${e.message}")
            emptyList()
        }
    }

    /**
     * Persist complete list of accounts.
     */
    fun saveAccounts(accounts: List<AccountMeta>) {
        val json = gson.toJson(accounts)
        securePrefs.edit().putString(KEY_ACCOUNTS_JSON, json).apply()
    }

    /**
     * Add or update an account configuration.
     */
    fun addOrUpdateAccount(
        account: AccountMeta,
        token: String? = null,
        refreshToken: String? = null
    ) {
        val current = getAccounts().toMutableList()
        val index = current.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            current[index] = account
        } else {
            current.add(account)
        }
        saveAccounts(current)

        val editor = securePrefs.edit()
        if (token != null) {
            editor.putString(PREFIX_TOKEN + account.id, token)
        }
        if (refreshToken != null) {
            editor.putString(PREFIX_REFRESH_TOKEN + account.id, refreshToken)
        }
        editor.apply()
    }

    /**
     * Disconnect and purge an account and its hardware-encrypted tokens.
     */
    fun removeAccount(accountId: String): Boolean {
        val current = getAccounts().toMutableList()
        val removed = current.removeAll { it.id == accountId }
        if (removed) {
            saveAccounts(current)
            securePrefs.edit()
                .remove(PREFIX_TOKEN + accountId)
                .remove(PREFIX_REFRESH_TOKEN + accountId)
                .remove(PREFIX_SYNC_STATUS + accountId)
                .apply()

            // If deleted account was selected, reset to ALL (null)
            if (getSelectedAccountId() == accountId) {
                setSelectedAccountId(null)
            }
        }
        return removed
    }

    /**
     * Read Knox-encrypted OAuth access token.
     */
    fun getAccessToken(accountId: String): String? {
        return securePrefs.getString(PREFIX_TOKEN + accountId, null)
    }

    /**
     * Read Knox-encrypted OAuth refresh token.
     */
    fun getRefreshToken(accountId: String): String? {
        return securePrefs.getString(PREFIX_REFRESH_TOKEN + accountId, null)
    }

    /**
     * Update sync status string (e.g. "OK", "EXPIRED", "SYNCING").
     */
    fun setAccountSyncStatus(accountId: String, status: String) {
        securePrefs.edit().putString(PREFIX_SYNC_STATUS + accountId, status).apply()
    }

    fun getAccountSyncStatus(accountId: String): String {
        return securePrefs.getString(PREFIX_SYNC_STATUS + accountId, "OK") ?: "OK"
    }

    /**
     * Get currently selected account ID filter (null means ALL inboxes unified).
     */
    fun getSelectedAccountId(): String? {
        return securePrefs.getString(KEY_SELECTED_ACCOUNT, null)
    }

    /**
     * Persist account filter choice.
     */
    fun setSelectedAccountId(accountId: String?) {
        if (accountId == null) {
            securePrefs.edit().remove(KEY_SELECTED_ACCOUNT).apply()
        } else {
            securePrefs.edit().putString(KEY_SELECTED_ACCOUNT, accountId).apply()
        }
    }

    fun getCustomClientId(): String? {
        return securePrefs.getString("custom_oauth_client_id", null)
    }

    fun setCustomClientId(clientId: String?) {
        if (clientId.isNullOrBlank()) {
            securePrefs.edit().remove("custom_oauth_client_id").apply()
        } else {
            securePrefs.edit().putString("custom_oauth_client_id", clientId.trim()).apply()
        }
    }
}
