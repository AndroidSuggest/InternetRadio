import urllib.request
import json
import os

URL = "https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/master/json/states.json"
TARGET_LANGUAGES = {"de", "et", "fr", "hi", "hu", "ro", "tr"}
OUTPUT_PATH = "app/src/main/assets/states.json"

import sys

def report_progress(block_num, block_size, total_size):
    downloaded = block_num * block_size
    if total_size > 0:
        percent = downloaded * 100 / total_size
        sys.stdout.write(f"\rDownloading states.json... {percent:.1f}%")
        sys.stdout.flush()
    else:
        sys.stdout.write(f"\rDownloading states.json... {downloaded} bytes")
        sys.stdout.flush()

def main():
    print("Downloading states.json...")
    temp_file, _ = urllib.request.urlretrieve(URL, reporthook=report_progress)
    print("\nDownload complete. Parsing JSON...")
    
    with open(temp_file, "r", encoding="utf-8") as f:
        data = json.load(f)
        
    print(f"Loaded {len(data)} states.")
    
    # We want a dictionary mapping country_code -> list of states
    grouped_states = {}
    
    for state in data:
        country_code = state.get("country_code")
        if not country_code:
            continue
            
        iso3166_2 = state.get("iso3166_2") or state.get("state_code")
        if not iso3166_2:
            continue
            
        name = state.get("name")
        translations_raw = state.get("translations") or {}
        
        # Filter translations
        translations_filtered = {}
        for lang, translation in translations_raw.items():
            if lang in TARGET_LANGUAGES and translation:
                translations_filtered[lang] = translation
                
        state_obj = {
            "code": iso3166_2,
            "name": name
        }
        if translations_filtered:
            state_obj["translations"] = translations_filtered
            
        if country_code not in grouped_states:
            grouped_states[country_code] = []
        grouped_states[country_code].append(state_obj)
        
    # Ensure assets directory exists
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(grouped_states, f, ensure_ascii=False, separators=(',', ':'))
        
    print(f"Successfully wrote {len(grouped_states)} countries to {OUTPUT_PATH}")
    print(f"File size: {os.path.getsize(OUTPUT_PATH) / 1024:.2f} KB")

if __name__ == "__main__":
    main()
