#!/bin/bash

echo "osu! Skin Selection Tool - Build and Run Script"
echo "==============================================="

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 17 or later and try again"
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Please install Apache Maven and try again"
    exit 1
fi

echo "Building application..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "ERROR: Build failed"
    exit 1
fi

echo "Starting application..."
mvn javafx:run