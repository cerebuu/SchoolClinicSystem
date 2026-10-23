# School Clinic Management System

Java 21 and plain Swing; no external libraries or database dependencies.

## Run

From PowerShell in this directory:

```powershell
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
javac -d out @sources
java -cp out clinic.app.Main
```

To start a specific role's interface, run its dedicated launcher instead:

```powershell
java -cp out clinic.app.SuperadminGUI
java -cp out clinic.app.NurseGUI
```

Both launchers show the shared sign-in screen and require an account of the selected role; they do not create or bypass authentication. Add `--demo` to either command to use the sample accounts (`AD001` / `admin123` for Superadmin and `N001` / `nurse123` for Nurse).

Normal startup opens login and offers first-run administrator setup. Create an administrator, sign in, add staff accounts and campuses, then sign out and sign in with a staff account. Login routes administrators to a separate Superadmin workspace for account and campus management, system summaries, and the audit log. School nurses receive their own clinic-operations dashboard for patients, consultations, inventory, dispensing, notifications, and reports. Assistants and doctors use their permission-limited clinic workspace. The underlying service layer enforces role access independently of navigation.

**Data is held in memory and is lost when the process exits.** This repository did not contain a database, schema, or concrete clinic data service. This implementation provides actual session-scoped operations; it does not simulate persistence or external message delivery.

`Demo.cmd` compiles and launches login with the existing explicit sample-user fixture. Normal startup never installs those accounts. `PagedTableDemo.cmd` launches a separate 10,000-row demonstration with 25 rows loaded per page. The CMD launchers work without changing PowerShell's script execution policy.

## Verify

```powershell
cmd /c tools\verify.cmd
```

This compiles production, demonstration, and check sources, then runs headless domain/security and Swing paging/navigation checks. Offscreen screenshots are saved under `verification`. These checks do not exercise native windows, modal dialogs, desktop scrolling, or keyboard focus.

See [the implementation report](docs/IMPLEMENTATION.md), [the complete file manifest](docs/FILES.md), and the captured [build](verification/build.txt) and [check](verification/checks.txt) output.

## Recent Clinic Visits search

The dashboard card has its own full-width name/ID search, clear button, and This week / Today / All selector. This week starts Monday at 00:00 and ends at the query time. Typing is debounced for 300 ms. Filtering runs before paging in the background, with 25 rows per page. Searches never write their text to the audit log. Assistants can search only consultations they recorded; nurses can search clinic visits. Doctor and administrator shells do not expose this staff data.

After compiling all `src`, `demo`, and `tests` Java files into `out-check`:

```text
java -Djava.awt.headless=true -cp out-check VisitSearchDemo
java -cp out-check VisitSearchDemo --show
java -cp out-check DashboardConsultationDemo
java -cp out-check SafetyPopupDemo
java -cp out-check PatientSectionPositionDemo --show
```

`VisitSearchDemo --show` automatically exercises search, scope changes, clear, and paging with 10,000 dummy visits, then closes. The sample patient names and visits belong only to the demo fixture.
