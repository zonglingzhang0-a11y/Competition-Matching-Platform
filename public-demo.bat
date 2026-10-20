@echo off
REM Public demo: start backend + frontend, then open a Cloudflare quick tunnel and print a public URL.
REM Keep this file ASCII-only (cmd.exe misreads multi-byte text in .bat files).
REM The real script, with Chinese messages, is scripts\public-demo.ps1. Add -Rebuild to rebuild the frontend.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\public-demo.ps1" %*
