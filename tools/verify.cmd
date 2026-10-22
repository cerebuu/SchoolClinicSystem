@echo off
cd /d "%~dp0.."
powershell -NoProfile -Command "$s = @(Get-ChildItem src,tests,demo -Recurse -Filter *.java | ForEach-Object FullName); javac -d out-check $s; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; foreach ($check in @('DomainChecks','UiChecks','DashboardConsultationChecks','VisitSearchDemo')) { java '-Djava.awt.headless=true' -cp out-check $check; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }; exit 0"
