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

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# Load environment variables from both root and gmail
load_dotenv(os.path.join(BASE_DIR, ".env"))
load_dotenv(os.path.join(BASE_DIR, "gmail", ".env"))

DATA_PATH = os.path.join(BASE_DIR, "gmail", "data.json")

def format_gmail_link(email_id: str, auth_index: int = 0, account_email: str = "") -> str:
    """Constructs an account-aware deep-link to Gmail for web and mobile browsers."""
    if not email_id:
        return "https://mail.google.com"
    if account_email:
        return f"https://mail.google.com/mail/u/?authuser={account_email}#all/{email_id}"
    return f"https://mail.google.com/mail/u/{auth_index}/#all/{email_id}"

def build_discord_payload(data: dict, forced_mode: str = None) -> dict:
    mb = data.get("morning_briefing", {})
    important = mb.get("important", [])
    attention = mb.get("needs_attention", [])
    actions = mb.get("actions", [])
    info = mb.get("informational", [])
    accounts = data.get("accounts", [])
    email_details = data.get("email_details", {})

    def get_item_meta(item):
        mid = item.get("id", "")
        detail = email_details.get(mid, {})
        label = item.get("account_label") or detail.get("account_label", "")
        auth_idx = item.get("auth_index", detail.get("auth_index", 0))
        email_addr = item.get("account_email", detail.get("account_email", ""))
        return label, auth_idx, email_addr

    # Determine mode: morning vs evening
    briefing_type = forced_mode or data.get("briefing_type")
    if not briefing_type or briefing_type == "auto":
        current_hour = datetime.now().hour
        briefing_type = "morning" if current_hour < 14 else "evening"

    is_evening = (briefing_type.lower() == "evening")
    now_str = datetime.now().strftime("%A, %B %d, %Y")

    embed_fields = []

    # 1. Action Items Field (High priority)
    if actions:
        action_lines = []
        for i, act in enumerate(actions[:5], start=1):
            acc_label, auth_idx, acc_email = get_item_meta(act)
            acc_tag = f"`[{acc_label}]` " if acc_label else ""
            gmail_url = format_gmail_link(act.get("id", ""), auth_idx, acc_email)
            action_lines.append(f"**{i}.** {acc_tag}{act.get('action')} — [✉️ Open in Gmail]({gmail_url})")
            if act.get("reason"):
                action_lines.append(f"> *Why:* {act.get('reason')}")

        header_label = "📋 Pending Wrap-Up Actions" if is_evening else "📋 Recommended Actions"
        embed_fields.append({
            "name": f"{header_label} ({len(actions)})",
            "value": "\n".join(action_lines)[:1024],
            "inline": False
        })

    # 2. Security Alerts / Critical
    if important:
        sec_lines = []
        for item in important[:3]:
            acc_label, auth_idx, acc_email = get_item_meta(item)
            acc_tag = f"`[{acc_label}]` " if acc_label else ""
            gmail_url = format_gmail_link(item.get("id", ""), auth_idx, acc_email)
            sec_lines.append(f"🚨 {acc_tag}**{item.get('subject')}**\nFrom: `{item.get('sender')}`\n{item.get('summary')}\n[✉️ View Email]({gmail_url})")

        embed_fields.append({
            "name": f"🚨 Security & Critical ({len(important)})",
            "value": "\n\n".join(sec_lines)[:1024],
            "inline": False
        })

    # 3. Needs Attention
    if attention:
        att_lines = []
        for item in attention[:3]:
            acc_label, auth_idx, acc_email = get_item_meta(item)
            acc_tag = f"`[{acc_label}]` " if acc_label else ""
            gmail_url = format_gmail_link(item.get("id", ""), auth_idx, acc_email)
            att_lines.append(f"⚡ {acc_tag}**{item.get('subject')}**\nFrom: `{item.get('sender')}` — [✉️ Open]({gmail_url})")

        header_label = "⚡ Follow-Ups & Responses" if is_evening else "⚡ Needs Attention"
        embed_fields.append({
            "name": f"{header_label} ({len(attention)})",
            "value": "\n\n".join(att_lines)[:1024],
            "inline": False
        })

    # 4. Informational Highlights / Reading Digest
    if info:
        info_lines = []
        for item in info[:4]:
            topic = item.get("topic")
            count = len(item.get("ids", []))
            summary = item.get("summary", "")
            summary_part = f" — *{summary}*" if summary else ""
            account_labels = item.get("account_labels", [])
            acc_tag = ""
            if account_labels:
                acc_tag = "`[" + ", ".join(account_labels) + "]` "
            elif item.get("account_label"):
                acc_tag = f"`[{item.get('account_label')}]` "
            info_lines.append(f"• {acc_tag}**{topic}** ({count} email{'s' if count > 1 else ''}){summary_part}")

        digest_label = "📰 Evening Reading & Digest" if is_evening else "📰 Digest Topics"
        embed_fields.append({
            "name": f"{digest_label} ({len(info)})",
            "value": "\n".join(info_lines)[:1024],
            "inline": False
        })

    # Connected accounts summary line
    account_summary = ""
    active_inboxes = [a for a in accounts if a.get("is_active", True)]
    if len(active_inboxes) > 1:
        pills = [f"`[{a.get('account_label', 'ACC')}]` {a.get('email_address') or a.get('display_name')}" for a in active_inboxes]
        account_summary = f"\n📬 **Inboxes ({len(pills)}):** " + " • ".join(pills)

    # Theme selection based on mode
    if is_evening:
        title = "🌆 Daily Evening Email Wrap-Up"
        color = 0x8B5CF6  # Violet / Purple
        badge = "Workday Wrap-Up"
    else:
        title = "🌅 Daily Morning Email Briefing"
        color = 0x3B82F6  # Tailwind Blue
        badge = "Daily Kickoff"

    active_count = len(active_inboxes)
    footer_text = f"BriefingOS • Multi-Account ({active_count} inboxes active)" if active_count > 1 else "BriefingOS • Sent automatically"

    payload = {
        "username": "AI Mail Assistant",
        "avatar_url": "https://cdn-icons-png.flaticon.com/512/281/281769.png",
        "embeds": [
            {
                "title": title,
                "description": f"**Date:** {now_str} • `{badge}`{account_summary}\n📊 **Summary:** `{len(important)} Critical` | `{len(attention)} Attention` | `{len(actions)} Actions` | `{len(info)} Topics`",
                "color": color,
                "fields": embed_fields,
                "footer": {
                    "text": footer_text,
                    "icon_url": "https://cdn-icons-png.flaticon.com/512/281/281769.png"
                },
                "timestamp": datetime.utcnow().isoformat() + "Z"
            }
        ]
    }

    return payload

