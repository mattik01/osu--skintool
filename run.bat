@echo off
echo osu! Skin Selection Tool - Build and Run Script
echo ===============================================

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 17 or later and try again
    pause
    exit /b 1
)

REM Check if Maven is installed
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Apache Maven and try again
    pause
    exit /b 1
)

echo Building application...
mvn clean compile

if %errorlevel% neq 0 (
    echo ERROR: Build failed
    pause
    exit /b 1
)

echo Starting application...
mvn javafx:run

pause