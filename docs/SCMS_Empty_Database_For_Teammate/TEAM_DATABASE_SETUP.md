# Teammate database setup

The starter is a fresh H2 database created with the current SCMS schema. It has
no staff accounts or patient records. It is not a copy of the existing clinic database.

## Easiest option: create it on your teammate's computer

Pull the project and run `.\Run.cmd` from PowerShell. If `data/scms.mv.db` does not
exist, the application creates its own encrypted database automatically. Use the
first-administrator setup, sign in, add a Nurse account in Users, then sign in as
that nurse to populate Patients and Inventory and record consultations.

Use normal mode, not `--demo`, to retain records after closing.

## Using the provided starter files

Close SCMS. Copy both `scms.mv.db` and `scms.key` from the supplied starter folder
into `data/team-starter/` in your teammate's project. Do not overwrite an existing
database or mix a database with a different key. Then run:

```powershell
cmd /c tools\dependencies.cmd
$clinicSources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
javac -encoding UTF-8 -d out @clinicSources
java '-Dclinic.database=data/team-starter/scms' -cp 'out;lib/*' clinic.app.Main
```

This explicitly selects the starter database instead of any existing `data/scms`.
Complete administrator setup and populate through the application. Account passwords
must be created through the application because storage expects salted hashes.

## For developers preparing another starter

After compiling production sources and obtaining the H2 driver:

```powershell
$clinicSources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
javac -encoding UTF-8 -d out-team @clinicSources tools/CreateTeamDatabase.java
java -cp 'out-team;lib/*' CreateTeamDatabase
```

The helper refuses to overwrite an existing starter. The exact tables, constraints
and migrations are defined in `src/clinic/persistence/Schema.java`. Populate patients
and inventory first; visits need existing patients/staff, and notifications need a
dispensing transaction belonging to the same patient. Prefer app workflows for
medicine changes to preserve stock/transaction consistency.

Database files and keys are ignored by Git. Sharing source code does not synchronize
local databases; each teammate maintains a separate copy. Share a starter pair directly
if needed. It contains no production records, but any populated database and its key
must be protected together.
