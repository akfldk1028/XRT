@echo off
echo Testing emulator...
echo.

echo Checking if emulator.exe exists...
if exist "D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe" (
    echo OK: emulator.exe found
) else (
    echo ERROR: emulator.exe not found!
    pause
    exit
)

echo.
echo Listing available AVDs...
D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -list-avds

echo.
echo Press Enter to try starting emulator...
pause

D:\Users\SOGANG\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Android_XR_Test -verbose

pause