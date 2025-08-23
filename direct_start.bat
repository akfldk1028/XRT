@echo off
echo Starting emulator directly...
echo.

cd /d "D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator"

echo Current directory: %CD%
echo.

echo Available AVDs:
emulator.exe -list-avds
echo.

echo Starting Android_XR_Test...
emulator.exe -avd Android_XR_Test -camera-back webcam0 -camera-front webcam0

pause