# SCMS implementation report

## Reference verification and initial findings

The Google Drive folder https://drive.google.com/drive/folders/1ZKkTXOoFZIABe62AvmZ9ATUSBcw5BPlT could not be opened by the available web tool. No Drive diagram review is claimed.

The supplied Markdown contained a title. The PDF contained a class diagram and a care sequence diagram. `Save_1_UML_Clinic.drawio` was readable: its one page defines Person, User, Patient, Student, Employee, Administrator, Nurse, Doctor, Inventory, Medicine, medicineTransaction, MedicalRecord, Consultation, and service relationships. It contains no screen layouts or navigator specifications. Navigation and field choices below are therefore provisional, based on the written requirements and existing source contracts. Campus assignment to people, the Draw.io separation of Consultation from MedicalRecord, and exact UI layouts need verification against the inaccessible latest diagrams.

Initially, authentication, role subclasses, audit storage, record formatting, guardian message formatting, and alert explanations were implemented. Person, Patient, Student, Employee, Medicine, MedicalRecord, MedicineTransaction, Alert, Inventory, SafetyChecker, PatternDetector, and RestockForecast were empty shells with implementation notes. No concrete ClinicSystem implementation, database, SQL, migration, dependency manager, or automated test suite existed. The UI eagerly created placeholder modules, mixed admin and clinic navigation, showed invented statistics, displayed sample credentials, and did not clear the Login session on sign-out.

The workspace already had local edits in GUI.java, Main.java, UICClinicDashboard.java and several untracked app classes before this task. No reset or cleanup of that pre-existing work was performed. Main.java was already routing to GUI and was not changed during this implementation. Unused legacy ModulePage and NurseDashboard were retained; the routed application does not use their old placeholder displays.

## Architecture and completed changes

- **Login and routing:** GUI composes ApplicationContext and Router. Normal startup uses an empty Login with first-administrator setup; `--demo` explicitly uses Login.withSampleUsers with the shared AuditLog. LoginPanel validates fields, masks passwords, submits with Enter, hashes/checks passwords off the event thread, handles generic credential failures and lockout warnings, and displays loading feedback. Router.logout calls Login.logout before returning to login.
- **Session and authorization:** Login requires an administrator session to add or edit accounts after bootstrap. AdminService checks the active session for user/campus/audit operations. Existing role permissions remain in force. ApplicationContext returns a session-version-bound ClinicSystem proxy around SecuredClinicSystem, so retained service references fail after logout and same-user relogin. Staff IDs must match the signed-in user. Read queries have separate checked, audited entry points because the unchanged ClinicSystem interface has no pagination methods.
- **Password handling:** PasswordHasher uses per-account random salts, PBKDF2-HMAC-SHA256, 210,000 iterations, and constant-time comparison. Production accounts are not seeded with hardcoded credentials. No password values appear in the UI or audit output. Password hashing was extracted from Login to keep responsibilities separate. Existing hashes needed no migration because the repository had no persisted account store.
- **Reusable dialogs:** FormPopup and FormField share add/edit forms, required-field validation, masked secret fields, choices, asynchronous submission, save-in-progress feedback, and cancellation handling. Module callbacks perform domain validation. MessagePopup shares details, confirmations, success/info, validation failures, errors, and warnings. WarningOverridePopup lists safety reasons and requires an override reason. Templates are configured rather than subclassed for individual forms. This is a deliberately small dialog system instead of a separate base class for each popup type.
- **Modules:** Navigator creates panels lazily and rejects unknown/disallowed destinations. MainShell and AdminShell have independent navigation. Every active feature panel and table model has its own file. Existing Theme supplies the pink/white palette, fonts, control sizes, spacing, borders, and semantic warning/error colors.
- **Real domain operations:** InMemoryClinicSystem implements the existing ClinicSystem signatures with actual session-scoped state. Models validate identity and quantities. Inventory excludes expired batches from available stock, dispenses by earliest expiry, rejects insufficient quantities before changing stock, and protects against integer overflow. Duplicate patient/account/campus IDs are rejected. Notifications validate transaction ownership. Records and transactions implement Formattable for the existing formatter. Read profiles and medicine batches are detached so reads cannot mutate stored state outside the audited write boundary.
- **Performance:** PageResult scans stored data but copies only the selected page. PagedTablePanel retains at most 25 rows, uses debounced search, previous/next, total counts, page labels, empty/loading/error states, cancellation, and stale-result protection. SwingWorker performs queries off the event thread. Inventory totals are aggregated once per alert scan rather than rescanned per batch. The UI never calls the list-all ClinicSystem methods to populate its tables. The 10,000-row demo generates rows during iteration rather than allocating a 10,000-row Swing model.

