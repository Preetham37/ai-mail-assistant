import os
import sys
import base64
import json
import time

# Ensure UTF-8 console output on Windows
if sys.platform.startswith('win'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

from google_auth_oauthlib.flow import InstalledAppFlow
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from bs4 import BeautifulSoup
from google import genai
from dotenv import load_dotenv
from pydantic import BaseModel


load_dotenv()

mailsList = []

SCOPES = [
    "https://www.googleapis.com/auth/gmail.readonly"
]


# --------------------------------------------------
# GOOGLE AUTHENTICATION
# --------------------------------------------------

if os.path.exists("token.json"):

    credentials = Credentials.from_authorized_user_file(
        "token.json",
        SCOPES
    )

    print("completed google security check")

else:

    flow = InstalledAppFlow.from_client_secrets_file(
        "credentials.json",
        SCOPES
    )

    credentials = flow.run_local_server(port=0)

    with open("token.json", "w") as tok:
        tok.write(credentials.to_json())


# --------------------------------------------------
# GMAIL SERVICE
# --------------------------------------------------

service = build(
    "gmail",
    "v1",
    credentials=credentials
)


result = service.users().messages().list(
    userId="me",
    q="newer_than:2d"
).execute()


# --------------------------------------------------
# DECODE EMAIL DATA
# --------------------------------------------------

def decode_data(data):

    decoded = base64.urlsafe_b64decode(data)

    text = decoded.decode(
        "utf-8",
        errors="replace"
    )

    return text


# --------------------------------------------------
# CLEAN HTML (STRIP ALL TAGS AND STYLES)
# --------------------------------------------------

def clean_html(html):

    soup = BeautifulSoup(
        html,
        "html.parser"
    )

    # Remove all scripts, styles, metadata, and stylesheets
    for tag in soup(["script", "style", "head", "meta", "noscript", "link"]):
        tag.decompose()

    # Ensure space between block tags
    for block in soup.find_all(["br", "p", "div", "tr", "li", "h1", "h2", "h3", "h4", "h5", "h6"]):
        block.append(" ")

    text = soup.get_text(
        " ",
        strip=True
    )

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

                        plain_text.append(
                            decode_data(data[i])
                        )

                    elif mimeType == "text/html":

                        html_body.append(
                            clean_html(
                                decode_data(data[i])
                            )
                        )

                elif isinstance(data[i], dict):

                    current_mime = mimeType

                    if "mimeType" in data:

                        current_mime = data["mimeType"]

                    explore(
                        data[i],
                        current_mime
                    )

                elif isinstance(data[i], list):

                    explore(
                        data[i],
                        mimeType
                    )

        elif isinstance(data, list):

            for item in data:

                explore(
                    item,
                    mimeType
                )

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

    return None


# --------------------------------------------------
# GET HEADER INFORMATION
# --------------------------------------------------

def get_info_header(header, name):
    for i in header:
        if i["name"].lower() == name.lower():
            return i["value"]
    return None


# --------------------------------------------------
# CREATE EMAIL OBJECT
# --------------------------------------------------

def mail_object(message_id, message, header):

    new_mail_object = {

        "id": message_id,

        "sender": get_info_header(
            header,
            "From"
        ),

        "subject": get_info_header(
            header,
            "Subject"
        ),

        "date": get_info_header(
            header,
            "Date"
        ),

        "body": content_gater(message)

    }

    return new_mail_object


# --------------------------------------------------
# GET ALL EMAILS
# --------------------------------------------------

def list_all_mails():

    mail = result.get(
        "messages",
        []
    )

    for i in mail:
        message = service.users().messages().get(
            userId="me",
            id=i["id"]
        ).execute()
        header = message["payload"]["headers"]

        mailsList.append(
            mail_object(
                i["id"],
                message,
                header
            )
        )


list_all_mails()


# --------------------------------------------------
# CONVERT EMAILS TO JSON
# --------------------------------------------------

def prepare_for_ai(mails):

    return json.dumps(
        mails,
        indent=2,
        ensure_ascii=False
    )


ai_input = prepare_for_ai(
    mailsList
)



# --------------------------------------------------
# GEMINI
# --------------------------------------------------

api_key = os.getenv(
    "GEMINI_API_KEY"
)

if not api_key:

    raise ValueError(
        "GEMINI_API_KEY was not found in .env"
    )


client = genai.Client(
    api_key=api_key
)


# --------------------------------------------------
# SUMMARIZE EMAILS
# --------------------------------------------------

def summarize_emails(ai_input):

    prompt = f"""
You are an AI email assistant.

Analyze the following emails from the last 48 hours.

The emails are provided as JSON.

Your job is to create a concise morning briefing.

IMPORTANT RULES:

1. Put important security, account, payment,
   or urgent emails first.

2. Group related emails together when useful.

3. Clearly identify emails that explicitly require
   the user's action.

4. Separate actionable emails from informational emails.

5. Ignore unnecessary promotional details and boilerplate.

6. Summarize important emails in 1-2 sentences.

7. Do not invent information.

8. Do not make assumptions that are not supported
   by the email.

9. Only use information present in the provided emails.

10. If an email says something like
    "if this wasn't you", preserve that condition.

11. Do not automatically say the user must take action
    when the email only says to act if something
    was unauthorized or unexpected.

12. Clearly distinguish between:
    - action explicitly requested by the email
    - action that may be useful based on the email

13. Never invent an email ID.

14. Never modify an email ID.

15. Never omit the email ID when referring
    to a specific email.

16. Copy email IDs exactly as provided.

17. If there are no actions required,
    return an empty actions array.

18. For every summary, action, and reason, use only information explicitly
    present in the corresponding email.

19. Do not infer technical details, permissions, scopes, risks, motives,
    or consequences unless they are explicitly stated in the email.

20. The "reason" field must explain why the email itself indicates
    that the action may be relevant. Do not add external knowledge.

21. If the email does not provide enough information for a reason,
    use a simple statement based directly on the email.

22. Never report "open the email in an HTML-compatible client" as an action. Extract the underlying message.

RETURN ONLY VALID JSON.

Do not use Markdown.

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

    models_to_try = ["gemini-3.8-flash", "gemini-3.5-flash"]
    last_error = None
    for model_name in models_to_try:
        try:
            print(f"Generating briefing with {model_name}...")
            response = client.models.generate_content(
                model=model_name,
                contents=prompt,
                config={
                    "response_mime_type": "application/json"
                }
            )
            return response.text
        except Exception as e:
            print(f"Notice: {model_name} unavailable ({e}), trying next model...")
            last_error = e
            time.sleep(1)

    raise last_error


# --------------------------------------------------
# GET GEMINI RESPONSE
# --------------------------------------------------

summary = summarize_emails(
    ai_input
)

# --------------------------------------------------
# CONVERT GEMINI RESPONSE TO PYTHON DICTIONARY
# --------------------------------------------------

try:

    summary_data = json.loads(summary)

except json.JSONDecodeError:

    print("Gemini did not return valid JSON.")

    print("\nRaw response:")
    print(summary)

    exit()


# --------------------------------------------------
# ATTACH FULL EMAIL BODIES FOR IN-APP READER
# --------------------------------------------------

emails_lookup = {}
for m in mailsList:
    emails_lookup[m["id"]] = {
        "id": m["id"],
        "sender": m.get("sender"),
        "subject": m.get("subject"),
        "date": m.get("date"),
        "body": m.get("body")
    }

summary_data["email_details"] = emails_lookup

# --------------------------------------------------
# SAVE JSON
# --------------------------------------------------

output_path = os.path.join(os.path.dirname(__file__), "data.json")
with open(output_path, "w", encoding="utf-8") as data:
    json.dump(summary_data, data, indent=4)

print("✅ Morning briefing successfully updated in data.json with full email bodies")
