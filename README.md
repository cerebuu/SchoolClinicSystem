# School Clinic Management System

Java 21 and plain Swing with an embedded, encrypted H2 database. No database
server or Maven installation is required.

## Run

From PowerShell in this directory:

```powershell
cmd /c Run.cmd
```

Or compile and launch manually:

```powershell
cmd /c tools\dependencies.cmd
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
javac -encoding UTF-8 -cp "lib/*" -d out @sources
java -cp "out;lib/*" clinic.app.Main
```

To start a specific role's interface, run its dedicated launcher instead:

```powershell
java -cp "out;lib/*" clinic.app.SuperadminGUI
java -cp "out;lib/*" clinic.app.NurseGUI
```

Both launchers show the shared sign-in screen and require an account of the selected role; they do not create or bypass authentication. Add `--demo` to either command to use the sample accounts (`AD001` / `admin123` for Superadmin and `N001` / `nurse123` for Nurse).

Normal startup opens login and offers first-run administrator setup. Create an administrator, sign in, add staff accounts and campuses, then sign out and sign in with a staff account. Login routes administrators to a separate Superadmin workspace for account and campus management, system summaries, and the audit log. School nurses receive their own clinic-operations dashboard for patients, consultations, inventory, dispensing, notifications, and reports. Assistants and doctors use their permission-limited clinic workspace. The underlying service layer enforces role access independently of navigation.

Normal startup stores data in an encrypted local H2 database at `data/scms` (or
the path supplied with `-Dclinic.database=...`). The first launch downloads the
pinned H2 driver into `lib/` and verifies its SHA-256 checksum. The database,
key, and lock files are deliberately ignored by Git; keep the database and its
matching key file together. `--demo` and automated checks use isolated
in-memory or temporary data and are not persistent.

## Guardian notifications

The notifier defaults to `SIMULATED`, which prints sends to the application
console Outbox and makes no network requests. Copy
`notifier.properties.example` to the ignored `notifier.properties` for local
configuration. `LIVE` email requires SMTP TLS settings and an app password;
`LIVE` SMS requires a Semaphore API key and may incur provider charges. Never
commit credentials. Do not select `LIVE` until a staff member has approved
real sends and configured the provider.

`Demo.cmd` compiles and launches login with the existing explicit sample-user fixture. Normal startup never installs those accounts. `PagedTableDemo.cmd` launches a separate 10,000-row demonstration with 25 rows loaded per page. The CMD launchers work without changing PowerShell's script execution policy.

## Verify

```powershell
cmd /c tools\verify.cmd
```

This compiles production, demonstration, and check sources, then runs headless domain/security and Swing paging/navigation checks. Offscreen screenshots are saved under `verification`.

For automated native-window workflows (opens temporary test windows and closes them):

```powershell
cmd /c tools\verify-desktop.cmd
```

The desktop checks use isolated in-memory data and exercise setup, login, required fields, cancellation, patient/consultation creation, campus editing, dispensing, safety overrides, restocking, notifications, reports, navigation refresh, resizing, and shutdown confirmation. Manual keyboard traversal, screen readers, multiple monitors, and alternative display scaling remain unverified.

## AI Nurse Assistant (optional)

The nurse-only **AI Nurse Assistant** uses Google Gemini through HTTPS.

### AI setup

Choose either setup method, then restart the application:

1. **Local file (recommended for VS Code, debugger, or desktop launches):** copy
   `config.local.properties.example` to `config.local.properties` in the project
   root and replace `your-key-here` with your own key. The local file is ignored
   by Git. Only the example belongs in version control. The application locates
   the root from the working directory or above its compiled `out/` / `bin/`
   directory. For a packaged app, put the file in its launch working directory.
2. **Environment variable:** set it in the same terminal that launches Java.
   An already-running IDE may not inherit variables exported in another terminal.

```powershell
$env:GEMINI_API_KEY = "your-key-here"
cmd /c Run.cmd
```

```sh
export GEMINI_API_KEY='your-key-here'
java -cp 'out:lib/*' clinic.app.Main
```

Lookup precedence is environment, then the Java system property
`-DGEMINI_API_KEY=...`, then the local properties file. Values are trimmed and
matching surrounding quotes are removed. Avoid putting real keys in command-line
arguments or shared launch configurations; the local file is preferable. A copied
example with its placeholder unchanged is treated as unconfigured.

If configuration is missing or unreadable, the panel shows a red setup message
and disables Send. Sending also requires checking **I confirm this contains no
student identifiers, records, or credentials**. Configuration values are never
printed. `GEMINI_MODEL` remains an optional environment setting.

Open **AI Nurse Assistant** from the nurse sidebar. It sends only the message
typed for the current request; it never reads the SCMS database or performs
SCMS actions, and chat is not persisted. Do not enter student names, IDs,
contact information, medical records, passwords, or other sensitive data. An
environment variable is suitable for local development only; a production
distributed desktop app should call an authorized server-side proxy rather
than ship a shared provider key.

The source default is `gemini-3.1-flash-lite`; its availability is not guaranteed
for every provider account. `NurseDemo.cmd` respects `GEMINI_MODEL` and recompiles
before opening the sample nurse login (`N001` / `nurse123`). Never paste a key in
chat or commit it. Live Gemini success is not established by the offline checks.

AI requests check the captured nurse session before and after network work.
Cancel, leaving the page, and closing the panel invalidate pending results.
Failed requests retain input for manual retry; no automatic retries are made.
The panel keeps at most 20 messages and sends only the current question.
Provider responses must contain a completed candidate; blocked, malformed, or
truncated answers are rejected. Obvious identifier patterns are screened, but
free-text screening is not a guarantee that sensitive data cannot be transmitted.
Use only non-identifying text pending an institutional privacy design.

See [the October 25 audit and improvement report](docs/SCMS_AUDIT_2026-10-25.md)
for implementation evidence, priorities, and deployment limitations. The earlier
PDF documents the pre-audit snapshot and is not a release readiness certificate.

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
