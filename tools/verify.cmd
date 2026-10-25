@echo off
cd /d "%~dp0.."
call tools\dependencies.cmd
if errorlevel 1 exit /b 1
powershell -NoProfile -Command "$s = @(Get-ChildItem src,tests,demo -Recurse -Filter *.java | ForEach-Object FullName); javac -encoding UTF-8 -d out-check $s; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; foreach ($check in @('DomainChecks','UiChecks','DashboardConsultationChecks','VisitSearchDemo','PersistenceChecks','clinic.ai.AiChecks')) { java '-Djava.awt.headless=true' -cp 'out-check;lib/*' $check; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }; exit 0"
