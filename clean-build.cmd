@echo off
setlocal EnableExtensions

cd /d "%~dp0"

echo Stopping Gradle daemons...
if exist "gradlew.bat" (
   call gradlew.bat --stop >nul 2>&1
) else (
   echo Warning: gradlew.bat not found; skipping daemon stop.
)

ping -n 3 127.0.0.1 >nul

if not exist "build" (
   echo build directory does not exist.
   exit /b 0
)

echo Removing build directory...

attrib -R "build\*" /S /D >nul 2>&1
rmdir /S /Q "build" 2>nul

if not exist "build" (
   echo Successfully removed build directory.
   exit /b 0
)

echo Direct delete failed; trying robocopy mirror trick...
set "EMPTY=%TEMP%\opendonut-empty-%RANDOM%"
mkdir "%EMPTY%" 2>nul
robocopy "%EMPTY%" "build" /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS >nul
rmdir /S /Q "%EMPTY%" 2>nul
attrib -R "build\*" /S /D >nul 2>&1
rmdir /S /Q "build" 2>nul

if exist "build" (
   echo.
   echo FAILED: Could not delete build directory.
   echo Close any terminal or IDE whose working directory is under build\,
   echo stop running Gradle/Java tasks for this project, then run this script again.
   exit /b 1
)

echo Successfully removed build directory.
exit /b 0
