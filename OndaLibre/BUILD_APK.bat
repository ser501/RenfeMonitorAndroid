@echo off
setlocal
cd /d "%~dp0"
where gradle >nul 2>nul
if errorlevel 1 (
  echo No se encuentra Gradle en el PATH.
  echo Abre esta carpeta en Android Studio y usa Build ^> Build Bundle(s) / APK(s) ^> Build APK(s).
  echo Consulta README_ES.md para ver los pasos completos.
  pause
  exit /b 1
)
gradle assembleDebug
if errorlevel 1 (
  echo La compilacion ha fallado. Revisa el error anterior y README_ES.md.
  pause
  exit /b 1
)
echo APK generado en app\build\outputs\apk\debug\app-debug.apk
pause
