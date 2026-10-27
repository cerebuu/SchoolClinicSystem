@echo off
cd /d "%~dp0"
call tools\dependencies.cmd
if errorlevel 1 exit /b 1
powershell -NoProfile -Command "$s = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName); javac -encoding UTF-8 -cp 'lib/*' -d out $s; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; java -cp 'out;lib/*' clinic.app.NurseGUI --demo; exit $LASTEXITCODE"
