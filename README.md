# School Clinic Management System

Java 17+ and plain Swing with an embedded, encrypted H2 database. No database
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
javac --release 17 -encoding UTF-8 -cp "lib/*" -d out @sources
java -cp "out;lib/*" clinic.app.Main
```

On macOS or Linux, use `sh Run.sh` (or `sh Run.sh --demo` for isolated sample
accounts). This verifies the same pinned dependencies, compiles with Java 17,
and includes the logo resource. macOS selects the installed JDK 17 when
`JAVA_HOME` is unset or points to an older runtime; set `JAVA_HOME` to choose a
different JDK 17 or newer. Without a supported JDK, launch stops with a setup
message.

To start a specific role's interface, run its dedicated launcher instead:

```powershell
java -cp "out;lib/*" clinic.app.SuperadminGUI
java -cp "out;lib/*" clinic.app.NurseGUI
```

Text inputs throughout the app support right-click Cut, Copy, Paste, and Select
All menus, along with Cmd shortcuts on macOS and Ctrl shortcuts on Windows/Linux.
Single-line paste replaces line breaks with spaces; length-limited form areas
keep their configured limit.

Use a Java 17 or newer runtime to launch the application. Java 8 cannot run
these classes.

Both launchers show the shared sign-in screen and require an account of the selected role; they do not create or bypass authentication. Add `--demo` to either command to use the sample accounts (`AD001` / `admin123` for Superadmin and `N001` / `nurse123` for Nurse).

Normal startup opens login and offers first-run administrator setup. Create an administrator, sign in, add staff accounts and campuses, then sign out and sign in with a staff account. Login routes administrators to a separate Superadmin workspace for account and campus management, system summaries, and the audit log. School nurses receive their own clinic-operations dashboard for patients, consultations, inventory, dispensing, notifications, and reports. Assistants and doctors use their permission-limited clinic workspace. The underlying service layer enforces role access independently of navigation.

New administrator and staff passwords must contain at least eight characters. Existing accounts remain usable; this rule applies when creating an account.

With `SCMS_DB_URL` unset, startup stores data in an encrypted local H2 database at `data/scms` (or
the path supplied with `-Dclinic.database=...`). The first launch downloads the
pinned H2 driver into `lib/` and verifies its SHA-256 checksum. The database,
key, and lock files are deliberately ignored by Git; keep the database and its
matching key file together. `--demo` and automated checks use isolated
in-memory or temporary data and are not persistent.

## Run with MySQL

Start the existing MySQL/MariaDB server and use the populated `school_clinic`
database. Do not recreate it or import sample data. Connector/J **8.4.0** is
pinned in both dependency scripts; its URL, license and SHA-256 verification
are documented in [lib/README.md](lib/README.md).

Set all three environment variables in the terminal that launches Java.
`SCMS_DB_PASSWORD` may be empty for a local XAMPP installation. The examples
use the supplied local account; use your actual credentials and never commit
them to source, launch settings, or shared scripts.

macOS/Linux:

```sh
export SCMS_DB_URL='jdbc:mysql://127.0.0.1:3306/school_clinic'
export SCMS_DB_USER='root'
export SCMS_DB_PASSWORD=''
sh Run.sh
# Or, after sh tools/dependencies.sh:
find src -name '*.java' > /tmp/srcs.txt
javac -encoding UTF-8 -cp 'lib/*' -d out @/tmp/srcs.txt
java -cp 'out:lib/*' clinic.app.NurseGUI
```

Windows PowerShell (launch a child process so an intentionally empty password
also works on Windows PowerShell versions that remove empty environment values):

```powershell
cmd /c tools\dependencies.cmd
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
javac --release 17 -encoding UTF-8 -cp "lib/*" -d out @sources
$env:SCMS_DB_URL = 'jdbc:mysql://127.0.0.1:3306/school_clinic'
$env:SCMS_DB_USER = 'root'
$env:SCMS_DB_PASSWORD = ''
$launch = New-Object System.Diagnostics.ProcessStartInfo
$launch.FileName = 'java.exe'
$launch.Arguments = '-cp "out;lib/*" clinic.app.NurseGUI'
$launch.UseShellExecute = $false
$launch.EnvironmentVariables['SCMS_DB_PASSWORD'] = ''
[System.Diagnostics.Process]::Start($launch).WaitForExit()
```

For a nonempty password set it in the environment and remove the explicit
empty-password override on `$launch`. Do not add `--demo`: that option always
uses isolated sample data. The IDE must inherit these variables too. If
`SCMS_DB_URL` is unset or blank, normal startup uses the original encrypted H2
database. An explicit remote connection failure is reported; it does not
silently open a different H2 database. URLs with `jdbc:mariadb://` are also
accepted through Connector/J's MySQL protocol.

