@echo off
echo ================================================
echo Restarting Android XR Emulator with Full Audio
echo ================================================
echo.

echo Killing current emulator...
adb emu kill 2>nul
timeout /t 3 >nul

echo Starting emulator with webcam AND microphone...
echo - Front Camera: webcam0
echo - Back Camera: webcam0  
echo - Audio Input: Enabled
echo - Audio Output: Enabled
echo.

D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Android_XR_Test -camera-back webcam0 -camera-front webcam0 -mic-type host

echo.
echo ================================================
echo Emulator started! Check if microphone works now.
echo ================================================