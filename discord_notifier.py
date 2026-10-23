import os
import sys
import json
import urllib.request
import urllib.error
import argparse
import time
from datetime import datetime
from dotenv import load_dotenv

# Ensure UTF-8 output on Windows
if sys.platform.startswith('win'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

# Load environment variables
load_dotenv()

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(BASE_DIR, "gmail", "data.json")

def format_gmail_link(email_id: str) -> str:
    return f"https://mail.google.com/mail/u/0/#all/{email_id}"

def build_discord_payload(data: dict) -> dict:
    mb = data.get("morning_briefing", {})
    important = mb.get("important", [])
    attention = mb.get("needs_attention", [])
    actions = mb.get("actions", [])
    info = mb.get("informational", [])

    now_str = datetime.now().strftime("%A, %B %d, %Y")

    embed_fields = []

    # 1. Action Items Field (High priority)
    if actions:
        action_lines = []
        for i, act in enumerate(actions[:4], start=1):
            gmail_url = format_gmail_link(act.get("id", ""))
            action_lines.append(f"**{i}.** {act.get('action')} — [✉️ Open in Gmail]({gmail_url})")
            if act.get("reason"):
                action_lines.append(f"> *Why:* {act.get('reason')}")
        
        embed_fields.append({
            "name": f"📋 Recommended Actions ({len(actions)})",
            "value": "\n".join(action_lines)[:1024],
            "inline": False
        })

    # 2. Security Alerts
    if important:
        sec_lines = []
        for item in important[:3]:
            gmail_url = format_gmail_link(item.get("id", ""))
            sec_lines.append(f"🚨 **{item.get('subject')}**\nFrom: `{item.get('sender')}`\n{item.get('summary')}\n[✉️ View Email]({gmail_url})")
        
        embed_fields.append({
            "name": f"🚨 Security & Critical ({len(important)})",
            "value": "\n\n".join(sec_lines)[:1024],
            "inline": False
        })

    # 3. Needs Attention
    if attention:
        att_lines = []
        for item in attention[:3]:
            gmail_url = format_gmail_link(item.get("id", ""))
            att_lines.append(f"⚡ **{item.get('subject')}**\nFrom: `{item.get('sender')}` — [✉️ Open]({gmail_url})")
        
        embed_fields.append({
            "name": f"⚡ Needs Attention ({len(attention)})",
            "value": "\n\n".join(att_lines)[:1024],
            "inline": False
        })

    # 4. Informational Highlights
    if info:
        info_lines = []
        for item in info[:4]:
            topic = item.get("topic")
            count = len(item.get("ids", []))
            info_lines.append(f"• **{topic}** ({count} email{'s' if count > 1 else ''})")
        
        embed_fields.append({
            "name": f"📰 Digest Topics ({len(info)})",
            "value": "\n".join(info_lines)[:1024],
            "inline": False
        })

    payload = {
        "username": "AI Mail Assistant",
        "avatar_url": "https://cdn-icons-png.flaticon.com/512/281/281769.png",
        "embeds": [
            {
                "title": f"📬 Daily Morning Email Briefing",
                "description": f"**Date:** {now_str}\n📊 **Summary:** `{len(important)} Critical` | `{len(attention)} Attention` | `{len(actions)} Actions` | `{len(info)} Topics`",
                "color": 0x3B82F6, # Tailwind blue
                "fields": embed_fields,
                "footer": {
                    "text": "BriefingOS • Sent automatically",
                    "icon_url": "https://cdn-icons-png.flaticon.com/512/281/281769.png"
                },
                "timestamp": datetime.utcnow().isoformat() + "Z"
            }
        ]
    }

    return payload

def send_briefing_to_discord(webhook_url: str = None, data_file: str = None):
    url = webhook_url or os.getenv("DISCORD_WEBHOOK_URL")
    if not url or "discord.com/api/webhooks" not in url:
        raise ValueError(
            "Missing or invalid DISCORD_WEBHOOK_URL. Please set it in .env or pass it as an argument."
        )

    file_to_read = data_file or DATA_PATH
    if not os.path.exists(file_to_read):
        raise FileNotFoundError(f"Could not find briefing file at: {file_to_read}")

    with open(file_to_read, "r", encoding="utf-8") as f:
        data = json.load(f)

    payload = build_discord_payload(data)
    json_data = json.dumps(payload).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=json_data,
        headers={
            "Content-Type": "application/json",
            "User-Agent": "AI-Mail-Assistant-Discord-Notifier/1.0"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req) as response:
            if response.status in [200, 204]:
                print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ Daily briefing successfully pushed to Discord!")
                return True
            else:
                print(f"Unexpected response status: {response.status}")
                return False
    except urllib.error.HTTPError as e:
        print(f"❌ Failed to push to Discord: HTTP {e.code} - {e.reason}")
        print(e.read().decode("utf-8", errors="replace"))
        return False
    except Exception as e:
        print(f"❌ Error sending Discord notification: {e}")
        return False

def run_daily_scheduler(target_time="08:00"):
    print(f"🚀 AI Mail Assistant Discord Scheduler is ACTIVE.")
    print(f"⏰ Daily briefing scheduled to send every morning at: {target_time}")
    print("Press Ctrl+C to stop.")

    sent_today = False

    while True:
        now = datetime.now()
        current_time = now.strftime("%H:%M")

        if current_time == target_time and not sent_today:
            print(f"🔔 It is {current_time}! Sending morning briefing to Discord...")
            try:
                send_briefing_to_discord()
                sent_today = True
            except Exception as e:
                print(f"Error during scheduled dispatch: {e}")

        # Reset flag after target minute passes
        if current_time != target_time and sent_today:
            sent_today = False

        time.sleep(25)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Send AI Mail Assistant briefing to Discord")
    parser.add_argument("--webhook", type=str, help="Discord Webhook URL (or set in .env)")
    parser.add_argument("--schedule", type=str, nargs="?", const="08:00", help="Run in daily schedule loop (default: 08:00)")
    parser.add_argument("--test", action="store_true", help="Send a test notification right now")

    args = parser.parse_args()

    if args.schedule:
        run_daily_scheduler(args.schedule)
    else:
        send_briefing_to_discord(webhook_url=args.webhook)
