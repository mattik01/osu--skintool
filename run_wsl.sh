#!/bin/bash

# WSL Display Configuration Script
# This script sets up the display environment for running JavaFX applications in WSL

echo "Setting up display environment for WSL..."

# Check if running in WSL
if grep -qi microsoft /proc/version; then
    echo "WSL environment detected"
    
    # Set DISPLAY variable for WSLg (Windows 11) or X server (Windows 10)
    if [ -z "$DISPLAY" ]; then
        export DISPLAY=:0
        echo "DISPLAY set to :0"
    else
        echo "DISPLAY already set to: $DISPLAY"
    fi
    
    # Additional JavaFX settings for better compatibility
    export _JAVA_OPTIONS="-Djava.awt.headless=false"
    
    echo "Environment configured successfully!"
else
    echo "Not running in WSL, using default display settings"
fi

echo "Starting osu! Skin Selection Tool..."
mvn javafx:run