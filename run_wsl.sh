#!/bin/bash

# Simple run script for WSL/Linux environments
# Sets display for X11 if needed

if [ -z "$DISPLAY" ]; then
    export DISPLAY=:0
fi

echo "Starting osu! Skin Selection Tool..."
echo ""
echo "NOTE: In WSL, your Windows folders are available under /mnt/"
echo "  C: drive -> /mnt/c/"
echo "  D: drive -> /mnt/d/"
echo ""
echo "Example osu! skins path: /mnt/c/Users/YOUR_USERNAME/AppData/Local/osu!/Skins/"
echo ""

mvn javafx:run