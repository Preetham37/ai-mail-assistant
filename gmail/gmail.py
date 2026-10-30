import os
import sys
import re
import base64
import json
import time
import hashlib
import argparse
from datetime import datetime

# Ensure UTF-8 console output on Windows
if sys.platform.startswith('win'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

from google_auth_oauthlib.flow import InstalledAppFlow
from google.oauth2.credentials import Credentials
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from bs4 import BeautifulSoup
from google import genai
from dotenv import load_dotenv

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.dirname(BASE_DIR)

# Load environment variables from both root .env and gmail/.env
load_dotenv(os.path.join(ROOT_DIR, ".env"))
load_dotenv(os.path.join(BASE_DIR, ".env"))

TOKEN_PATH = os.path.join(BASE_DIR, "token.json")
CREDS_PATH = os.path.join(BASE_DIR, "credentials.json")
DATA_PATH = os.path.join(BASE_DIR, "data.json")
ACCOUNTS_CONFIG_PATH = os.path.join(BASE_DIR, "accounts_config.json")

SCOPES = [
    "https://www.googleapis.com/auth/gmail.readonly"
]

DEFAULT_PALETTE = [
    "#8B5CF6",  # Violet (Personal)
    "#3B82F6",  # Blue (Work)
    "#10B981",  # Emerald (Dev/Alerts)
    "#F59E0B",  # Amber
    "#EC4899",  # Pink
    "#06B6D4",  # Cyan
]

def restore_secrets_from_env():
    """Restores token.json and credentials.json from environment variables if present (CI/CD environments)."""
    env_token = os.getenv("GMAIL_TOKEN")
    if env_token and not os.path.exists(TOKEN_PATH):
        try:
            content = base64.b64decode(env_token).decode("utf-8")
        except Exception:
            content = env_token
        with open(TOKEN_PATH, "w", encoding="utf-8") as f:
            f.write(content)
        print("Restored token.json from GMAIL_TOKEN environment variable.")

    env_creds = os.getenv("GMAIL_CREDENTIALS")
    if env_creds and not os.path.exists(CREDS_PATH):
        try:
            content = base64.b64decode(env_creds).decode("utf-8")
        except Exception:
            content = env_creds
        with open(CREDS_PATH, "w", encoding="utf-8") as f:
            f.write(content)
        print("Restored credentials.json from GMAIL_CREDENTIALS environment variable.")

def extract_client_credentials(creds_data):
    """Extracts client_id and client_secret from various credentials dict formats."""
    if not isinstance(creds_data, dict):
        return None, None
    target = creds_data
    if "installed" in creds_data:
        target = creds_data["installed"]
    elif "web" in creds_data:
        target = creds_data["web"]
    return target.get("client_id"), target.get("client_secret")

def load_accounts_config():
    """
    Loads Gmail accounts configuration.
    Priority:
    1. GMAIL_ACCOUNTS_JSON environment variable (JSON string or base64 encoded JSON).
    2. Local gmail/accounts_config.json file if present.
    3. Seamless fallback: Single-account legacy mode using TOKEN_PATH, CREDS_PATH, GMAIL_TOKEN, GMAIL_CREDENTIALS.
    """
    raw_env = os.getenv("GMAIL_ACCOUNTS_JSON", "").strip()
    raw_json = None

    if raw_env:
        try:
            # Check if base64 encoded
            raw_json = base64.b64decode(raw_env).decode("utf-8")
            json.loads(raw_json)
        except Exception:
            raw_json = raw_env
    elif os.path.exists(ACCOUNTS_CONFIG_PATH):
        try:
            with open(ACCOUNTS_CONFIG_PATH, "r", encoding="utf-8") as f:
                raw_json = f.read().strip()
        except Exception as e:
            print(f"Notice: Could not read {ACCOUNTS_CONFIG_PATH}: {e}")

    if raw_json:
        try:
            parsed = json.loads(raw_json)
            if isinstance(parsed, list) and len(parsed) > 0:
                accounts = []
                for idx, item in enumerate(parsed):
                    acc_id = item.get("account_id") or f"acc_{idx + 1}"
                    acc_label = item.get("account_label") or ("PERSONAL" if idx == 0 else f"ACCOUNT_{idx + 1}")
                    acc_color = item.get("account_color_hex") or DEFAULT_PALETTE[idx % len(DEFAULT_PALETTE)]
                    acc_index = item.get("auth_index", idx)
                    accounts.append({
                        "account_id": acc_id,
                        "email_address": item.get("email_address", ""),
                        "display_name": item.get("display_name") or acc_label,
                        "account_label": acc_label,
                        "account_color_hex": acc_color,
                        "auth_index": acc_index,
                        "enabled": item.get("enabled", True),
                        "credentials": item.get("credentials"),
                        "token": item.get("token"),
                        "token_path": item.get("token_path"),
                        "creds_path": item.get("creds_path"),
                        "query": item.get("query")
                    })
                print(f"📋 Loaded {len(accounts)} account configuration(s) from multi-account config.")
                return accounts
        except Exception as e:
            print(f"⚠️ Warning: Failed to parse GMAIL_ACCOUNTS_JSON: {e}. Falling back to single account mode.")

    # Single-account legacy fallback
    restore_secrets_from_env()
    print("ℹ️ Using single-account legacy fallback.")
    return [{
        "account_id": "default",
        "email_address": "",
        "display_name": "Primary Mail",
        "account_label": "PRIMARY",
        "account_color_hex": DEFAULT_PALETTE[0],
        "auth_index": 0,
        "enabled": True,
        "token_path": TOKEN_PATH,
        "creds_path": CREDS_PATH
    }]

def get_service_for_account(acc_config):
    """
    Initializes and returns an authorized Gmail API service instance for a given account config.
    Auto-refreshes expired tokens and handles credentials validation.
    """
    acc_id = acc_config.get("account_id", "default")
    token_data = acc_config.get("token")
    token_path = acc_config.get("token_path")
    creds_data = acc_config.get("credentials")
    creds_path = acc_config.get("creds_path") or CREDS_PATH

    credentials = None

    # Case 1: In-memory token object or string
    if token_data:
        token_info = None
        if isinstance(token_data, dict):
            token_info = dict(token_data)
        elif isinstance(token_data, str):
            try:
                token_info = json.loads(token_data)
            except Exception:
                try:
                    token_info = json.loads(base64.b64decode(token_data).decode("utf-8"))
                except Exception:
                    token_info = {}

        if token_info:
            # If client_id / client_secret are missing from token_info, pull them from creds
            if not token_info.get("client_id") or not token_info.get("client_secret"):
                cid, csec = extract_client_credentials(creds_data)
                if not cid and os.path.exists(creds_path):
                    try:
                        with open(creds_path, "r", encoding="utf-8") as f:
                            cid, csec = extract_client_credentials(json.load(f))
                    except Exception:
                        pass
                if cid and not token_info.get("client_id"):
                    token_info["client_id"] = cid
                if csec and not token_info.get("client_secret"):
                    token_info["client_secret"] = csec

            credentials = Credentials.from_authorized_user_info(token_info, SCOPES)

    # Case 2: Token file path
    elif token_path and os.path.exists(token_path):
        credentials = Credentials.from_authorized_user_file(token_path, SCOPES)

    # Auto-refresh expired credentials
    if credentials and credentials.expired and credentials.refresh_token:
        try:
            print(f"[{acc_id}] Refreshing expired access token via Google OAuth...")
            credentials.refresh(Request())
            if token_path and os.path.exists(token_path):
                with open(token_path, "w", encoding="utf-8") as tok:
                    tok.write(credentials.to_json())
            print(f"[{acc_id}] Google OAuth token successfully refreshed.")
        except Exception as e:
            print(f"[{acc_id}] Notice: Token refresh failed ({e}). Proceeding if still valid.")

    # Interactive flow if running locally
    if not credentials or not credentials.valid:
        if creds_path and os.path.exists(creds_path):
            print(f"[{acc_id}] Interactive authentication required...")
            flow = InstalledAppFlow.from_client_secrets_file(creds_path, SCOPES)
            credentials = flow.run_local_server(port=0)
            target_path = token_path or TOKEN_PATH
            with open(target_path, "w", encoding="utf-8") as tok:
                tok.write(credentials.to_json())
        else:
            raise FileNotFoundError(
                f"Missing or invalid authentication credentials for account '{acc_id}'."
            )

    service = build("gmail", "v1", credentials=credentials)

    # Discover email address from profile if not provided
    if not acc_config.get("email_address"):
        try:
            profile = service.users().getProfile(userId="me").execute()
            if profile and profile.get("emailAddress"):
                acc_config["email_address"] = profile["emailAddress"]
        except Exception:
            pass

    return service

def get_gmail_service():
    """Backward compatibility wrapper returning service for the primary account."""
    accounts = load_accounts_config()
    return get_service_for_account(accounts[0])

# --------------------------------------------------
# DECODE EMAIL DATA
# --------------------------------------------------

def decode_data(data):
    decoded = base64.urlsafe_b64decode(data)
    return decoded.decode("utf-8", errors="replace")

# --------------------------------------------------
# CLEAN HTML (STRIP ALL TAGS AND STYLES)
# --------------------------------------------------

def clean_html(html):
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "head", "meta", "noscript", "link"]):
        tag.decompose()

    for block in soup.find_all(["br", "p", "div", "tr", "li", "h1", "h2", "h3", "h4", "h5", "h6"]):
        block.append(" ")

    text = soup.get_text(" ", strip=True)
    return " ".join(text.split())

