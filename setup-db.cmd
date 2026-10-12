@echo off
setlocal
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-local.ps1" -Setup
set "result=%errorlevel%"
pause
exit /b %result%
