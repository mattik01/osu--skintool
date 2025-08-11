#!/usr/bin/env python3
"""
Check which default skin elements are present and which are missing.
"""

import os
from pathlib import Path

# Define the path to the default skin folder
DEFAULT_SKIN_PATH = Path("src/main/resources/default-skin")

# Define all required elements organized by category
REQUIRED_ELEMENTS = {
    "Essential Hit Circle Elements": [
        "hitcircle.png",
        "hitcircleoverlay.png",
        "approachcircle.png",
    ],
    
    "Hit Burst Animations": [
        "hit0.png",
        "hit50.png",
        "hit100.png",
        "hit300.png",
        "lighting.png",
    ],
    
    "Combo Numbers (default)": [
        "default-0.png",
        "default-1.png",
        "default-2.png",
        "default-3.png",
        "default-4.png",
        "default-5.png",
        "default-6.png",
        "default-7.png",
        "default-8.png",
        "default-9.png",
    ],
    
    "Slider Elements": [
        "sliderb.png",
        "sliderfollowcircle.png",
        "sliderball.png",
        "reversearrow.png",
        "sliderscorepoint.png",
    ],
    
    "Cursor Elements": [
        "cursor.png",
        "cursortrail.png",
    ],
    
    "Health Bar Elements": [
        "scorebar-bg.png",
        "scorebar-colour.png",
        "scorebar-ki.png",
        "scorebar-kidanger.png",
        "scorebar-kidanger2.png",
    ],
    
    "Combo Counter Numbers": [
        "combo-0.png",
        "combo-1.png",
        "combo-2.png",
        "combo-3.png",
        "combo-4.png",
        "combo-5.png",
        "combo-6.png",
        "combo-7.png",
        "combo-8.png",
        "combo-9.png",
        "combo-x.png",
    ],
    
    "Score Numbers": [
        "score-0.png",
        "score-1.png",
        "score-2.png",
        "score-3.png",
        "score-4.png",
        "score-5.png",
        "score-6.png",
        "score-7.png",
        "score-8.png",
        "score-9.png",
        "score-comma.png",
        "score-dot.png",
        "score-percent.png",
        "score-x.png",
    ],
}

# Optional animated elements
OPTIONAL_ELEMENTS = {
    "Animated Hit Bursts": [
        "hit0-0.png",
        "hit50-0.png",
        "hit100-0.png",
        "hit300-0.png",
        "hit100k-0.png",
    ],
    
    "Particle Effects": [
        "particle50.png",
        "particle100.png",
        "particle300.png",
    ],
}

def check_files():
    """Check which files are present and missing."""
    
    # Check if directory exists
    if not DEFAULT_SKIN_PATH.exists():
        print(f"❌ Directory not found: {DEFAULT_SKIN_PATH}")
        return
    
    # Get list of all files in the directory
    existing_files = set()
    for file in DEFAULT_SKIN_PATH.iterdir():
        if file.is_file() and file.suffix.lower() == '.png':
            existing_files.add(file.name.lower())
    
    print("=" * 60)
    print("DEFAULT SKIN ELEMENTS CHECK")
    print("=" * 60)
    print(f"Directory: {DEFAULT_SKIN_PATH.absolute()}")
    print(f"Total PNG files found: {len(existing_files)}")
    print()
    
    # Check required elements
    total_required = 0
    total_found = 0
    missing_critical = []
    
    print("REQUIRED ELEMENTS:")
    print("-" * 40)
    
    for category, files in REQUIRED_ELEMENTS.items():
        found = []
        missing = []
        
        for file in files:
            total_required += 1
            if file.lower() in existing_files:
                found.append(file)
                total_found += 1
            else:
                missing.append(file)
                if category in ["Essential Hit Circle Elements", "Combo Numbers (default)"]:
                    missing_critical.append(file)
        
        status = "✅" if len(missing) == 0 else "⚠️" if len(found) > 0 else "❌"
        print(f"\n{status} {category}:")
        print(f"   Found: {len(found)}/{len(files)}")
        
        if missing:
            print(f"   Missing: {', '.join(missing)}")
    
    # Check optional elements
    print("\n" + "=" * 40)
    print("OPTIONAL ELEMENTS:")
    print("-" * 40)
    
    total_optional = 0
    optional_found = 0
    
    for category, files in OPTIONAL_ELEMENTS.items():
        found = []
        missing = []
        
        for file in files:
            total_optional += 1
            if file.lower() in existing_files:
                found.append(file)
                optional_found += 1
            else:
                missing.append(file)
        
        status = "✅" if len(missing) == 0 else "📦" if len(found) > 0 else "⭕"
        print(f"\n{status} {category}:")
        print(f"   Found: {len(found)}/{len(files)}")
    
    # Check for extra files not in our lists
    all_expected = set()
    for files in REQUIRED_ELEMENTS.values():
        all_expected.update(f.lower() for f in files)
    for files in OPTIONAL_ELEMENTS.values():
        all_expected.update(f.lower() for f in files)
    
    extra_files = existing_files - all_expected - {'readme.md', 'required_elements.md'}
    
    if extra_files:
        print("\n" + "=" * 40)
        print("ADDITIONAL FILES FOUND (not in standard list):")
        print("-" * 40)
        for file in sorted(extra_files):
            print(f"   📄 {file}")
    
    # Summary
    print("\n" + "=" * 60)
    print("SUMMARY:")
    print("-" * 40)
    print(f"Required elements: {total_found}/{total_required} ({total_found*100//total_required}%)")
    print(f"Optional elements: {optional_found}/{total_optional}")
    
    if missing_critical:
        print("\n⚠️  CRITICAL MISSING FILES (needed for basic preview):")
        for file in missing_critical:
            print(f"   - {file}")
    
    # Priority recommendations
    if total_found < total_required:
        print("\n📋 PRIORITY FILES TO ADD:")
        priority_missing = []
        
        # Check most important categories first
        for cat in ["Essential Hit Circle Elements", "Combo Numbers (default)", "Cursor Elements", "Hit Burst Animations"]:
            if cat in REQUIRED_ELEMENTS:
                for file in REQUIRED_ELEMENTS[cat]:
                    if file.lower() not in existing_files:
                        priority_missing.append(file)
                        if len(priority_missing) >= 10:
                            break
            if len(priority_missing) >= 10:
                break
        
        for file in priority_missing[:10]:
            print(f"   1. {file}")
    else:
        print("\n✅ All required elements are present!")
    
    print("\n" + "=" * 60)

if __name__ == "__main__":
    check_files()