package com.preetham.aimailassistant.security

import com.preetham.aimailassistant.data.AccountMeta

/**
 * Storage interface for managing Google account metadata, tokens, and settings.
 * Supports Knox Keystore in production and in-memory storage for testing.
 */
interface AccountStorage {
    fun getAccounts(): List<AccountMeta>
    fun saveAccounts(accounts: List<AccountMeta>)
    fun addOrUpdateAccount(account: AccountMeta, token: String? = null, refreshToken: String? = null)
    fun removeAccount(accountId: String): Boolean
    fun getAccessToken(accountId: String): String?
    fun getRefreshToken(accountId: String): String?
    fun setAccountSyncStatus(accountId: String, status: String)
    fun getAccountSyncStatus(accountId: String): String
    fun getSelectedAccountId(): String?
    fun setSelectedAccountId(accountId: String?)
    fun getCustomClientId(): String?
    fun setCustomClientId(clientId: String?)
    fun updateAccessToken(accountId: String, newAccessToken: String)
    fun getGeminiApiKey(): String?
    fun setGeminiApiKey(apiKey: String?)
}

/**
 * Lightweight in-memory implementation of [AccountStorage], ideal for unit testing
 * and mock environments where Samsung Knox / EncryptedSharedPreferences is unavailable.
 */
class InMemoryAccountStorage(
    initialAccounts: List<AccountMeta> = emptyList()
) : AccountStorage {
    private val accounts = initialAccounts.toMutableList()
    private val accessTokens = mutableMapOf<String, String>()
    private val refreshTokens = mutableMapOf<String, String>()
    private val syncStatuses = mutableMapOf<String, String>()
    private var selectedAccountId: String? = null
    private var customClientId: String? = null
    private var geminiApiKey: String? = null

    override fun getAccounts(): List<AccountMeta> = accounts.toList()

    override fun saveAccounts(accounts: List<AccountMeta>) {
        this.accounts.clear()
        this.accounts.addAll(accounts)
    }

    override fun addOrUpdateAccount(
        account: AccountMeta,
        token: String?,
        refreshToken: String?
    ) {
        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }
        token?.let { accessTokens[account.id] = it }
        refreshToken?.let { refreshTokens[account.id] = it }
    }

    override fun removeAccount(accountId: String): Boolean {
        val removed = accounts.removeAll { it.id == accountId }
        if (removed) {
            accessTokens.remove(accountId)
            refreshTokens.remove(accountId)
            syncStatuses.remove(accountId)
            if (selectedAccountId == accountId) {
                selectedAccountId = null
            }
        }
        return removed
    }

    override fun getAccessToken(accountId: String): String? = accessTokens[accountId]

    override fun getRefreshToken(accountId: String): String? = refreshTokens[accountId]

    override fun setAccountSyncStatus(accountId: String, status: String) {
        syncStatuses[accountId] = status
    }

    override fun getAccountSyncStatus(accountId: String): String = syncStatuses[accountId] ?: "OK"

    override fun getSelectedAccountId(): String? = selectedAccountId

    override fun setSelectedAccountId(accountId: String?) {
        selectedAccountId = accountId
    }

    override fun getCustomClientId(): String? = customClientId

    override fun setCustomClientId(clientId: String?) {
        customClientId = clientId?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun updateAccessToken(accountId: String, newAccessToken: String) {
        accessTokens[accountId] = newAccessToken
    }

    override fun getGeminiApiKey(): String? = geminiApiKey

    override fun setGeminiApiKey(apiKey: String?) {
        geminiApiKey = apiKey?.trim()?.takeIf { it.isNotBlank() }
    }
}