## Provisional module map

All rows below come from the written requirements and existing project contracts, supported by domain attributes in the supplied UML where available. They are **not claimed to come from unseen UI diagrams**. Every table also has search/filter, Refresh, Prev/Next, and count/page feedback.

| Module | Actions and fields | Table columns / displayed data |
|---|---|---|
| Login | ID, masked password, Sign in, first-run administrator ID/name/password | Generic errors, loading and lockout warning |
| Clinic dashboard | Refresh | Actual patient and consultation counts |
| Patients | Register: ID, name, contact, student/employee, course/department, section/position, year level. Add allergy. Details. | ID, name, type, contact |
| Consultations | Record visit: patient ID, controlled symptom choice, concern, optional remarks | Record ID, patient ID, symptoms, concern, date; assistant can record but cannot read history |
| Dispense | Patient ID, medicine name, positive quantity; Check safety and dispense; required override reason on warnings | Success includes transaction ID |
| Inventory | Add batch: medicine, quantity, expiry, remarks. Restock selected medicine: quantity and expiry. Stock and forecast. | Medicine, quantity, expiry, batch date, remarks |
| Notifications | Create: patient ID, transaction ID, reason. View message. Mark sent after manual-delivery confirmation. | ID, patient ID, transaction ID, reason, date, status |
| Reports | Generate selected visit report | Record ID, patient ID, symptoms, concern, date |
| Settings | Session information | Signed-in ID, role, storage lifecycle |
| Admin dashboard | Refresh | Actual user and campus counts |
| Users | Add: ID, name, role, masked password. Edit: name, role, active status. Deactivate. Account status. Self-edit/deactivation blocked. | ID, name, role |
| Campuses | Add: ID and name. Edit name of selected campus. | Campus ID, name |
| Audit | Search tokens for user ID, date, and `denied` | Time, user ID, role, action, details, allowed |

Nurses get clinic modules; redundant Analytics navigation has been removed in favor of Reports. Assistants have patient registration, recording consultations, dispensing, stock viewing, and notifications; they have no restock, report, analytics, or admin operations. Doctors get reports only. Administrators get only admin dashboard, users, campuses, audit, and settings. Unsupported appointments and duplicate fixed �Floyd� statistics are absent from the new navigator.

## Validation and actual output

Final captured output:

```text
Production compile exit: 0
PASS: 42 domain, authentication, authorization, and integrity checks.
PASS: 11 paging, background loading, stale-result, and role-navigation checks.
Rendered offscreen snapshots: login, admin, nurse, paged-10000. No interactive desktop/dialog test performed.
Verification command exit: 0
```

Production compilation used every Java source under `src` with `javac -d out`. The verification launcher compiles `src`, `tests`, and `demo` together and runs two headless executables. Captured output is in `verification/build.txt` and `verification/checks.txt`; the report does not substitute for those files.

Checks cover bootstrap/login/logout, failed-login lockout and expiry, inactive accounts, role updates without password replacement, unauthorized direct operations, patient/campus uniqueness, campus edits, role-specific navigation, staff impersonation, allergy override, stock conservation across successful/failed dispensing, earliest-expiry order, expired batches, reports, notification ownership/status, audit privacy, detached read results, and session invalidation. Paging checks cover 10,000 rows, the last page, filtering/page reset, background execution, bounded model rows, and stale results.

Offscreen snapshots: `verification/login.png`, `admin.png`, `nurse.png`, and `paged-10000.png`. They exercise Swing layout and painting without opening native windows. Real desktop dialog cancellation, focus, Enter behavior in modal forms, clipping at different display scales, and subjective scrolling smoothness were not interactively tested. Build/debug errors encountered during implementation were corrected before the final passing run. The host blocks direct PS1 execution, so the provided launchers use inline PowerShell commands from CMD without changing execution policy.

## Limits and decisions requiring follow-up

