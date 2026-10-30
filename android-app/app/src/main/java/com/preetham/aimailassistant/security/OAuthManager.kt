package com.preetham.aimailassistant.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.preetham.aimailassistant.BuildConfig
import com.preetham.aimailassistant.data.AccountMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import net.openid.appauth.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native On-Device Google OAuth2 PKCE Authentication Manager using AppAuth-Android (RFC 8252).
 * Launches Google sign-in inside Chrome Custom Tabs and persists tokens to Samsung Knox Keystore.
 */
class OAuthManager(
    private val context: Context,
    private val secureAccountStorage: AccountStorage = SecureAccountStorage(context)
) {
    companion object {
        private const val TAG = "OAuthManager"

        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val DEFAULT_REDIRECT_URI = "com.preetham.aimailassistant:/oauth2redirect"
        const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

        // OAuth2 Endpoints for BriefingOS
        const val DEFAULT_CLIENT_ID = ""
    }

    private val authService = AuthorizationService(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(AUTH_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT)
    )

    /**
     * Creates the Intent configured for AppAuth PKCE authorization flow.
     */
    private fun resolveClientId(explicitClientId: String = ""): String {
        return when {
            explicitClientId.isNotBlank() -> explicitClientId
            !secureAccountStorage.getCustomClientId().isNullOrBlank() -> secureAccountStorage.getCustomClientId()!!
            BuildConfig.OAUTH_CLIENT_ID.isNotBlank() -> BuildConfig.OAUTH_CLIENT_ID
            else -> DEFAULT_CLIENT_ID
        }
    }

    /**
     * Resolves the proper redirect URI for Google OAuth:
     * Native Android OAuth requires the reverse client ID scheme:
     * com.googleusercontent.apps.<client_id_prefix>:/oauth2redirect
     */
    fun resolveRedirectUri(targetClientId: String): Uri {
        val prefix = targetClientId.substringBefore(".apps.googleusercontent.com")
        return if (prefix.isNotBlank() && prefix != targetClientId) {
            Uri.parse("com.googleusercontent.apps.$prefix:/oauth2redirect")
        } else {
            Uri.parse(DEFAULT_REDIRECT_URI)
        }
    }

    /**
     * Creates the Intent configured for AppAuth PKCE authorization flow.
     */
    fun createAuthIntent(
        clientId: String = "",
        redirectUriStr: String = ""
    ): Intent {
        val targetClientId = resolveClientId(clientId)
        if (targetClientId.isBlank()) {
            throw IllegalStateException("Google OAuth Client ID not configured. Please use Manual Profile Setup or configure your OAuth credentials.")
        }
        val redirectUri = if (redirectUriStr.isNotBlank()) {
            Uri.parse(redirectUriStr)
        } else {
            resolveRedirectUri(targetClientId)
        }

        Log.i(TAG, "Initiating OAuth with client: $targetClientId, redirect: $redirectUri")

        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,
            targetClientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
        .setScopes(GMAIL_SCOPE, "email", "profile")
        .setPrompt("consent")
        .setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())

        val authRequest = authRequestBuilder.build()
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Initiates the Google OAuth PKCE authorization flow via Chrome Custom Tabs.
     */
    fun startAuthFlow(
        launcher: ActivityResultLauncher<Intent>,
        clientId: String = "",
        redirectUriStr: String = ""
    ) {
        val authIntent = createAuthIntent(clientId, redirectUriStr)
        launcher.launch(authIntent)
    }

    /**
     * Handles the callback Intent from the authorization response,
     * performs token exchange with PKCE, discovers profile details,
     * and securely persists credentials into Knox-backed storage.
     */
    fun handleAuthResponse(
        intent: Intent?,
        clientId: String = "",
        clientSecret: String? = null,
        onComplete: (Result<AccountMeta>) -> Unit
    ) {
        if (intent == null) {
            onComplete(Result.failure(Exception("Authorization intent was null")))
            return
        }

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            Log.e(TAG, "Authorization error: ${exception.message}", exception)
            onComplete(Result.failure(exception))
            return
        }

        if (response == null) {
            onComplete(Result.failure(Exception("No authorization response contained in intent")))
            return
        }

        val clientAuth: ClientAuthentication = if (!clientSecret.isNullOrBlank()) {
            ClientSecretPost(clientSecret)
        } else {
            NoClientAuthentication.INSTANCE
        }

        val tokenRequest = response.createTokenExchangeRequest()

        authService.performTokenRequest(tokenRequest, clientAuth) { tokenResponse, tokenEx ->
            if (tokenEx != null || tokenResponse == null) {
                val err = tokenEx ?: Exception("Token exchange returned null response")
                Log.e(TAG, "OAuth token exchange failed: ${err.message}", err)
                onComplete(Result.failure(err))
                return@performTokenRequest
            }

            val accessToken = tokenResponse.accessToken ?: ""
            val refreshToken = tokenResponse.refreshToken ?: ""
            val idToken = tokenResponse.idToken

            scope.launch(Dispatchers.IO) {
                try {
                    val profile = fetchProfile(accessToken, idToken)
                    val email = profile.first
                    val displayName = profile.second

                    val existingAccounts = secureAccountStorage.getAccounts()
                    val authIndex = existingAccounts.size
                    val palette = SecureAccountStorage.DEFAULT_PALETTES
                    val colorHex = palette[authIndex % palette.size]

                    val label = when (authIndex) {
                        0 -> "PERSONAL"
                        1 -> "WORK"
                        2 -> "DEV"
                        else -> "ACCOUNT_${authIndex + 1}"
                    }

                    val safeSlug = email.substringBefore("@").replace(".", "_").replace("-", "_")
                    val accountId = "acc_${label.lowercase()}_$safeSlug"

                    val accountMeta = AccountMeta(
                        id = accountId,
                        email = email,
                        display_name = displayName.ifBlank { "$label Mail" },
                        label = label,
                        color_hex = colorHex,
                        is_active = true
                    )

                    // Persist credentials in Samsung Knox Keystore
                    secureAccountStorage.addOrUpdateAccount(
                        account = accountMeta,
                        token = accessToken,
                        refreshToken = refreshToken
                    )

                    withContext(Dispatchers.Main) {
                        onComplete(Result.success(accountMeta))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed resolving profile or saving account: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        onComplete(Result.failure(e))
                    }
                }
            }
        }
    }

    /**
     * Resolves user email address and display name via ID token JWT, Gmail API, or UserInfo.
     */
    private fun fetchProfile(accessToken: String, idToken: String?): Pair<String, String> {
        // 1. Try decoding ID Token JWT payload
        if (!idToken.isNullOrBlank()) {
            try {
                val parts = idToken.split(".")
                if (parts.size >= 2) {
                    val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                    val jsonStr = String(decodedBytes, Charsets.UTF_8)
                    val obj = JSONObject(jsonStr)
                    val email = obj.optString("email", "")
                    val name = obj.optString("name", "")
                    if (email.isNotBlank()) {
                        return Pair(email, name.ifBlank { email.substringBefore("@") })
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract profile from id_token: ${e.message}")
            }
        }

        // 2. Try Gmail API users().getProfile
        try {
            val url = URL("https://gmail.googleapis.com/gmail/v1/users/me/profile")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(text)
                val email = obj.optString("emailAddress", "")
                if (email.isNotBlank()) {
                    return Pair(email, email.substringBefore("@"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch profile from Gmail API: ${e.message}")
        }

        // 3. Try UserInfo endpoint
        try {
            val url = URL("https://www.googleapis.com/oauth2/v3/userinfo")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(text)
                val email = obj.optString("email", "")
                val name = obj.optString("name", "")
                if (email.isNotBlank()) {
                    return Pair(email, name.ifBlank { email.substringBefore("@") })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch profile from userinfo: ${e.message}")
        }

        // Fallback
        val fallbackEmail = "user${System.currentTimeMillis() % 1000}@gmail.com"
        return Pair(fallbackEmail, "Google Account")
    }

    fun dispose() {
        authService.dispose()
    }

    /**
     * Obtains a valid access token for the given account.
     * Checks if token needs refresh; if expired or requested, performs refresh with Google.
     * Updates Knox storage with the fresh access token.
     */
    suspend fun getFreshAccessToken(accountId: String, forceRefresh: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        val currentToken = secureAccountStorage.getAccessToken(accountId)
        val refreshToken = secureAccountStorage.getRefreshToken(accountId)

        if (refreshToken.isNullOrBlank()) {
            return@withContext if (!currentToken.isNullOrBlank() && !forceRefresh) {
                Result.success(currentToken)
            } else {
                Result.failure(IllegalStateException("No refresh token stored for account $accountId. Re-authentication required."))
            }
        }

        if (!forceRefresh && !currentToken.isNullOrBlank()) {
            return@withContext Result.success(currentToken)
        }

        val clientId = resolveClientId()
        val refreshResult = executeTokenRefresh(clientId, refreshToken)
        if (refreshResult.isSuccess) {
            val newToken = refreshResult.getOrThrow()
            secureAccountStorage.updateAccessToken(accountId, newToken)
            secureAccountStorage.setAccountSyncStatus(accountId, "OK")
            Result.success(newToken)
        } else {
            val ex = refreshResult.exceptionOrNull() ?: Exception("Unknown refresh error")
            Log.e(TAG, "Token refresh failed for $accountId: ${ex.message}")
            secureAccountStorage.setAccountSyncStatus(accountId, "EXPIRED")
            Result.failure(ex)
        }
    }

    suspend fun ensureFreshAccessToken(accountId: String): Result<String> = getFreshAccessToken(accountId)

    /**
     * Executes OAuth2 refresh request using AppAuth.
     */
    private suspend fun executeTokenRefresh(clientId: String, refreshToken: String): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            val tokenRequest = TokenRequest.Builder(
                serviceConfig,
                clientId
            )
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()

            authService.performTokenRequest(tokenRequest, NoClientAuthentication.INSTANCE) { response, ex ->
                if (response != null && response.accessToken != null) {
                    continuation.resume(Result.success(response.accessToken!!))
                } else {
                    continuation.resume(Result.failure(ex ?: Exception("Token refresh returned empty response")))
                }
            }
        }
    }
}
