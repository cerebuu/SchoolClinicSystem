@echo off
cd /d "%~dp0"
call tools\dependencies.cmd
if errorlevel 1 exit /b 1
powershell -NoProfile -Command "$s = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName); javac -encoding UTF-8 -d out $s; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; Remove-Item Env:GEMINI_MODEL -ErrorAction SilentlyContinue; java -cp 'out;lib/*' clinic.app.NurseGUI --demo; exit $LASTEXITCODE"
