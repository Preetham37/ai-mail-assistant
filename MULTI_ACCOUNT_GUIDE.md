# BriefingOS // Multi-Account Onboarding & Configuration Guide

This guide details how to configure, authenticate, and manage multiple Gmail accounts across both the automated cloud ingestion pipeline (Option A) and the native on-device Android application (Option C).

---

## 1. System Overview & Multi-Account Architecture

BriefingOS supports ingesting from 2 or more independent Gmail accounts concurrently (e.g., `Personal`, `Enterprise Workspace`, `Homelab / Developer`).

- **Isolated Authentication:** Each account has its own OAuth2 refresh token and credentials. If one account fails authentication or token refresh, remaining inboxes continue uninterrupted.
- **Hardware-Backed Android Vault:** All on-device credentials and tokens are encrypted with Samsung Knox StrongBox Keymaster using AES-256 GCM (`EncryptedSharedPreferences`).
- **Unified Cloud Secret (`GMAIL_ACCOUNTS_JSON`):** GitHub Actions reads a single base64-encoded JSON secret containing all authorized accounts.
- **Cross-Account Intelligence:** Gemini 3.8-Flash analyzes emails across all configured inboxes, performing deduplication and assigning color-coded retro badges (`[PERSONAL]`, `[WORK]`, `[DEV]`).

---

## 2. Option A: CLI Onboarding Tool (`gmail/setup_accounts.py`)

The `gmail/setup_accounts.py` tool provides both an interactive terminal interface and non-interactive CLI flags to manage accounts and synchronize with GitHub Actions.

### 2.1 Interactive Terminal Mode

Launch the interactive console:

```bash
python gmail/setup_accounts.py
```

Console Interface:
```text
=================================================================
■ BRIEFINGOS // MULTI-ACCOUNT ONBOARDING & SECRET GENERATOR
=================================================================
Configuration File: D:\apps ideas\ai-mail-assistant\gmail\accounts_config.json
Configured Accounts: 1
  • [PERSONAL] mpreetham992@gmail.com (#8B5CF6)
-----------------------------------------------------------------
[1] Add New Gmail Account (OAuth2 Flow)
[2] Import Existing Legacy token.json
[3] List Detailed Account Information
[4] Remove an Account
[5] Test / Verify All Account Connections
[6] Generate Base64 Secret for GMAIL_ACCOUNTS_JSON
[7] Push Secret Directly to GitHub (via gh CLI)
[8] Exit
-----------------------------------------------------------------
Select an option [1-8]:
```

### 2.2 Adding an Account via OAuth2 PKCE Flow

1. Choose option `[1]` or run:
   ```bash
   python gmail/setup_accounts.py --add
   ```
2. A local loopback server starts on port `8088` (or next open port) and opens your default browser.
3. Sign in to your target Google account (e.g., Work or Personal) and grant read-only permissions (`gmail.readonly`).
4. The CLI auto-discovers your email address via Gmail's `users().getProfile` endpoint.
5. You are prompted to choose:
   - **Account Label:** `PERSONAL`, `WORK`, `DEV`, `ALERTS`, or custom.
   - **Accent Color:** Pick from the retro palette (`#8B5CF6` Violet, `#3B82F6` Blue, `#10B981` Emerald, `#F59E0B` Amber, etc.).
6. Account metadata and refresh token are saved locally into `gmail/accounts_config.json`.

### 2.3 Importing Existing `token.json`

If you previously authenticated a single account using legacy `auth_helper.py`:

```bash
python gmail/setup_accounts.py --import-legacy --label PERSONAL --color "#8B5CF6"
```

This verifies the existing token against Gmail API, discovers your email, and saves it into `accounts_config.json` without requiring re-authentication.

### 2.4 Synchronizing with GitHub Actions

#### Method 1: Automated Push (Recommended)
If GitHub CLI (`gh`) is installed and authenticated (`gh auth status`):
```bash
python gmail/setup_accounts.py --set-gh-secret
```
This automatically encodes `gmail/accounts_config.json` and updates `GMAIL_ACCOUNTS_JSON` repository secret.

#### Method 2: Manual Base64 Copy
Generate the Base64 secret string:
```bash
python gmail/setup_accounts.py --export-b64
```
Copy the printed output and paste it into GitHub:
1. Navigate to your repository: `Settings` → `Secrets and variables` → `Actions`.
2. Click **New repository secret**.
3. Name: `GMAIL_ACCOUNTS_JSON`
4. Value: `<paste base64 string>`
5. Click **Add secret**.

---

## 3. Option C: Native On-Device Android Authentication (AppAuth-Android)

The Android app includes native on-device OAuth 2.0 PKCE authentication via Chrome Custom Tabs, backed by Samsung Knox Keystore.

### 3.1 Adding an Account from Samsung Galaxy S24

1. Open **BriefingOS** on your device.
2. In the top console header, tap the Account Switcher pill: `[ACC: ALL ▾]`.
3. In the modal dialog (`CONSOLE://ACCOUNT_MANAGER.SYS`), tap:
   ```text
   ┌────────────────────────────────────────────────────────┐
   │ [+] ADD GMAIL ACCOUNT                                  │
   │ > INITIATE GOOGLE OAUTH2 PKCE FLOW                     │
   └────────────────────────────────────────────────────────┘
   ```
4. A secure Chrome Custom Tab opens directly into Google Identity.
5. Select your Google account and approve read-only permissions.
6. The app handles the redirect (`com.preetham.aimailassistant:/oauth2redirect`), exchanges the authorization code for tokens via PKCE, and stores them in `SecureAccountStorage` (AES-256 GCM Knox TEE).
7. The new account appears in the Account Manager and top filter carousel.

---

## 4. Local Testing & Verification

### 4.1 Test Ingestion CLI
Run the multi-account pipeline locally:
```bash
# Morning run
python gmail/gmail.py --mode morning

# Evening wrap-up run
python gmail/gmail.py --mode evening
```

### 4.2 Verify Automated Unit Tests
```bash
python -m unittest tests.test_multi_account_pipeline
```
All 10 test cases verify legacy fallback, multi-account ingestion, cross-account deduplication, and failure isolation.

---

## 5. Security Architecture & Threat Model

| Threat | Mitigation |
|---|---|
| Plaintext Token Storage on Device | EncryptedSharedPreferences backed by Knox StrongBox (AES-256 GCM). Tokens are excluded from cloud device backups via `backup_rules.xml`. |
| Client Secret Exposure in APK | RFC 7636 PKCE (Proof Key for Code Exchange) with code verifier generated on-device dynamically. |
| Ingestion Interruption by Bad Account | Per-account error boundaries. If an account's token is revoked, status is marked `EXPIRED`, and other accounts continue ingestion normally. |
| GitHub Actions Leakage | Accounts JSON is stored as an encrypted GitHub Secret (`GMAIL_ACCOUNTS_JSON`) and only decrypted in-memory during workflow execution. |
