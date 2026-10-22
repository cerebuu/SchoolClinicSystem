@echo off
cd /d "%~dp0"
powershell -NoProfile -Command "$s = @(Get-ChildItem src,demo -Recurse -Filter *.java | ForEach-Object FullName); javac -d out-demo $s; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; java -cp out-demo PagedTableDemo"