# --------------------------------------------------
# GET EMAIL BODY (PREFER SUBSTANTIVE CONTENT OVER STUBS)
# --------------------------------------------------

HTML_STUB_PHRASES = [
    "html-compatible",
    "html compatible",
    "view this email in",
    "open this email in",
    "open in html",
    "please view in html",
    "enable html",
    "viewing this message in plain text",
    "requires an html email client",
]

def is_stub_text(text):
    if not text:
        return True
    lower = text.lower()
    return any(phrase in lower for phrase in HTML_STUB_PHRASES)

def content_gater(data):
    plain_text = []
    html_body = []

    def explore(data, mimeType=None):
        if isinstance(data, dict):
            for i in data:
                if i == "data":
                    if mimeType == "text/plain":
                        plain_text.append(decode_data(data[i]))
                    elif mimeType == "text/html":
                        html_body.append(clean_html(decode_data(data[i])))
                elif isinstance(data[i], dict):
                    current_mime = mimeType
                    if "mimeType" in data:
                        current_mime = data["mimeType"]
                    explore(data[i], current_mime)
                elif isinstance(data[i], list):
                    explore(data[i], mimeType)
        elif isinstance(data, list):
            for item in data:
                explore(item, mimeType)

    explore(data)

    best_html = html_body[0] if html_body else None
    best_plain = plain_text[0] if plain_text else None

    # If plain text is just an "open in HTML client" placeholder, discard it and use cleaned HTML
    if best_plain and is_stub_text(best_plain) and best_html:
        return best_html

    # If both exist, choose whichever contains actual richer content
    if best_html and best_plain:
        if len(best_html) > len(best_plain) * 1.3:
            return best_html
        return best_plain

    if best_html:
        return best_html
    if best_plain:
        return best_plain

    return ""

