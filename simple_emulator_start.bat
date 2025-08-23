@echo off
echo Starting emulator with basic network settings...
echo.

D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Android_XR_Test -camera-back webcam0 -camera-front webcam0 -mic-type host

pause