@echo off
set JAVA_HOME=D:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
echo Building with JAVA_HOME: %JAVA_HOME%
gradlew.bat assembleDebug
echo Build completed!