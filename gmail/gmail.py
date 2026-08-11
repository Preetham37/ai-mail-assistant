import os
import base64


from google_auth_oauthlib.flow import InstalledAppFlow
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build

mailsList=[]


SCOPES = ["https://www.googleapis.com/auth/gmail.readonly"]

if os.path.exists("token.json"):
    credentials = Credentials.from_authorized_user_file("token.json",SCOPES)
    print("completed google security check")
else:
    flow = InstalledAppFlow.from_client_secrets_file("credentials.json", SCOPES)
    credentials= flow.run_local_server(port=0)
    with open("token.json","w") as tok:
        tok.write(credentials.to_json())



service = build("gmail","v1",credentials=credentials) # this will build a way to get api calls
result = service.users().messages().list(userId="me").execute() # gets all mails in to a dictatory and have id and something


# this function get called by the mail contain getter to cypher the encrypted mail contain

def decode_data(data):
    decoded = base64.urlsafe_b64decode(data)
    text = decoded.decode("utf-8")
    return(text)

# this code will go to the bottom of the nested dictionary and find the mail contain

def content_gater(data,mimeType=None):
    if isinstance(data,dict):
        for i in data:
            if i == "data" and mimeType == "text/plain":
                return decode_data(data[i])

            elif isinstance(data[i],dict):
                if "mimeType" in data:
                    mimeType = data["mimeType"]
                result=content_gater(data[i],mimeType)
                if result is not None:
                    return result

            elif isinstance(data[i],list):
                result=content_gater(data[i],mimeType)
                if result is not None:
                    return result

    elif isinstance(data,list):
        for list_dicts in data:
            result=content_gater(list_dicts,mimeType)
            if result is not None:
                return result

# this function will get me the things that i need for new list like sender mail, date , subject

def get_info_header(header,name):
    for i in header:
        if i["name"]==name:
            return (i["value"])
    return None


def mail_object(message_id,message,header):

    new_mail_object={
        "id":message_id,
        "sender":get_info_header(header,"From"),
        "subject":get_info_header(header,"Subject"),
        "date":get_info_header(header,"Date"),
        "body":content_gater(message)
    }
    return new_mail_object

def list_all_mails():
    mail = result["messages"]
    for i in mail:
        message = service.users().messages().get(userId="me",id=i["id"]).execute()
        header = message["payload"]["headers"]
        mailsList.append(mail_object(i["id"],message,header))
        
list_all_mails()

print(len(mailsList))
print(mailsList[0])