An empty root password was rejected by the server inspected during verification.
Set `SCMS_DB_PASSWORD` to your actual local password in that case; the app does
not read or copy credentials from MySQL client configuration files.

MySQL startup validates the existing v5+ base tables before DDL. It then applies
retryable, additive migrations through the current v12 schema. Existing rows,
names (including strengths), password hashes and table names are preserved.
The obsolete MySQL notification check is retained as NOT ENFORCED and replaced
in enforcement by the expanded status check. MariaDB cannot disable one check
this way: if its v5 status check excludes FAILED, startup stops before DDL and
requires a DBA to widen that check. No automatic constraint/table drops occur.

Manual check of the supplied database:

1. Set the three variables, launch without `--demo`, and sign in as **N001**
   using its existing password. Do not reset or rehash accounts.
2. Open Patients: **70** expected. Open Inventory: **7 batches** expected
   (the UI may group multiple batches under one medicine). Check that legacy
   names such as “Paracetamol 500mg” display intact.
3. Open Recent Clinic Visits, select **All**, and clear its search: **4** expected
   after the approved campus mapping described below.
4. Restart and check persistence. Unset `SCMS_DB_URL` to confirm the separate
   embedded H2 database still opens.

**Campus prerequisite for this v5 import:** it has no account or visit campus
assignments. Current nurse permissions require an approved UIC campus; visits
are filtered by that campus. Have an administrator assign N001 using the
existing user editor, and have the data owner approve the matching campus for
legacy visits/dispensing/notifications before a DBA backfills those nullable
fields. The migration deliberately leaves them NULL rather than guessing a
campus or broadening access. Until then, the N001 UI count check is blocked.
The three approved UIC campus choices remain available in the UI. Remote startup
does not seed those choices into the existing campuses table; H2 startup retains
its existing additive defaults. No sample patients or accounts are installed.

After compiling `src`, `tests`, and `demo`, an optional repository check runs
against this database with `java -cp 'out-check:lib/*'
clinic.persistence.MySqlChecks --existing` (Windows uses `;` in the classpath).
It applies the same migrations twice, checks unscoped 70/7/4 counts and paging,
checks values using a session-only temporary table, and verifies that original
base columns and password hashes are unchanged. It does not log in or assign
campuses. Run `sh tools/verify.sh` first for the isolated H2 checks.

Administrators can also open **Users → a staff row’s Actions menu → Reset password**
for a nurse, doctor, or assistant. Enter and confirm the new password in the
masked fields, then Save. This clears failed attempts and lockout without changing
role, campus, or active status. The new password works immediately in the running app.

## One-time password recovery

Close the app, then run this from a real terminal in the project directory,
with the same `SCMS_DB_URL`, `SCMS_DB_USER`, and `SCMS_DB_PASSWORD` environment
variables used for normal startup. If the URL is unset, the usual H2 database
is selected instead.

```sh
sh tools/dependencies.sh
. ./tools/java-env.sh
find src -name '*.java' > /tmp/srcs.txt
"$scms_javac" -encoding UTF-8 -cp 'lib/*' -d out @/tmp/srcs.txt
"$scms_java" -cp 'out:lib/*' clinic.core.ResetPassword AD001
```

Type the new password (at least eight characters) at `New password:`. Input is
hidden; passwords are never accepted as command arguments or piped input.
Success prints only `Password reset for AD001.` The utility reuses login's
password hasher and updates only that account's `password_hash`,
`failed_attempts=0`, and `locked_until=0`. Restart the app after the reset.

On Windows, compile using the existing PowerShell instructions, then run
`java -cp "out;lib/*" clinic.core.ResetPassword AD001` in a terminal with the
same database environment settings.

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

On macOS/Linux use `sh tools/verify.sh`; for native windows use
`sh tools/verify.sh --desktop`. These compile all sources and run in a printed
temporary directory so screenshots and test databases do not overwrite the
workspace or production data. The native checks include duplicate-submission
and retry tests for interactive dialogs.

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

If the assistant reports that Gemini rejected the API key, replace the value in
the selected configuration source with an active key from Google AI Studio, then
restart the app. Check the environment variable first: it overrides the Java
system property, which overrides `config.local.properties`. Do not paste keys
into chat, source files, or logs. Google now
rejects unrestricted standard keys; use an authorization key or restrict a
standard key to the Gemini API. A project-level permission or billing restriction
must be corrected in Google AI Studio/Google Cloud and cannot be fixed by changing
the Java request. The app distinguishes invalid or blocked keys, desktop-app key
restrictions, disabled API access, and billing errors without displaying Google's
raw error body.

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
