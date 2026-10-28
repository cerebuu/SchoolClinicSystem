# Recent visit search: behavior and verification

- Card-local `SearchBar` with placeholder, clear button, and a 300 ms debounce.
- Default This week: Monday 00:00 through the time the query starts. Today starts at midnight. All has no lower date boundary; future visits are excluded.
- Partial, case-insensitive patient names or partial IDs; purpose is not treated as a name match.
- Filters are applied inside the data source before selecting a 25-row page. The repository is scanned in reverse insertion order without copying all visits into the UI.
- Existing APIs and ClinicSystem signatures are preserved. New application/service overloads take inclusive LocalDateTime date bounds.
- Only staff with record-transaction permission can access this query. Staff without broader history permission see their own recorded visits.
- Audit details contain only `visit search performed`, never the search value.
- The existing consultation button, shared form, and Section / Position column are preserved.

Build and run (PowerShell, Java 17+):

```powershell
cmd /c tools\dependencies.cmd
$sources = @(Get-ChildItem src,tests,demo -Recurse -Filter *.java | ForEach-Object FullName)
javac --release 17 -cp 'lib/*' -d out-check @sources
java '-Djava.awt.headless=true' -cp out-check VisitSearchDemo
java -cp out-check VisitSearchDemo --show
```

The demo prints two Ana Cruz visits for this week, three matching 2022-ID visits this week, zero xyz matches, and three Cruz visits under All (including Pedro Cruz last month). It also verifies midnight and upper-bound inclusion, future exclusion, 10,004-row bounded paging, and audit privacy. Desktop mode drives partial name/ID searches, no-result feedback, All, clear, and page reset; it closes automatically on success. Sample timestamps depend on the execution time.

The application remains an in-memory school project: data does not survive process exit. This feature does not add a database or change that lifecycle.
