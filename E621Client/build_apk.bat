@echo off
echo ================================
echo Building E621 Client APK
echo ================================

cd /d "%~dp0"

echo Checking for Gradle wrapper...
if not exist "gradlew.bat" (
    echo Error: gradlew.bat not found!
    echo Please open this project in Android Studio first.
    pause
    exit /b 1
)

echo Building debug APK...
call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================
    echo Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo ================================
) else (
    echo.
    echo Build failed!
)

pause