def get_info_header(headers, name):
    for i in headers:
        if i.get("name", "").lower() == name.lower():
            return i.get("value")
    return None

def mail_object(message_id, message, headers):
    return {
        "id": message_id,
        "sender": get_info_header(headers, "From") or "Unknown Sender",
        "subject": get_info_header(headers, "Subject") or "(No Subject)",
        "date": get_info_header(headers, "Date") or "",
        "body": content_gater(message.get("payload", {})),
        "rfc_message_id": get_info_header(headers, "Message-ID") or get_info_header(headers, "Message-Id") or ""
    }

# --------------------------------------------------
# CROSS-ACCOUNT DEDUPLICATION
# --------------------------------------------------

def compute_email_fingerprint(mail):
    """Computes a robust deduplication fingerprint based on Message-ID header or normalized Subject + Body hash."""
    rfc_id = mail.get("rfc_message_id", "").strip().lower()
    if rfc_id:
        return f"rfc:{rfc_id}"

    subject = mail.get("subject", "").lower()
    norm_subject = re.sub(r"^(re|fwd|fw):\s*", "", subject, flags=re.IGNORECASE).strip()

    body = mail.get("body", "").lower()
    norm_body = " ".join(re.findall(r"\w+", body)[:80])
    body_hash = hashlib.sha256(norm_body.encode("utf-8")).hexdigest()[:16]

    return f"content:{norm_subject}|{body_hash}"

