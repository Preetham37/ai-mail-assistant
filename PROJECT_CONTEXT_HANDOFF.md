# BriefingOS & ai-mail-assistant — Master Context & Handoff Document

> **Purpose:** This document contains the complete state, environment configurations, system paths, credentials reference, codebase architecture, and explicit next steps to seamlessly continue the development of the **BriefingOS Native Android Mobile App** in a new conversation session.

---

## 1. Executive Summary & Objective

* **Project:** `ai-mail-assistant` (Brand name: **BriefingOS**)
* **Repository Path:** `D:\apps ideas\ai-mail-assistant`
* **Remote Repository:** [https://github.com/Preetham37/ai-mail-assistant](https://github.com/Preetham37/ai-mail-assistant)
* **Chosen Architecture Direction:** **Option 1 — Native Android App (Kotlin + Jetpack Compose + Material 3)** styled with a modern dark theme matching Samsung One UI / Zinc design language.
* **Immediate Goal in Next Session:** Scaffold the Android project in `D:\apps ideas\ai-mail-assistant\android-app`, build the Jetpack Compose UI (Briefing, Interactive Checklist, Topics, and Full In-App Email Reader), compile the debug APK, and install/launch it directly onto the connected Samsung Galaxy S24 phone via ADB.

---

## 2. Connected Mobile Device & Hardware Specs

* **Device:** Samsung Galaxy S24 (`SM-S921B`)
* **Serial Number (ADB):** `RZCXB1X0HZY`
* **ADB Status:** Authorized (`device`), connected via USB.
* **ADB Binary Path:** `C:\Users\mpree\AppData\Local\Android\Sdk\platform-tools\adb.exe`
* **Android OS / API:** Android 15 / 16 (API 35/36 supported)
* **Mobile Deep-Linking Setting (Already Configured):** 
  * Gmail app settings -> "Open supported links" has been toggled **OFF** so external links (`https://mail.google.com/mail/u/0/#all/<id>`) open directly in Google Chrome on the phone to the exact email thread.
* **Reverse Port Forwarding Tip:**
  ```powershell
  & "C:\Users\mpree\AppData\Local\Android\Sdk\platform-tools\adb.exe" reverse tcp:8080 tcp:8080
  ```
  *(Allows the mobile app on the phone to access `http://localhost:8080` hosted on the Windows PC via USB).*

---

## 3. Local Environment & Build Toolchain

All necessary SDKs, runtimes, and build tools are already installed on the Windows machine:

| Component | Path / Version | Usage Note |
|---|---|---|
| **Android SDK** | `C:\Users\mpree\AppData\Local\Android\Sdk` | Set `sdk.dir` in `local.properties` (platforms: `android-35`, `android-36`; build-tools: `35.0.0`, `36.0.0`) |
| **Java JDK 17** | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` | Must set `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"` before running Gradle |
| **Gradle** | `C:\Users\mpree\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat` (or 8.x) | Cached and ready for wrapper generation |
| **Python** | Global / Virtual environment with `google-genai`, `beautifulsoup4`, `requests`, `google-api-python-client` | For pipeline and local server |
| **Local Web Server** | `D:\apps ideas\ai-mail-assistant\serve.py` | Serves dashboard & `data.json` at `http://localhost:8080/` |

---

## 4. Codebase Architecture & File Structure

```
D:\apps ideas\ai-mail-assistant\
├── .github/
│   └── workflows/
│       └── daily_briefing.yml       # Cloud GitHub Action running dual-schedule: Morning (07:55 IST) & Evening (18:35 IST)
├── .env                              # Contains GEMINI_API_KEY and DISCORD_WEBHOOK_URL
├── .gitignore                        # Ignores credentials.json, token.json, .env, emails.db, build dirs
├── android-app/                      # Native Android project directory (Jetpack Compose, Samsung S24)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── local.properties
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── assets/data.json      # Bundled snapshot of email briefing data
│           └── java/com/preetham/aimailassistant/
├── discord_notifier.py               # Sends rich Discord Embeds with action badges, morning/evening modes & dual-schedule
├── gmail/
│   ├── credentials.json              # Gmail API OAuth client secrets
│   ├── token.json                    # Gmail API user OAuth tokens
│   ├── data.json                     # Generated JSON containing morning_briefing + email_details + briefing_type
│   └── gmail.py                      # Gmail ingestion & Gemini AI dual-mode engine (morning / evening)
├── index.html                        # BriefingOS modern dark dashboard (Tailwind, Lucide, Mermaid)
├── serve.py                          # Local server running on port 8080
└── PROJECT_CONTEXT_HANDOFF.md        # This file
```

---

## 5. Data Schema (`gmail/data.json`)

The pipeline outputs `gmail/data.json` with the following clean schema:

```json
{
  "morning_briefing": {
    "important": [
      {
        "id": "1a167a10bbe37986",
        "sender": "Sender Name <sender@example.com>",
        "subject": "Email Subject",
        "summary": "AI generated concise summary...",
        "action_required": false
      }
    ],
    "needs_attention": [ ... ],
    "informational": [ ... ],
    "actions": [
      {
        "id": "1a15e66ff90fd2f8",
        "action": "Task description required by user",
        "reason": "Why this action is needed"
      }
    ]
  },
  "email_details": {
    "1a167a10bbe37986": {
      "id": "1a167a10bbe37986",
      "sender": "Sender Name <sender@example.com>",
      "subject": "Email Subject",
      "date": "23 Oct 2026",
      "body": "Full extracted plain-text body of the email sanitized from HTML..."
    }
  }
}
```

---

## 6. Secrets & Integration Details

* **Gemini API Key:** Configured in `D:\apps ideas\ai-mail-assistant\.env` and stored in GitHub repository secrets (`GEMINI_API_KEY`).
* **Gemini Model:** `gemini-3.8-flash` (with fallback to `gemini-3.5-flash`). Do not use deprecated `gemini-2.5-flash` or `gemini-2.0-flash`.
* **Discord Webhook:** Configured in `D:\apps ideas\ai-mail-assistant\.env` and GitHub secrets (`DISCORD_WEBHOOK_URL`).
* **Gmail OAuth:** `credentials.json` and `token.json` present locally in `gmail/` and base64 encoded in GitHub secrets (`GMAIL_CREDENTIALS`, `GMAIL_TOKEN`).
* **Deep Link Pattern:** `https://mail.google.com/mail/u/0/#all/<EMAIL_ID>` opens directly in Chrome on Android.

---

## 7. Known Gotchas & Solutions

1. **Windows CP1252 Encoding Error:**
   When printing unicode emojis (`✅`, `🚨`) in Python on Windows console, always use:
   ```python
   import sys
   if sys.platform.startswith('win'):
       sys.stdout.reconfigure(encoding='utf-8', errors='replace')
   ```
2. **HTML MIME Body Stubs:**
   Certain email senders send stub plain-text bodies (`"open the email in an html-compatible client"`). `gmail/gmail.py` is configured to detect this phrase, parse the HTML payload using `BeautifulSoup`, and extract real text. Prompt Rule 22 also enforces this.
3. **Android Studio / Gradle Builds on Windows:**
   Always specify `JAVA_HOME` explicitly before executing Gradle:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
   ```

---

## 8. Immediate Action Plan for Next Session

In the new conversation session, tell the agent:
> *"Read `D:\apps ideas\ai-mail-assistant\PROJECT_CONTEXT_HANDOFF.md` and continue building the native Android app for BriefingOS."*

### Exact Step-by-Step Execution:
1. **Initialize Android Gradle Files in `D:\apps ideas\ai-mail-assistant\android-app`**:
   - `settings.gradle.kts` (includes `:app`, Google & MavenCentral repositories).
   - `local.properties` (`sdk.dir=C:\\Users\\mpree\\AppData\\Local\\Android\\Sdk`).
   - Root `build.gradle.kts` with Android Gradle Plugin & Kotlin Compose plugin.
   - `app/build.gradle.kts` with `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, Jetpack Compose Material 3 dependencies, Gson/Kotlinx Serialization.
2. **Copy Seed Data**:
   - Copy `D:\apps ideas\ai-mail-assistant\gmail\data.json` to `app/src/main/assets/data.json`.
3. **Compose UI Implementation**:
   - **Theme:** One UI / Material 3 dark aesthetic (Dark zinc `#09090b` / card `#18181b`, violet/indigo accent `#8b5cf6`).
   - **Navigation (3 Tabs):**
     1. **Briefing Tab:** Top KPI stats (Total emails, Action count, Attention needed) + High Priority / Security & Attention Cards.
     2. **Checklist Tab:** Interactive tasks from `actions` with strike-through checkboxes.
     3. **Topics / All Emails Tab:** Informational digest items.
   - **In-App Reader (`ModalBottomSheet`):** When tapping any card, opens full email details (`sender`, `date`, formatted `body`), plus a button "Open in Gmail" (`Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/mail/u/0/#all/$id"))`).
   - **Sync:** Button to reload data either from local asset or `http://localhost:8080/gmail/data.json`.
4. **Compile & Deploy to Galaxy S24**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
   # Build APK
   .\gradlew.bat assembleDebug
   # Install directly onto connected phone
   & "C:\Users\mpree\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
   # Launch
   & "C:\Users\mpree\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.preetham.aimailassistant/.MainActivity
   ```
5. **Verify with Screenshot:**
   Take an ADB screenshot to confirm the app looks great and runs smoothly on the phone!
