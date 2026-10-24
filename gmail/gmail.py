import os
import sys
import base64
import json
import time
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

# Load environment variables from both gmail/.env and root .env
load_dotenv(os.path.join(ROOT_DIR, ".env"))
load_dotenv(os.path.join(BASE_DIR, ".env"))

TOKEN_PATH = os.path.join(BASE_DIR, "token.json")
CREDS_PATH = os.path.join(BASE_DIR, "credentials.json")
DATA_PATH = os.path.join(BASE_DIR, "data.json")

SCOPES = [
    "https://www.googleapis.com/auth/gmail.readonly"
]

def restore_secrets_from_env():
    """Restores token.json and credentials.json from environment variables if present (CI/CD environments)."""
    env_token = os.getenv("GMAIL_TOKEN")
    if env_token and not os.path.exists(TOKEN_PATH):
        try:
            # Try decoding base64 first
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

def get_gmail_service():
    """Initializes and returns an authorized Gmail API service instance."""
    restore_secrets_from_env()

    credentials = None

    if os.path.exists(TOKEN_PATH):
        credentials = Credentials.from_authorized_user_file(TOKEN_PATH, SCOPES)
        # Auto-refresh expired access tokens using the refresh_token
        if credentials and credentials.expired and credentials.refresh_token:
            print("Access token expired. Refreshing token via Google OAuth...")
            try:
                credentials.refresh(Request())
                with open(TOKEN_PATH, "w", encoding="utf-8") as tok:
                    tok.write(credentials.to_json())
                print("Google OAuth token refreshed and saved.")
            except Exception as e:
                print(f"Notice: Token refresh failed ({e}). Proceeding if still valid.")

    if not credentials or not credentials.valid:
        if os.path.exists(CREDS_PATH):
            print("Interactive authentication required...")
            flow = InstalledAppFlow.from_client_secrets_file(CREDS_PATH, SCOPES)
            credentials = flow.run_local_server(port=0)
            with open(TOKEN_PATH, "w", encoding="utf-8") as tok:
                tok.write(credentials.to_json())
        else:
            raise FileNotFoundError(
                f"Missing authentication: Neither {TOKEN_PATH} nor {CREDS_PATH} was found."
            )

    return build("gmail", "v1", credentials=credentials)

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
        "body": content_gater(message.get("payload", {}))
    }

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
        "Your job is to create a concise evening briefing and workday wrap-up. "
        "Review today's correspondence, highlight pending responses, action items, and group newsletters into digest topics."
        if is_evening else
        "Your job is to create a concise morning briefing. "
        "Put important security, account, payment, or urgent emails first, followed by today's action checklist and informational digest topics."
    )

    prompt = f"""
You are an AI email assistant.

Analyze the following emails from the user's inbox.
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

RETURN ONLY VALID JSON.
Do not use Markdown formatting.

Use exactly this JSON structure:
{{
    "morning_briefing": {{
        "important": [
            {{
                "id": "email_id",
                "sender": "sender",
                "subject": "subject",
                "summary": "short summary",
                "action_required": true
            }}
        ],
        "needs_attention": [
            {{
                "id": "email_id",
                "sender": "sender",
                "subject": "subject",
                "summary": "short summary",
                "action_required": true
            }}
        ],
        "informational": [
            {{
                "ids": ["email_id"],
                "topic": "group topic",
                "summary": "short grouped summary"
            }}
        ],
        "actions": [
            {{
                "id": "email_id",
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

# --------------------------------------------------
# MAIN PIPELINE EXECUTION
# --------------------------------------------------

def run_briefing_pipeline(mode="auto", query="newer_than:1d", output_file=None):
    if mode == "auto":
        current_hour = datetime.now().hour
        mode = "morning" if current_hour < 14 else "evening"

    print(f"🚀 Starting BriefingOS pipeline [Mode: {mode.upper()}]...")
    service = get_gmail_service()

    print(f"📥 Querying Gmail messages with query '{query}'...")
    result = service.users().messages().list(
        userId="me",
        q=query
    ).execute()

    messages = result.get("messages", [])
    print(f"Found {len(messages)} recent messages.")

    if not messages:
        print("No messages found in time window. Building empty briefing snapshot.")
        summary_data = {
            "briefing_type": mode,
            "generated_at": datetime.now().isoformat(),
            "morning_briefing": {
                "important": [],
                "needs_attention": [],
                "informational": [],
                "actions": []
            },
            "email_details": {}
        }
    else:
        mails_list = []
        emails_lookup = {}

        for item in messages:
            msg_id = item["id"]
            message = service.users().messages().get(
                userId="me",
                id=msg_id
            ).execute()
            headers = message.get("payload", {}).get("headers", [])

            mail_obj = mail_object(msg_id, message, headers)
            mails_list.append(mail_obj)
            emails_lookup[msg_id] = {
                "id": msg_id,
                "sender": mail_obj["sender"],
                "subject": mail_obj["subject"],
                "date": mail_obj["date"],
                "body": mail_obj["body"]
            }

        ai_input = json.dumps(mails_list, indent=2, ensure_ascii=False)

        summary_json = summarize_emails(ai_input, mode=mode)
        try:
            summary_data = json.loads(summary_json)
        except json.JSONDecodeError:
            # Strip backticks if returned
            clean_text = summary_json.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
            summary_data = json.loads(clean_text)

        summary_data["email_details"] = emails_lookup
        summary_data["briefing_type"] = mode
        summary_data["generated_at"] = datetime.now().isoformat()

    target_path = output_file or DATA_PATH
    os.makedirs(os.path.dirname(target_path), exist_ok=True)
    with open(target_path, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=4, ensure_ascii=False)

    print(f"✅ {mode.capitalize()} briefing successfully generated and saved to {target_path}!")
    return summary_data

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Fetch and summarize emails with Gemini")
    parser.add_argument("--mode", type=str, default="auto", choices=["auto", "morning", "evening"], help="Briefing mode")
    parser.add_argument("--query", type=str, default="newer_than:1d", help="Gmail search query")
    parser.add_argument("--output", type=str, default=None, help="Output JSON path")
    args = parser.parse_args()

    run_briefing_pipeline(mode=args.mode, query=args.query, output_file=args.output)
