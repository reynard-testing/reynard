
import json
import os


def get_json(file_path):
    try:
        with open(file_path, 'r') as file:
            return json.load(file)
    except Exception as e:
        print(f"Error reading JSON file {file_path}: {e}")
        return None


def find_json(dir: str, s: str):
    for root, _, files in os.walk(dir):
        for file in files:
            if file.endswith(".json") and s.lower() in file.lower():
                return os.path.join(root, file)
    print(f"JSON file with '{s}' not found in {dir}")
    return None