def cross_account_deduplicate(mails_list):
    """
    Performs cross-account deduplication.
    If an identical message is received in multiple accounts:
    1. Keeps the primary instance for AI summarization.
    2. Tags it with multi-account metadata (all_account_ids, all_account_labels, duplicate_ids).
    3. Returns (deduped_mails, duplicate_mappings).
    """
    deduped = []
    seen = {}
    duplicate_mappings = {}

    for mail in mails_list:
        fp = compute_email_fingerprint(mail)
        if fp in seen:
            primary_idx = seen[fp]
            primary = deduped[primary_idx]

            if "duplicate_ids" not in primary:
                primary["duplicate_ids"] = []
            primary["duplicate_ids"].append(mail["id"])

            if "all_account_ids" not in primary:
                primary["all_account_ids"] = [primary["account_id"]]
            if mail["account_id"] not in primary["all_account_ids"]:
                primary["all_account_ids"].append(mail["account_id"])

            if "all_account_labels" not in primary:
                primary["all_account_labels"] = [primary["account_label"]]
            if mail["account_label"] not in primary["all_account_labels"]:
                primary["all_account_labels"].append(mail["account_label"])

            duplicate_mappings[mail["id"]] = primary
            print(f"🔄 Cross-account duplicate detected: '{mail['subject']}' in [{mail['account_label']}] (linked to [{primary['account_label']}])")
        else:
            mail["all_account_ids"] = [mail["account_id"]]
            mail["all_account_labels"] = [mail["account_label"]]
            seen[fp] = len(deduped)
            deduped.append(mail)

    return deduped, duplicate_mappings

# --------------------------------------------------
# GEMINI SUMMARIZATION ENGINE
# --------------------------------------------------

