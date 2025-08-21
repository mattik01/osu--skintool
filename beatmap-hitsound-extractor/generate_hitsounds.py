#!/usr/bin/env python3
'''
Generate hitsounds for an arrangement with skin + default fallback.
Usage: python generate_hitsounds.py <arrangement.json> <skin_folder> <default_folder> <output_file>
'''

import sys
import json
import subprocess
from pathlib import Path

def load_hitsounds_with_fallback(skin_folder, default_folder):
    '''Load hitsound files from skin folder with fallback to defaults'''
    skin_path = Path(skin_folder)
    default_path = Path(default_folder)
    hitsounds = {}
    
    # All possible hitsound combinations
    samplesets = ['normal', 'soft', 'drum']
    additions = ['hitnormal', 'hitclap', 'hitwhistle', 'hitfinish', 'slidertick', 'sliderslide']
    
    for sampleset in samplesets:
        for addition in additions:
            key = f"{sampleset}-{addition}"
            found = False
            
            # First try skin folder
            for ext in ['.wav', '.ogg', '.mp3']:
                filename = f"{key}{ext}"
                filepath = skin_path / filename
                if filepath.exists():
                    hitsounds[key] = str(filepath)
                    print(f"Using skin hitsound: {key}")
                    found = True
                    break
            
            # If not found in skin, try default folder
            if not found:
                for ext in ['.wav', '.ogg', '.mp3']:
                    filename = f"{key}{ext}"
                    filepath = default_path / filename
                    if filepath.exists():
                        hitsounds[key] = str(filepath)
                        print(f"Using default hitsound: {key}")
                        break
    
    return hitsounds

def create_hitsound_mix(arrangement_data, hitsounds, output_file):
    '''Create hitsound mix using FFmpeg - EXACT SAME LOGIC AS ORIGINAL'''
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
        if hitnormal_key in hitsounds:
            inputs.append(f"-i '{hitsounds[hitnormal_key]}'")
            delay = int(time_ms)
            filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
        
        # Add additions based on hitsound flags
        if obj.get('has_whistle'):
            whistle_key = f"{sampleset}-hitwhistle"
            if whistle_key in hitsounds:
                inputs.append(f"-i '{hitsounds[whistle_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
        
        if obj.get('has_clap'):
            clap_key = f"{sampleset}-hitclap"
            if clap_key in hitsounds:
                inputs.append(f"-i '{hitsounds[clap_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
        
        if obj.get('has_finish'):
            finish_key = f"{sampleset}-hitfinish"
            if finish_key in hitsounds:
                inputs.append(f"-i '{hitsounds[finish_key]}'")
                delay = int(time_ms)
                filters.append(f"[{len(inputs)-1}]adelay={delay}|{delay},volume=2.0[s{len(filters)}]")
    
    if not inputs:
        print("No hitsounds to mix")
        return False
    
    # Create mix filter - EXACT SAME AS ORIGINAL
    mix_inputs = ''.join(f"[s{i}]" for i in range(len(filters)))
    filters.append(f"{mix_inputs}amix=inputs={len(filters)}:duration=longest,volume=3.0[out]")
    
    # Build and run FFmpeg command
    cmd = f"ffmpeg -y {' '.join(inputs)} -filter_complex \"{';'.join(filters)}\" -map '[out]' -ac 2 -ar 44100 -b:a 192k '{output_file}'"
    
    print(f"Running FFmpeg with {len(inputs)} inputs...")
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    
    if result.returncode != 0:
        print(f"FFmpeg error: {result.stderr}")
    
    return result.returncode == 0

def main():
    if len(sys.argv) != 5:
        print("Usage: python generate_hitsounds.py <arrangement.json> <skin_folder> <default_folder> <output_file>")
        sys.exit(1)
    
    arrangement_file = sys.argv[1]
    skin_folder = sys.argv[2]
    default_folder = sys.argv[3]
    output_file = sys.argv[4]
    
    # Load arrangement
    with open(arrangement_file, 'r') as f:
        arrangement_data = json.load(f)
    
    # Load hitsounds with fallback
    hitsounds = load_hitsounds_with_fallback(skin_folder, default_folder)
    
    if not hitsounds:
        print("No hitsounds found at all!")
        sys.exit(1)
    
    print(f"Loaded {len(hitsounds)} hitsounds total")
    
    # Create hitsound mix
    if create_hitsound_mix(arrangement_data, hitsounds, output_file):
        print(f"Successfully created: {output_file}")
    else:
        print("Failed to create hitsound mix")
        sys.exit(1)

if __name__ == "__main__":
    main()