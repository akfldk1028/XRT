@echo off
echo ========================================
echo Starting Emulator with Network Fix
echo ========================================
echo.

echo Killing any existing emulator processes...
taskkill /f /im "emulator.exe" 2>nul
timeout /t 2 >nul

echo Starting emulator with network configuration...
echo - Camera: webcam0 (front/back)
echo - Microphone: Host PC
echo - DNS: 8.8.8.8, 8.8.4.4
echo - Network: Full speed, no delay
echo.

start "Android Emulator" /min D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Android_XR_Test -camera-back webcam0 -camera-front webcam0 -mic-type host -dns-server 8.8.8.8,8.8.4.4 -netspeed full -netdelay none -no-snapshot-save

echo.
echo Emulator started in background!
echo Waiting for device to come online...

timeout /t 5 >nul