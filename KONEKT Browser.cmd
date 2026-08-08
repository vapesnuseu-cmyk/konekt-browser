@echo off
rem KONEKT Browser — double-click to launch.
rem electron.exe runs the app directly; node is not needed at runtime.
if not exist "%~dp0node_modules\electron\dist\electron.exe" (
  echo Electron is not installed yet. Run "npm install" in this folder first.
  pause
  exit /b 1
)
start "KONEKT Browser" "%~dp0node_modules\electron\dist\electron.exe" "%~dp0."
