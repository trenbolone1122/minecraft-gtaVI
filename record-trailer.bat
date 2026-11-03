@echo off
rem Double-click to record the Leonida trailer (see README, "Record the trailer on Windows").
rem Needs java (JDK 25), ffmpeg, ffprobe and yt-dlp on PATH. Options pass through, e.g.  record-trailer.bat -RenderDistance 16 -Bpm 128
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\record-trailer.ps1" %*
echo.
pause
