import json

with open("gmail/data.json","r") as data:
    my_data = json.load(data)

print(type(my_data))