def summarize_emails(ai_input, mode="morning"):
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY was not found in environment variables.")

    client = genai.Client(api_key=api_key)

    is_evening = (mode.lower() == "evening")
    briefing_context = (
        "Your job is to create a concise evening briefing and workday wrap-up across all user inboxes. "
        "Review today's correspondence, highlight pending responses, action items, and group newsletters into digest topics."
        if is_evening else
        "Your job is to create a concise morning briefing across all user inboxes. "
        "Put important security, account, payment, or urgent emails first, followed by today's action checklist and informational digest topics."
    )

    prompt = f"""
You are an AI email assistant.

Analyze the following emails from the user's inbox(es).
The emails are provided as JSON.

{briefing_context}

IMPORTANT RULES:
1. Put important security, account, payment, or urgent emails first.
2. Group related emails together when useful.
3. Clearly identify emails that explicitly require the user's action.
4. Separate actionable emails from informational emails.
5. Ignore unnecessary promotional details and boilerplate.
6. Summarize important emails in 1-2 sentences.
7. Do not invent information.
8. Do not make assumptions that are not supported by the email.
9. Only use information present in the provided emails.
10. If an email says something like "if this wasn't you", preserve that condition.
11. Do not automatically say the user must take action when the email only says to act if something was unauthorized or unexpected.
12. Clearly distinguish between:
    - action explicitly requested by the email
    - action that may be useful based on the email
13. Never invent an email ID.
14. Never modify an email ID.
15. Never omit the email ID when referring to a specific email.
16. Copy email IDs exactly as provided.
17. If there are no actions required, return an empty actions array.
18. For every summary, action, and reason, use only information explicitly present in the corresponding email.
19. Do not infer technical details, permissions, scopes, risks, motives, or consequences unless they are explicitly stated in the email.
20. The "reason" field must explain why the email itself indicates that the action may be relevant. Do not add external knowledge.
21. If the email does not provide enough information for a reason, use a simple statement based directly on the email.
22. Never report "open the email in an HTML-compatible client" as an action. Extract the underlying message.

MULTI-ACCOUNT & CROSS-ACCOUNT RULES:
23. Every item in "important", "needs_attention", and "actions" MUST contain the exact "account_id", "account_label", and "account_color_hex" matching the originating email in EMAILS.
24. If an email has "all_account_labels" with multiple accounts, summarize it ONCE, and note both accounts if relevant.
25. For items in "informational", populate "account_ids" (array) and "account_labels" (array) representing all accounts associated with the grouped emails.
26. Maintain strict separation of work vs personal priorities: prioritize urgent work action items during morning workdays, and keep personal urgent banking/security alerts prominently surfaced.

RETURN ONLY VALID JSON.
Do not use Markdown formatting.

Use exactly this JSON structure:
{{
    "morning_briefing": {{
        "important": [
            {{
                "id": "email_id",
                "account_id": "account_id",
                "account_label": "account_label",
                "account_color_hex": "#HEX_COLOR",
                "sender": "sender",
                "subject": "subject",
                "summary": "short summary",
                "action_required": true
            }}
        ],
        "needs_attention": [
            {{
                "id": "email_id",
                "account_id": "account_id",
                "account_label": "account_label",
                "account_color_hex": "#HEX_COLOR",
                "sender": "sender",
                "subject": "subject",
                "summary": "short summary",
                "action_required": true
            }}
        ],
        "informational": [
            {{
                "ids": ["email_id"],
                "account_ids": ["account_id"],
                "account_labels": ["account_label"],
                "topic": "group topic",
                "summary": "short grouped summary"
            }}
        ],
        "actions": [
            {{
                "id": "email_id",
                "account_id": "account_id",
                "account_label": "account_label",
                "account_color_hex": "#HEX_COLOR",
                "action": "action the user should consider",
                "reason": "why this action is relevant"
            }}
        ]
    }}
}}

EMAILS:
{ai_input}
"""

    # Robust model fallback list
    models_to_try = ["gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash"]
    last_error = None
    for model_name in models_to_try:
        try:
            print(f"Generating {mode} briefing with {model_name}...")
            response = client.models.generate_content(
                model=model_name,
                contents=prompt,
                config={
                    "response_mime_type": "application/json"
                }
            )
            if response.text and response.text.strip():
                return response.text.strip()
        except Exception as e:
            print(f"Notice: {model_name} attempt failed ({e}), trying next model...")
            last_error = e
            time.sleep(1)

    raise last_error or RuntimeError("All Gemini models failed to respond.")

