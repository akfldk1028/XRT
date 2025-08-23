@echo off
echo =============================================
echo Android XR Emulator with Webcam Launcher
echo =============================================
echo.

echo Checking for running emulator...
adb devices | findstr "emulator"
if %errorlevel%==0 (
    echo Found running emulator. Killing it first...
    adb emu kill
    timeout /t 3 >nul
)

echo.
echo Starting emulator with webcam enabled...
echo Front Camera: webcam0
echo Back Camera: webcam0
echo.

D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Android_XR_Test -camera-back webcam0 -camera-front webcam0

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to start emulator!
    echo Check if AVD name is correct: Android_XR_Test
    pause
)