@echo off
REM Nexora Console - build a release and publish it.
REM Everything happens in release.ps1; this only starts it.
title Nexora Console - release
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1"
