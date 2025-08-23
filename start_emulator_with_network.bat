@echo off
echo ======================================================
echo Starting Android XR Emulator with Network + Audio
echo ======================================================
echo.

echo Configuration:
echo - Front Camera: webcam0
echo - Back Camera: webcam0  
echo - Microphone: Host PC
echo - Network: Full internet access
echo - DNS: Google DNS (8.8.8.8)
echo.

D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Android_XR_Test -camera-back webcam0 -camera-front webcam0 -mic-type host -dns-server 8.8.8.8 -netdelay none -netspeed full

echo.
echo ======================================================
echo Emulator started with full network and audio support!
echo ======================================================