def post_process_briefing(summary_data, emails_lookup, accounts):
    """
    Enforces contract v2.0 schema integrity:
    Ensures account_id, account_label, account_color_hex are populated across all briefing items.
    """
    acc_map = {acc["account_id"]: acc for acc in accounts}
    default_acc = accounts[0] if accounts else {
        "account_id": "default",
        "account_label": "PRIMARY",
        "account_color_hex": DEFAULT_PALETTE[0]
    }

    mb = summary_data.setdefault("morning_briefing", {})
    for category in ["important", "needs_attention"]:
        for item in mb.get(category, []):
            msg_id = item.get("id")
            detail = emails_lookup.get(msg_id, {})
            acc_id = item.get("account_id") or detail.get("account_id") or default_acc["account_id"]
            acc_info = acc_map.get(acc_id, default_acc)
            item["account_id"] = acc_id
            item["account_label"] = item.get("account_label") or detail.get("account_label") or acc_info.get("account_label", "PRIMARY")
            item["account_color_hex"] = item.get("account_color_hex") or detail.get("account_color_hex") or acc_info.get("account_color_hex", "#8B5CF6")

    for act in mb.get("actions", []):
        msg_id = act.get("id")
        detail = emails_lookup.get(msg_id, {})
        acc_id = act.get("account_id") or detail.get("account_id") or default_acc["account_id"]
        acc_info = acc_map.get(acc_id, default_acc)
        act["account_id"] = acc_id
        act["account_label"] = act.get("account_label") or detail.get("account_label") or acc_info.get("account_label", "PRIMARY")
        act["account_color_hex"] = act.get("account_color_hex") or detail.get("account_color_hex") or acc_info.get("account_color_hex", "#8B5CF6")

    for topic in mb.get("informational", []):
        ids = topic.get("ids", [])
        acc_ids = []
        acc_labels = []
        for mid in ids:
            detail = emails_lookup.get(mid, {})
            aid = detail.get("account_id")
            if aid and aid not in acc_ids:
                acc_ids.append(aid)
                acc_labels.append(detail.get("account_label", aid))
        if not acc_ids:
            if topic.get("account_ids"):
                acc_ids = topic["account_ids"]
            else:
                acc_ids = [default_acc["account_id"]]
        if not acc_labels:
            if topic.get("account_labels"):
                acc_labels = topic["account_labels"]
            else:
                acc_labels = [default_acc.get("account_label", "PRIMARY")]

        topic["account_ids"] = acc_ids
        topic["account_labels"] = acc_labels

    return summary_data

# --------------------------------------------------
# MAIN PIPELINE EXECUTION
# --------------------------------------------------

