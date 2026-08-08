import os
import json

from google_auth_oauthlib.flow import InstalledAppFlow
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build

SCOPES = ["https://www.googleapis.com/auth/gmail.readonly"]


if os.path.exists("token.json"):
    credentials = Credentials.from_authorized_user_file("token.json",SCOPES)
    print("it works")
else:
    flow = InstalledAppFlow.from_client_secrets_file("credentials.json",SCOPES)
    credentials = flow.run_local_server(port=0)
    with open("token.json","w") as token:
        token.write(credentials.to_json())

service = build("gmail","v1",credentials=credentials)

result = service.users().messages().list(userId="me").execute()

print(result)