- There is no database or existing persistent integration to preserve. Accounts, campuses, patients, inventory, records, and audit state are genuinely maintained in memory during the process, then lost on exit. A durable repository and deployment configuration remain future work; no simulated database or fake write success was added.
- Exact UI-diagram alignment remains unverified. Campus membership of users/patients is not invented; the supplied diagram uses a campus string, but the existing ClinicSystem has no campus-assignment contract. The new campus catalog supports actual add/edit operations independently.
- The existing ClinicSystem interface remains unchanged. Paged queries use additional application/service entry points; legacy list-returning API methods remain available for existing consumers. In-memory query scans are O(n), aggregation consumes service-side working memory, and the entire in-memory repository still grows with stored data. This is bounded UI rendering, not database/server pagination.
- Existing safety comments describe a limited medicine/allergen mapping. The implementation handles exact recorded medicine allergies and the documented Amoxicillin/Penicillin rule; a complete interaction database is not present. An override logs confirmation plus reason-presence/length metadata; free-text reason content is withheld to avoid introducing patient names or allergy text into audit details.
- Default analytics policy is provisional: low stock 10 units, expiry 30 days, outbreak 3 distinct students in a section/symptom group over 7 days, recurring visits 3 over 30 days, forecast 30-day usage. These are implementation defaults, not verified clinical policy or diagram requirements.
- Guardian messages are generated locally; Mark sent records the operator's confirmation of delivery outside the app. No email/SMS integration or outbound communication is claimed.
- Patient deletion, editing saved clinical records, appointment scheduling, password reset, and campus assignment lack established working contracts and were not added arbitrarily. User/campus editing and account deactivation are implemented. Saved visits remain immutable as the original source notes specify.

## Review-comment checklist

| Original review comment | Evidence | Status |
|---|---|---|
| 1. Separate files | `app/nav`, `app/ui/<feature>`, dedicated table models and services; separate focused feature panels and table models | Implemented |
| 2. Reusable popups | `ui/common/popup/FormPopup`, `FormField`, `MessagePopup`, `WarningOverridePopup` | Implemented; native-dialog interaction still requires manual verification |
| 3. Latest navigator diagrams | Provisional module map above and supplied class/sequence diagrams | Latest Drive UI diagrams inaccessible; alignment unverified |
| 4. Selective table rendering | `PageResult`, `PagedTablePanel`, `AsyncLoader`, `demo/PagedTableDemo`, `tests/UiChecks` | Implemented and headlessly checked |
| 5. Separate admin dashboard | `AdminShell`, `AdminDashboardPanel`, `UserManagementPanel`, `CampusManagementPanel`, `AdminService` | Implemented and role checks exercised |
| 6. Login first | `GUI`, `Router`, `ui/auth/LoginPanel`, `Login` | Implemented; domain login/logout checked |

The full file-by-file manifest is in `FILES.md`. Changes are described here and through Git diffs instead of adding visual change markers to production code.

## Follow-up UI changes and local visit search (October 23, 2026)

The obsolete Analytics UI files were removed; UICClinicDashboard now delegates to Reports. Its old Status Report / Performance Snapshot and hardcoded safety card are absent. The dashboard now includes Recent Clinic Visits with + New Consultation, shared form validation, paged newest-first data, Section / Position and Type columns, and formatted dates. The Safety warning is a modal reusable popup shown only during conflicting dispensing, with Cancel as the default and a required ten-character override reason. Successful overrides are logged by the secured service without patient or reason text.

The Recent Clinic Visits header now owns a reusable SearchBar. It debounces for 300 ms, provides a placeholder and clear button, and drives This week / Today / All filtering before paging. A week starts Monday at midnight. Date and name search uses added overloads in ApplicationContext and InMemoryClinicSystem, delegating to VisitSearch; ClinicSystem itself remains unchanged. The old paged overloads remain available to existing callers. New searches reset to page 1, and stale background results are rejected. Patient-name queries are never included in audit details; the search audit message is only `visit search performed`.

The demo generates 10,000 dummy visits plus four named fixture visits, including last-month history. It prints Cruz / 2022 / xyz results and Cruz with All, and can automatically exercise the actual shared search and paging controls in a Swing window. These are demo data, not production records. Run instructions are in README.md. Source files added specifically for this follow-up are SearchBar.java, VisitPeriod.java, VisitSearch.java, DatedDemoVisit.java, and VisitSearchDemo.java; integration changes are in RecentVisitsPanel.java, PagedTablePanel.java, ApplicationContext.java, and InMemoryClinicSystem.java.

Medicine Predictions was not implemented: its separately proposed usage API has not been added to ClinicSystem. No latest-Drive diagram alignment or durable database storage is claimed.
