@echo off
cd /d "%~dp0"
powershell -NoProfile -Command "$s = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName); javac -d out $s; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; java -cp out clinic.app.Main --demo"
