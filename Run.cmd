@echo off
cd /d "%~dp0"
call tools\dependencies.cmd
if errorlevel 1 exit /b 1
powershell -NoProfile -Command "$s = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName); javac --release 17 -encoding UTF-8 -cp 'lib/*' -d out $s; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; New-Item -ItemType Directory -Force out/clinic/app/ui/auth | Out-Null; Copy-Item src/clinic/app/ui/auth/uic-logo.png out/clinic/app/ui/auth/uic-logo.png -Force; java -cp 'out;lib/*' clinic.app.Main; exit $LASTEXITCODE"
