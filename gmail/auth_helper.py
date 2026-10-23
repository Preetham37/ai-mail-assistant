import os
import sys
from google_auth_oauthlib.flow import InstalledAppFlow

if sys.platform.startswith('win'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SCOPES = ["https://www.googleapis.com/auth/gmail.readonly"]

def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    creds_path = os.path.join(base_dir, "credentials.json")
    token_path = os.path.join(base_dir, "token.json")

    flow = InstalledAppFlow.from_client_secrets_file(
        creds_path,
        SCOPES
    )

    # run_local_server will print:
    # "Please visit this URL to authorize this application: https://..."
    creds = flow.run_local_server(
        port=8088,
        open_browser=False,
        prompt='consent',
        access_type='offline'
    )

    with open(token_path, "w", encoding="utf-8") as f:
        f.write(creds.to_json())

    print("\n✅ Google authentication successful! token.json has been created.")

if __name__ == "__main__":
    main()
