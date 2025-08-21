#!/usr/bin/env python3
'''
Regenerate hitsounds for a single arrangement with a specific skin.
Usage: python regenerate_single.py <arrangement.json> <skin_folder> <output_file>
'''

import sys
import json
import subprocess
import tempfile
from pathlib import Path

def load_skin_hitsounds(skin_folder):
    '''Load all hitsound files from a skin folder'''
    skin_path = Path(skin_folder)
    hitsounds = {}

    # All possible hitsound combinations
    samplesets = ['normal', 'soft', 'drum']
    additions = ['hitnormal', 'hitclap', 'hitwhistle', 'hitfinish', 'slidertick', 'sliderslide']

    for sampleset in samplesets:
        for addition in additions:
            # Try different extensions
            for ext in ['.wav', '.ogg', '.mp3']:
                filename = f"{sampleset}-{addition}{ext}"
                filepath = skin_path / filename
                if filepath.exists():
                    hitsounds[f"{sampleset}-{addition}"] = str(filepath)
                    break

    return hitsounds

def create_hitsound_mix(arrangement_data, skin_hitsounds, output_file):
    '''Create hitsound mix using FFmpeg'''
    hit_objects = arrangement_data.get('hit_objects', [])
    if not hit_objects:
        return False

    # Build FFmpeg command
    inputs = []
    filters = []

    for obj in hit_objects:
        time_ms = obj['time']
        sampleset = obj.get('sampleset', 'normal')

        # Add hitnormal
        hitnormal_key = f"{sampleset}-hitnormal"
        if hitnormal_key in skin_hitsounds:
            inputs.append(f"-i '{skin_hitsounds[hitnormal_key]}'")
            delay = int(time_ms)
            filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")

        # Add additions based on hitsound flags
        if obj.get('has_whistle'):
            whistle_key = f"{sampleset}-hitwhistle"
            if whistle_key in skin_hitsounds:
                inputs.append(f"-i '{skin_hitsounds[whistle_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")

        if obj.get('has_clap'):
            clap_key = f"{sampleset}-hitclap"
            if clap_key in skin_hitsounds:
                inputs.append(f"-i '{skin_hitsounds[clap_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")

        if obj.get('has_finish'):
            finish_key = f"{sampleset}-hitfinish"
            if finish_key in skin_hitsounds:
                inputs.append(f"-i '{skin_hitsounds[finish_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")

    if not inputs:
        return False

    # Create mix filter
    mix_inputs = ''.join(f"[s{i}]" for i in range(len(filters)))
    filters.append(f"{mix_inputs}amix=inputs={len(filters)}:duration=longest,volume=3.0[out]")

    # Build and run FFmpeg command
    cmd = f"ffmpeg -y {' '.join(inputs)} -filter_complex \"{';'.join(filters)}\" -map '[out]' -ac 2 -ar 44100 -b:a 192k '{output_file}'"

    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0

def main():
    if len(sys.argv) != 4:
        print("Usage: python regenerate_single.py <arrangement.json> <skin_folder> <output_file>")
        sys.exit(1)

    arrangement_file = sys.argv[1]
    skin_folder = sys.argv[2]
    output_file = sys.argv[3]

    # Load arrangement
    with open(arrangement_file, 'r') as f:
        arrangement_data = json.load(f)

    # Load skin hitsounds
    skin_hitsounds = load_skin_hitsounds(skin_folder)

    if not skin_hitsounds:
        print(f"No hitsounds found in skin: {skin_folder}")
        sys.exit(1)

    # Create hitsound mix
    if create_hitsound_mix(arrangement_data, skin_hitsounds, output_file):
        print(f"Successfully created: {output_file}")
    else:
        print("Failed to create hitsound mix")
        sys.exit(1)

if __name__ == "__main__":
    main()
