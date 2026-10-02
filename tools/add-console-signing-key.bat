@echo off
REM Nexora Console - add the console's own signing key to D:\nexora-signing (one time).
REM Everything happens in add-console-signing-key.ps1; this only starts it.
title Nexora Console - signing key
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0add-console-signing-key.ps1"
