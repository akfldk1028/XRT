@echo off
echo ================================================
echo Starting Emulator with Working Network for API
echo ================================================
echo.

echo Killing current emulator...
adb kill-server
taskkill /f /im "emulator.exe" 2>nul
timeout /t 3 >nul

echo Starting ADB server...
adb start-server
timeout /t 2 >nul

echo Starting emulator with network fixes...
echo - Camera: webcam0
echo - DNS: 8.8.8.8
echo - Network mode: bridged
echo.

start "Android Emulator" D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Android_XR_Test -camera-back webcam0 -camera-front webcam0 -mic-type host -dns-server 8.8.8.8 -netspeed full -netdelay none -http-proxy ""

echo.
echo Waiting for emulator to start...
timeout /t 10 >nul

echo Testing network connection...
adb wait-for-device
adb shell ping -c 1 8.8.8.8

pause