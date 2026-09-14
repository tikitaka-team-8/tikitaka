@echo off
cd /d "%~dp0"
echo Starting Toss payment test server...
java --add-modules jdk.httpserver StaticServer.java
pause
