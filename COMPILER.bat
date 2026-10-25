@echo off
setlocal
cd /d "%~dp0"
echo ============================================================
echo   THE BLACK PARASITE - compilation automatique (Windows)
echo   Internet requis. 1er lancement : 10 a 20 minutes.
echo ============================================================
set "TOOLS=%~dp0.tools"
if not exist "%TOOLS%" mkdir "%TOOLS%"

if not exist "%TOOLS%\jdk\bin\java.exe" (
  echo [1/3] Telechargement de Java 17...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile '%TOOLS%\jdk.zip'; Expand-Archive '%TOOLS%\jdk.zip' '%TOOLS%\jdk_tmp' -Force; $d = Get-ChildItem '%TOOLS%\jdk_tmp' | Select-Object -First 1; Move-Item $d.FullName '%TOOLS%\jdk'; Remove-Item '%TOOLS%\jdk_tmp','%TOOLS%\jdk.zip' -Recurse -Force"
)
if not exist "%TOOLS%\jdk\bin\java.exe" ( echo ERREUR : Java 17 non installe. & pause & exit /b 1 )

if not exist "%TOOLS%\gradle-8.8\bin\gradle.bat" (
  echo [2/3] Telechargement de Gradle 8.8...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.8-bin.zip' -OutFile '%TOOLS%\gradle.zip'; Expand-Archive '%TOOLS%\gradle.zip' '%TOOLS%' -Force; Remove-Item '%TOOLS%\gradle.zip' -Force"
)
if not exist "%TOOLS%\gradle-8.8\bin\gradle.bat" ( echo ERREUR : Gradle non telecharge. & pause & exit /b 1 )

set "JAVA_HOME=%TOOLS%\jdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [3/3] Compilation du mod...
call "%TOOLS%\gradle-8.8\bin\gradle.bat" build --no-daemon
if errorlevel 1 (
  echo.
  echo ============ ERREUR ============
  echo Copiez le texte ci-dessus (depuis "FAILED" ou "error:") et envoyez-le a Claude.
) else (
  echo.
  echo TERMINE ! Votre mod : build\libs\blackparasite-0.1.0.jar
  echo Copiez-le dans le dossier mods de votre profil CurseForge ^(Forge 1.20.1^).
  explorer build\libs
)
pause