def run_briefing_pipeline(mode="auto", query="newer_than:1d", output_file=None, limit=None):
    if mode == "auto":
        current_hour = datetime.now().hour
        mode = "morning" if current_hour < 14 else "evening"

    print(f"🚀 Starting BriefingOS multi-account pipeline [Mode: {mode.upper()}]...")
    accounts = load_accounts_config()

    account_records = []
    all_fetched_mails = []
    emails_lookup = {}

    for idx, acc in enumerate(accounts):
        acc_id = acc.get("account_id", f"acc_{idx + 1}")
        acc_label = acc.get("account_label", "PRIMARY")
        acc_color = acc.get("account_color_hex", DEFAULT_PALETTE[idx % len(DEFAULT_PALETTE)])
        auth_index = acc.get("auth_index", idx)
        email_addr = acc.get("email_address", "")
        display_name = acc.get("display_name", acc_label)

        if not acc.get("enabled", True):
            print(f"⏭️ Skipping disabled account: '{acc_id}' [{acc_label}]")
            account_records.append({
                "account_id": acc_id,
                "email_address": email_addr,
                "display_name": display_name,
                "account_label": acc_label,
                "account_color_hex": acc_color,
                "auth_index": auth_index,
                "is_active": False,
                "last_sync_status": "DISABLED"
            })
            continue

        print(f"📥 [{acc_label}] Connecting to account '{acc_id}' ({email_addr or 'auto-discover'})...")
        try:
            service = get_service_for_account(acc)
            # Retrieve updated email address if discovered
            email_addr = acc.get("email_address", email_addr)

            account_query = acc.get("query") or query
            result = service.users().messages().list(
                userId="me",
                q=account_query
            ).execute()

            messages = result.get("messages", [])
            if limit and limit > 0:
                messages = messages[:limit]
            print(f"[{acc_label}] Found {len(messages)} recent messages.")

            for item in messages:
                msg_id = item["id"]
                try:
                    message = service.users().messages().get(
                        userId="me",
                        id=msg_id
                    ).execute()
                    headers = message.get("payload", {}).get("headers", [])

                    mail_obj = mail_object(msg_id, message, headers)
                    mail_obj["account_id"] = acc_id
                    mail_obj["account_label"] = acc_label
                    mail_obj["account_color_hex"] = acc_color
                    mail_obj["account_email"] = email_addr
                    mail_obj["auth_index"] = auth_index

                    all_fetched_mails.append(mail_obj)
                    emails_lookup[msg_id] = {
                        "id": msg_id,
                        "account_id": acc_id,
                        "account_email": email_addr,
                        "account_label": acc_label,
                        "account_color_hex": acc_color,
                        "auth_index": auth_index,
                        "sender": mail_obj["sender"],
                        "subject": mail_obj["subject"],
                        "date": mail_obj["date"],
                        "body": mail_obj["body"]
                    }
                except Exception as msg_err:
                    print(f"⚠️ Warning: Failed to fetch message {msg_id} in [{acc_label}]: {msg_err}")

            account_records.append({
                "account_id": acc_id,
                "email_address": email_addr,
                "display_name": display_name,
                "account_label": acc_label,
                "account_color_hex": acc_color,
                "auth_index": auth_index,
                "is_active": True,
                "last_sync_status": "OK"
            })

        except Exception as e:
            err_msg = str(e).lower()
            status = "EXPIRED" if ("invalid_grant" in err_msg or "token expired" in err_msg or "expired" in err_msg or "unauthorized" in err_msg) else "ERROR"
            print(f"❌ Error fetching from account '{acc_id}' [{acc_label}]: {e}. Marking as {status}.")
            account_records.append({
                "account_id": acc_id,
                "email_address": email_addr,
                "display_name": display_name,
                "account_label": acc_label,
                "account_color_hex": acc_color,
                "auth_index": auth_index,
                "is_active": False,
                "last_sync_status": status
            })

    active_count = sum(1 for a in account_records if a.get("is_active"))
    print(f"📊 Completed account ingestion. {active_count}/{len(account_records)} active account(s), {len(all_fetched_mails)} total email(s) fetched.")

    if not all_fetched_mails:
        print("No messages found across configured inboxes. Building empty briefing snapshot.")
        summary_data = {
            "briefing_type": mode,
            "generated_at": datetime.now().isoformat(),
            "active_account_count": active_count,
            "accounts": account_records,
            "morning_briefing": {
                "important": [],
                "needs_attention": [],
                "informational": [],
                "actions": []
            },
            "email_details": {}
        }
    else:
        # Cross-account deduplication
        deduped_mails, duplicate_mappings = cross_account_deduplicate(all_fetched_mails)
        print(f"📨 Ingestion queue: {len(deduped_mails)} unique email(s) ready for AI summarization.")

        # Serialize for Gemini prompt
        ai_input = json.dumps([{
            "id": m["id"],
            "account_id": m["account_id"],
            "account_label": m["account_label"],
            "account_color_hex": m["account_color_hex"],
            "all_account_labels": m.get("all_account_labels", [m["account_label"]]),
            "sender": m["sender"],
            "subject": m["subject"],
            "date": m["date"],
            "body": m["body"]
        } for m in deduped_mails], indent=2, ensure_ascii=False)

        summary_json = summarize_emails(ai_input, mode=mode)
        try:
            summary_data = json.loads(summary_json)
        except json.JSONDecodeError:
            clean_text = summary_json.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
            summary_data = json.loads(clean_text)

        summary_data = post_process_briefing(summary_data, emails_lookup, account_records)
        summary_data["active_account_count"] = active_count
        summary_data["accounts"] = account_records
        summary_data["email_details"] = emails_lookup
        summary_data["briefing_type"] = mode
        summary_data["generated_at"] = datetime.now().isoformat()

    target_path = output_file or DATA_PATH
    os.makedirs(os.path.dirname(target_path), exist_ok=True)
    with open(target_path, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=4, ensure_ascii=False)

    print(f"✅ {mode.capitalize()} briefing successfully generated with multi-account support and saved to {target_path}!")
    return summary_data

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Fetch and summarize emails with Gemini across multiple Gmail accounts")
    parser.add_argument("--mode", type=str, default="auto", choices=["auto", "morning", "evening"], help="Briefing mode")
    parser.add_argument("--query", type=str, default="newer_than:1d", help="Gmail search query")
    parser.add_argument("--output", type=str, default=None, help="Output JSON path")
    parser.add_argument("--limit", type=int, default=None, help="Maximum number of emails to process per account")
    args = parser.parse_args()

    run_briefing_pipeline(mode=args.mode, query=args.query, output_file=args.output, limit=args.limit)
