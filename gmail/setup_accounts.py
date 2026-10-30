#!/usr/bin/env python3
"""
BriefingOS // Multi-Account Setup Tool & Secret Generator (Option A)

Features:
- Authenticates multiple Gmail accounts via OAuth2 (RFC 8252 loopback).
- Auto-discovers profile email address (users().getProfile).
- Interactive prompts for label (PERSONAL, WORK, DEV) and color hex.
- Can import existing token.json/credentials.json seamlessly.
- Saves multi-account configuration to gmail/accounts_config.json.
- Generates base64-encoded string for GitHub Secret GMAIL_ACCOUNTS_JSON.
- Automatically pushes to GitHub Secrets via `gh secret set` if gh CLI is available.
"""

import os
import sys
import json
import base64
import shutil
import socket
import argparse
import subprocess
from typing import List, Dict, Any, Optional

# Ensure UTF-8 console output on Windows
if sys.platform.startswith('win'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

from google_auth_oauthlib.flow import InstalledAppFlow
from google.oauth2.credentials import Credentials
from google.auth.transport.requests import Request
from googleapiclient.discovery import build

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.dirname(BASE_DIR)
CREDS_PATH = os.path.join(BASE_DIR, "credentials.json")
TOKEN_PATH = os.path.join(BASE_DIR, "token.json")
ACCOUNTS_CONFIG_PATH = os.path.join(BASE_DIR, "accounts_config.json")

SCOPES = ["https://www.googleapis.com/auth/gmail.readonly"]

COLOR_PALETTE = [
    ("#8B5CF6", "Violet  [PERSONAL]"),
    ("#3B82F6", "Blue    [WORK]"),
    ("#10B981", "Emerald [DEV / LAB]"),
    ("#F59E0B", "Amber   [ALERTS]"),
    ("#EC4899", "Pink    [PROJECTS]"),
    ("#06B6D4", "Cyan    [FINANCE]"),
]


def is_port_available(port: int) -> bool:
    """Checks whether the specified TCP port is open for binding."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        try:
            s.bind(('127.0.0.1', port))
            return True
        except OSError:
            return False


def find_available_port(preferred: int = 8088) -> int:
    """Finds an available port starting from preferred."""
    for p in range(preferred, preferred + 20):
        if is_port_available(p):
            return p
    return 0  # 0 lets OS pick random open port


def extract_client_credentials(creds_data: Any) -> Dict[str, str]:
    """Extracts client_id, client_secret, token_uri from credentials dict or file."""
    if not isinstance(creds_data, dict):
        return {}
    target = creds_data
    if "installed" in creds_data:
        target = creds_data["installed"]
    elif "web" in creds_data:
        target = creds_data["web"]

    return {
        "client_id": target.get("client_id", ""),
        "client_secret": target.get("client_secret", ""),
        "token_uri": target.get("token_uri", "https://oauth2.googleapis.com/token")
    }


def load_existing_accounts() -> List[Dict[str, Any]]:
    """Loads accounts from gmail/accounts_config.json."""
    if not os.path.exists(ACCOUNTS_CONFIG_PATH):
        return []
    try:
        with open(ACCOUNTS_CONFIG_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, list):
                return data
    except Exception as e:
        print(f"⚠️ Notice: Could not read {ACCOUNTS_CONFIG_PATH}: {e}")
    return []


def save_accounts(accounts: List[Dict[str, Any]]) -> None:
    """Saves accounts list to gmail/accounts_config.json."""
    with open(ACCOUNTS_CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(accounts, f, indent=2, ensure_ascii=False)
    print(f"💾 Updated accounts configuration saved to: {ACCOUNTS_CONFIG_PATH}")


def fetch_account_profile(creds: Credentials) -> Dict[str, Any]:
    """Connects to Gmail API users().getProfile to discover user email and message count."""
    service = build("gmail", "v1", credentials=creds, cache_discovery=False)
    profile = service.users().getProfile(userId="me").execute()
    return profile


def run_oauth_flow(creds_file: str, port: int = 8088, open_browser: bool = True) -> Credentials:
    """Executes local OAuth 2.0 InstalledAppFlow."""
    if not os.path.exists(creds_file):
        raise FileNotFoundError(f"OAuth credentials file not found at: {creds_file}")

    actual_port = find_available_port(port)
    flow = InstalledAppFlow.from_client_secrets_file(creds_file, SCOPES)

    print(f"🌐 Initiating OAuth2 loopback server on http://127.0.0.1:{actual_port}/...")
    creds = flow.run_local_server(
        port=actual_port,
        open_browser=open_browser,
        prompt='consent',
        access_type='offline'
    )
    return creds


def add_account_interactive(creds_path: str = CREDS_PATH, open_browser: bool = True, port: int = 8088,
                           default_label: Optional[str] = None, default_color: Optional[str] = None) -> Optional[Dict[str, Any]]:
    """Interactive account addition flow."""
    print("\n" + "=" * 65)
    print("■ BRIEFINGOS // ADD GMAIL ACCOUNT VIA OAUTH2")
    print("=" * 65)

    if not os.path.exists(creds_path):
        print(f"❌ Error: {creds_path} does not exist!")
        print("Please place your Google Cloud OAuth Client credentials.json file in the 'gmail' directory.")
        return None

    try:
        creds = run_oauth_flow(creds_path, port=port, open_browser=open_browser)
    except Exception as e:
        print(f"❌ OAuth flow failed: {e}")
        return None

    print("\n🔍 Querying Gmail API for account identity...")
    try:
        profile = fetch_account_profile(creds)
        email_addr = profile.get("emailAddress", "unknown@gmail.com")
        messages_total = profile.get("messagesTotal", 0)
        print(f"✅ Authenticated: {email_addr} (Total Messages: {messages_total})")
    except Exception as e:
        print(f"⚠️ Could not fetch profile ({e}). Falling back to manual entry.")
        email_addr = input("Enter email address for this account: ").strip()

    existing_accounts = load_existing_accounts()
    existing_emails = [a.get("email_address") for a in existing_accounts]

    auth_index = len(existing_accounts)
    if email_addr in existing_emails:
        idx = existing_emails.index(email_addr)
        print(f"ℹ️ Account {email_addr} already exists at index {idx}. Will update existing entry.")
        auth_index = idx

    # Prompt for label
    suggested_label = default_label
    if not suggested_label:
        if auth_index == 0:
            suggested_label = "PERSONAL"
        elif auth_index == 1:
            suggested_label = "WORK"
        elif auth_index == 2:
            suggested_label = "DEV"
        else:
            suggested_label = f"ACCOUNT_{auth_index + 1}"

    if default_label:
        acc_label = default_label.upper()
    elif not sys.stdin or not sys.stdin.isatty():
        acc_label = suggested_label
    else:
        try:
            label_in = input(f"Account Label (e.g. PERSONAL, WORK, DEV, ALERTS) [{suggested_label}]: ").strip().upper()
            acc_label = label_in if label_in else suggested_label
        except (EOFError, OSError):
            acc_label = suggested_label

    # Prompt for color
    suggested_color = default_color
    if not suggested_color:
        suggested_color = COLOR_PALETTE[auth_index % len(COLOR_PALETTE)][0]

    if default_color:
        acc_color = default_color.upper()
    elif not sys.stdin or not sys.stdin.isatty():
        acc_color = suggested_color
    else:
        print("\nSelect Accent Color:")
        for idx, (hex_code, desc) in enumerate(COLOR_PALETTE, 1):
            indicator = "★ (default)" if hex_code == suggested_color else ""
            print(f"  [{idx}] {hex_code} - {desc} {indicator}")

        try:
            color_in = input(f"Choose color [1-{len(COLOR_PALETTE)}] or enter Hex code [{suggested_color}]: ").strip()
            if color_in.isdigit() and 1 <= int(color_in) <= len(COLOR_PALETTE):
                acc_color = COLOR_PALETTE[int(color_in) - 1][0]
            elif color_in.startswith("#") and len(color_in) in (4, 7):
                acc_color = color_in.upper()
            else:
                acc_color = suggested_color
        except (EOFError, OSError):
            acc_color = suggested_color

    # Read credentials file data
    with open(creds_path, "r", encoding="utf-8") as f:
        creds_json = json.load(f)
    client_creds = extract_client_credentials(creds_json)

    token_info = json.loads(creds.to_json())
    account_slug = acc_label.lower().replace(" ", "_")
    acc_id = f"acc_{account_slug}_{email_addr.split('@')[0]}"

    account_entry = {
        "account_id": acc_id,
        "email_address": email_addr,
        "display_name": f"{acc_label.title()} Mail",
        "account_label": acc_label,
        "account_color_hex": acc_color,
        "auth_index": auth_index,
        "enabled": True,
        "credentials": client_creds,
        "token": token_info
    }

    # Update or append
    updated = False
    for i, a in enumerate(existing_accounts):
        if a.get("email_address") == email_addr:
            existing_accounts[i] = account_entry
            updated = True
            break
    if not updated:
        existing_accounts.append(account_entry)

    save_accounts(existing_accounts)
    print(f"\n🎉 Successfully configured account [{acc_label}] ({email_addr})!")
    return account_entry


def import_legacy_token(token_file: str = TOKEN_PATH, creds_file: str = CREDS_PATH,
                        label: str = "PERSONAL", color: str = "#8B5CF6") -> Optional[Dict[str, Any]]:
    """Imports an existing token.json and credentials.json into accounts_config.json."""
    if not os.path.exists(token_file):
        print(f"❌ Error: {token_file} does not exist.")
        return None
    if not os.path.exists(creds_file):
        print(f"❌ Error: {creds_file} does not exist.")
        return None

    print(f"📦 Importing existing legacy credentials from {token_file}...")

    with open(creds_file, "r", encoding="utf-8") as f:
        creds_json = json.load(f)
    client_creds = extract_client_credentials(creds_json)

    with open(token_file, "r", encoding="utf-8") as f:
        token_info = json.load(f)

    # Ensure client credentials in token
    if not token_info.get("client_id") and client_creds.get("client_id"):
        token_info["client_id"] = client_creds["client_id"]
    if not token_info.get("client_secret") and client_creds.get("client_secret"):
        token_info["client_secret"] = client_creds["client_secret"]

    creds = Credentials.from_authorized_user_info(token_info, SCOPES)
    if creds.expired and creds.refresh_token:
        print("🔄 Refreshing expired token...")
        try:
            creds.refresh(Request())
            token_info = json.loads(creds.to_json())
        except Exception as e:
            print(f"⚠️ Failed to refresh token: {e}")

    try:
        profile = fetch_account_profile(creds)
        email_addr = profile.get("emailAddress", "primary@gmail.com")
        print(f"✅ Discovered account email: {email_addr}")
    except Exception as e:
        print(f"⚠️ Could not discover email via API: {e}")
        email_addr = "primary@gmail.com"

    existing_accounts = load_existing_accounts()
    acc_id = f"acc_{label.lower()}_{email_addr.split('@')[0]}"

    account_entry = {
        "account_id": acc_id,
        "email_address": email_addr,
        "display_name": f"{label.title()} Mail",
        "account_label": label,
        "account_color_hex": color,
        "auth_index": 0,
        "enabled": True,
        "credentials": client_creds,
        "token": token_info
    }

    # Upsert
    replaced = False
    for i, a in enumerate(existing_accounts):
        if a.get("email_address") == email_addr or a.get("account_id") == "default":
            existing_accounts[i] = account_entry
            replaced = True
            break
    if not replaced:
        existing_accounts.insert(0, account_entry)

    save_accounts(existing_accounts)
    print(f"🎉 Imported legacy account [{label}] ({email_addr}) into accounts_config.json!")
    return account_entry


def list_accounts() -> None:
    """Prints configured accounts."""
    accounts = load_existing_accounts()
    print("\n" + "=" * 70)
    print("■ BRIEFINGOS // CONFIGURED GMAIL ACCOUNTS")
    print("=" * 70)
    if not accounts:
        print("No accounts configured yet in gmail/accounts_config.json.")
        print("Run with --add or choose option [1] in the interactive menu to add an account.")
        return

    for idx, acc in enumerate(accounts):
        status_sym = "● ACTIVE" if acc.get("enabled", True) else "○ DISABLED"
        print(f"[{idx}] ID: {acc.get('account_id')}")
        print(f"    Label: [{acc.get('account_label')}] | Color: {acc.get('account_color_hex')}")
        print(f"    Email: {acc.get('email_address')}")
        print(f"    Name:  {acc.get('display_name')} | Status: {status_sym}")
        has_refresh = "Yes" if acc.get("token", {}).get("refresh_token") else "No"
        print(f"    OAuth Refresh Token Present: {has_refresh}")
        print("-" * 70)


def remove_account(account_id_or_email: str) -> bool:
    """Removes an account by ID or email address."""
    accounts = load_existing_accounts()
    before_len = len(accounts)
    accounts = [a for a in accounts if a.get("account_id") != account_id_or_email and a.get("email_address") != account_id_or_email]
    if len(accounts) < before_len:
        save_accounts(accounts)
        print(f"🗑️ Removed account '{account_id_or_email}'.")
        return True
    else:
        print(f"⚠️ Account '{account_id_or_email}' not found.")
        return False


def generate_base64_secret() -> str:
    """Generates the Base64 representation of accounts_config.json."""
    accounts = load_existing_accounts()
    if not accounts:
        raise ValueError("No accounts found in accounts_config.json! Please add at least one account first.")

    raw_json = json.dumps(accounts, indent=2)
    b64_str = base64.b64encode(raw_json.encode("utf-8")).decode("utf-8")
    return b64_str


def check_gh_cli() -> bool:
    """Checks if gh CLI is installed and authenticated."""
    if not shutil.which("gh"):
        return False
    try:
        res = subprocess.run(["gh", "auth", "status"], capture_output=True, text=True, timeout=5)
        return res.returncode == 0
    except Exception:
        return False


def set_github_secret(b64_secret: str) -> bool:
    """Pushes GMAIL_ACCOUNTS_JSON secret to GitHub repository using gh CLI."""
    if not check_gh_cli():
        print("⚠️ GitHub CLI (gh) is not installed or not authenticated.")
        return False

    print("🚀 Pushing GMAIL_ACCOUNTS_JSON to GitHub Secrets via gh CLI...")
    try:
        proc = subprocess.Popen(
            ["gh", "secret", "set", "GMAIL_ACCOUNTS_JSON"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            cwd=ROOT_DIR
        )
        stdout, stderr = proc.communicate(input=b64_secret, timeout=15)
        if proc.returncode == 0:
            print("✅ Successfully updated GitHub Secret: GMAIL_ACCOUNTS_JSON!")
            return True
        else:
            print(f"❌ Failed to set GitHub secret: {stderr.strip()}")
            return False
    except Exception as e:
        print(f"❌ Error running gh secret set: {e}")
        return False


def test_accounts_connection() -> None:
    """Tests authentication and API connection for all configured accounts."""
    accounts = load_existing_accounts()
    if not accounts:
        print("No accounts to test.")
        return

    print("\n🔍 Testing connections for all accounts:")
    print("=" * 60)
    for acc in accounts:
        acc_id = acc.get("account_id")
        label = acc.get("account_label")
        email = acc.get("email_address")
        token_info = acc.get("token")
        if not token_info:
            print(f"❌ [{label}] {email}: No token information found!")
            continue

        try:
            creds = Credentials.from_authorized_user_info(token_info, SCOPES)
            if creds.expired and creds.refresh_token:
                creds.refresh(Request())
            service = build("gmail", "v1", credentials=creds, cache_discovery=False)
            profile = service.users().getProfile(userId="me").execute()
            print(f"✅ [{label}] {profile.get('emailAddress')}: OK (Total Messages: {profile.get('messagesTotal')})")
        except Exception as e:
            print(f"❌ [{label}] {email}: ERROR ({e})")
    print("=" * 60)


def print_secret_export_guide(b64_str: str) -> None:
    """Displays export instructions."""
    print("\n" + "=" * 70)
    print("■ GMAIL_ACCOUNTS_JSON (BASE64 ENCODED SECRET)")
    print("=" * 70)
    print(b64_str)
    print("=" * 70)
    print("📋 Copy the string above and paste it into GitHub Repository Secrets:")
    print("   Repository -> Settings -> Secrets and variables -> Actions")
    print("   Name:  GMAIL_ACCOUNTS_JSON")
    print("   Value: <paste the base64 string above>")
    print("-" * 70)


def interactive_menu() -> None:
    """Interactive CLI menu."""
    while True:
        accounts = load_existing_accounts()
        print("\n" + "=" * 65)
        print("■ BRIEFINGOS // MULTI-ACCOUNT ONBOARDING & SECRET GENERATOR")
        print("=" * 65)
        print(f"Configuration File: {ACCOUNTS_CONFIG_PATH}")
        print(f"Configured Accounts: {len(accounts)}")
        for idx, a in enumerate(accounts):
            print(f"  • [{a.get('account_label')}] {a.get('email_address')} ({a.get('account_color_hex')})")
        print("-" * 65)
        print("[1] Add New Gmail Account (OAuth2 Flow)")
        print("[2] Import Existing Legacy token.json")
        print("[3] List Detailed Account Information")
        print("[4] Remove an Account")
        print("[5] Test / Verify All Account Connections")
        print("[6] Generate Base64 Secret for GMAIL_ACCOUNTS_JSON")
        print("[7] Push Secret Directly to GitHub (via gh CLI)")
        print("[8] Exit")
        print("-" * 65)

        choice = input("Select an option [1-8]: ").strip()
        if choice == "1":
            add_account_interactive()
        elif choice == "2":
            import_legacy_token()
        elif choice == "3":
            list_accounts()
        elif choice == "4":
            acc_id = input("Enter Account ID or Email to remove: ").strip()
            if acc_id:
                remove_account(acc_id)
        elif choice == "5":
            test_accounts_connection()
        elif choice == "6":
            try:
                b64 = generate_base64_secret()
                print_secret_export_guide(b64)
            except Exception as e:
                print(f"❌ Error: {e}")
        elif choice == "7":
            try:
                b64 = generate_base64_secret()
                set_github_secret(b64)
            except Exception as e:
                print(f"❌ Error: {e}")
        elif choice == "8" or choice.lower() in ("q", "exit"):
            print("Exiting. Keep your briefings sharp!")
            break
        else:
            print("Invalid option. Please choose between 1 and 8.")


def main():
    parser = argparse.ArgumentParser(description="BriefingOS Multi-Account Onboarding & Secret Generator")
    parser.add_argument("--add", action="store_true", help="Launch interactive OAuth flow to add an account")
    parser.add_argument("--import-legacy", action="store_true", help="Import legacy token.json / credentials.json")
    parser.add_argument("--list", action="store_true", help="List all configured accounts")
    parser.add_argument("--remove", type=str, metavar="ID_OR_EMAIL", help="Remove an account by ID or email")
    parser.add_argument("--test", action="store_true", help="Test connectivity of all configured accounts")
    parser.add_argument("--export-b64", action="store_true", help="Generate and print Base64 string for GMAIL_ACCOUNTS_JSON")
    parser.add_argument("--set-gh-secret", action="store_true", help="Push GMAIL_ACCOUNTS_JSON directly to GitHub Secrets")
    parser.add_argument("--label", type=str, help="Preset label when adding (e.g. WORK, DEV)")
    parser.add_argument("--color", type=str, help="Preset color hex when adding (e.g. #3B82F6)")
    parser.add_argument("--no-browser", action="store_true", help="Do not open browser automatically")
    parser.add_argument("--port", type=int, default=8088, help="Local loopback port for OAuth (default: 8088)")

    args = parser.parse_args()

    # If flags passed, run non-interactive / specific command
    if args.import_legacy:
        import_legacy_token(label=args.label or "PERSONAL", color=args.color or "#8B5CF6")
    elif args.add:
        add_account_interactive(
            open_browser=not args.no_browser,
            port=args.port,
            default_label=args.label,
            default_color=args.color
        )
    elif args.list:
        list_accounts()
    elif args.remove:
        remove_account(args.remove)
    elif args.test:
        test_accounts_connection()
    elif args.export_b64:
        try:
            b64 = generate_base64_secret()
            print_secret_export_guide(b64)
        except Exception as e:
            print(f"❌ Error: {e}")
            sys.exit(1)
    elif args.set_gh_secret:
        try:
            b64 = generate_base64_secret()
            success = set_github_secret(b64)
            if not success:
                print_secret_export_guide(b64)
                sys.exit(1)
        except Exception as e:
            print(f"❌ Error: {e}")
            sys.exit(1)
    else:
        interactive_menu()


if __name__ == "__main__":
    main()