def send_briefing_to_discord(webhook_url: str = None, data_file: str = None, mode: str = "auto", fetch: bool = False):
    url = webhook_url or os.getenv("DISCORD_WEBHOOK_URL")
    if not url or "discord.com/api/webhooks" not in url:
        raise ValueError(
            "Missing or invalid DISCORD_WEBHOOK_URL. Please set it in .env or pass it as an argument."
        )

    file_to_read = data_file or DATA_PATH

    if fetch:
        print("🔄 Fetching fresh emails and regenerating briefing before push...")
        try:
            from gmail.gmail import run_briefing_pipeline
            run_briefing_pipeline(mode=mode, output_file=file_to_read)
        except Exception as e:
            print(f"⚠️ Ingestion pipeline warning: {e}. Falling back to existing data if available.")

    if not os.path.exists(file_to_read):
        raise FileNotFoundError(f"Could not find briefing file at: {file_to_read}")

    with open(file_to_read, "r", encoding="utf-8") as f:
        data = json.load(f)

    payload = build_discord_payload(data, forced_mode=mode)
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
                mode_str = mode if mode != "auto" else data.get("briefing_type", "daily")
                print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ {mode_str.capitalize()} briefing successfully pushed to Discord!")
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

def run_two_phase_scheduler(schedule_times=("08:00", "18:30")):
    """Runs a local persistent daemon triggering morning briefing at schedule_times[0] and evening wrap-up at schedule_times[1]."""
    morning_time = schedule_times[0].strip()
    evening_time = schedule_times[1].strip() if len(schedule_times) > 1 else "18:30"

    print("=================================================================")
    print("🚀 BriefingOS Dual-Schedule Dispatcher is ACTIVE.")
    print(f"🌅 Morning Briefing scheduled for: {morning_time} IST")
    print(f"🌆 Evening Wrap-Up scheduled for: {evening_time} IST")
    print("Zero-latency local scheduler running. Press Ctrl+C to stop.")
    print("=================================================================")

    last_trigger_date = {"morning": None, "evening": None}

    while True:
        now = datetime.now()
        current_time = now.strftime("%H:%M")
        today_date = now.strftime("%Y-%m-%d")

        if current_time == morning_time and last_trigger_date["morning"] != today_date:
            print(f"🔔 [{current_time}] Triggering Morning Briefing with fresh email fetch...")
            try:
                send_briefing_to_discord(mode="morning", fetch=True)
                last_trigger_date["morning"] = today_date
            except Exception as e:
                print(f"Error during morning dispatch: {e}")

        if current_time == evening_time and last_trigger_date["evening"] != today_date:
            print(f"🔔 [{current_time}] Triggering Evening Wrap-Up with fresh email fetch...")
            try:
                send_briefing_to_discord(mode="evening", fetch=True)
                last_trigger_date["evening"] = today_date
            except Exception as e:
                print(f"Error during evening dispatch: {e}")

        time.sleep(20)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Send AI Mail Assistant briefing to Discord")
    parser.add_argument("--webhook", type=str, help="Discord Webhook URL (or set in .env)")
    parser.add_argument("--mode", type=str, default="auto", choices=["auto", "morning", "evening"], help="Briefing mode")
    parser.add_argument("--fetch", action="store_true", help="Fetch fresh emails and re-generate before pushing")
    parser.add_argument("--schedule", type=str, nargs="?", const="08:00,18:30", help="Run dual schedule loop (e.g. '08:00,18:30')")
    parser.add_argument("--data", type=str, default=None, help="Path to data.json")

    args = parser.parse_args()

    if args.schedule:
        times = [t.strip() for t in args.schedule.split(",") if t.strip()]
        run_two_phase_scheduler(tuple(times))
    else:
        send_briefing_to_discord(
            webhook_url=args.webhook,
            data_file=args.data,
            mode=args.mode,
            fetch=args.fetch
        )
