#!/bin/bash
# Run the hitsound processor for all beatmaps

SKIN_PATH="/root/skins/-    《DT》 - 『 東方Project 』 Paper Touhou Project  -"

echo "Processing all beatmaps with skin: $SKIN_PATH"
python3 process_all_beatmaps.py "beatmaps" "$SKIN_PATH"