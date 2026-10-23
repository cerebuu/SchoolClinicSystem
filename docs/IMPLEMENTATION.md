# Application audit and verification — October 24, 2026

This report supersedes earlier implementation notes. The application remains Java 21 / Swing and uses a pinned embedded H2 JDBC driver. Existing nurse, assistant, doctor, and Superadmin workflows are preserved. The existing change to `.vscode/launch.json` was left untouched.

## Implemented fixes

- Notification creation, list, and page queries return detached snapshots. Mutating a returned notification cannot bypass service authorization. Repeated sent-status updates are rejected. Generated messages contain the patient name and dispensed medicine rather than an ID used as a name.
- Low-stock summaries aggregate available quantities across batches and count each medicine once. Empty batches no longer produce expiry alerts. Expiry queries still include expired stock requiring attention.
- Safety override reasons from the dialog are retained in transaction remarks and formatted transaction reports. The secured service logs confirmation without copying free-text reasons into the general audit log. A reason shorter than ten characters is rejected before stock changes. The old boolean dispensing signature remains available; a String overload supports documented overrides.
- Blank/null symptom lists and blank symptom entries are rejected in the domain. Invalid visits do not change history. Blank passwords are rejected when creating accounts.
- Failed reauthentication clears the previous session. Retained clinic services check session identity and version under the login lock before executing operations.
- Cached pages refresh their tables and summary metrics when revisited. Assistants can see their own consultations on the Consultations page using the existing staff-scoped query.
- Table loading disables stale selection; failed loads clear rows and total count and allow retry. Repeated dashboard refreshes ignore older results.
- Closing the application requires confirmation and closes the local encrypted database cleanly.

## Interface changes

Shared styling covers all routed screens: neutral card borders, consistent Segoe UI typography, clearer page headers, corrected status badges, distinct navigation icons, responsive search controls, concise pagination status, wrapped empty states, and a scrollable sidebar. Login accommodates first-run setup and error messages. Forms use consistent friendly error text and support Escape cancellation when idle.

The Superadmin dashboard now shows actual account/campus/audit totals and a searchable, paged campus catalog. The visual-only campus selector and fabricated campus comparison cards were replaced. Clinic screens no longer claim a sample campus assignment. Settings now presents readable account and storage information. Nurse and Superadmin workspaces remain separate; underlying permission checks are retained.

## Important files

- `src/clinic/app/Theme.java`, `ui/common`, and `nav/Navigator.java`: shared design and navigation refresh.
- `ui/admin/AdminDashboardPanel.java` and `service/AdminService.java`: real dashboard data and authorized summaries.
- `core/Login.java`, `app/ApplicationContext.java`: reauthentication and retained-session checks.
- `service/InMemoryClinicSystem.java`, `Inventory.java`, `SecuredClinicSystem.java`: notification integrity, stock summaries, and documented overrides.
- `model/NotificationLog.java`, `MedicalRecord.java`, `MedicineTransaction.java`: detached status snapshots, validation, and transaction remarks.
- `tests/DomainChecks.java`, `UiChecks.java`, and new `DesktopChecks.java`: domain, Swing, and native integration regression coverage.
- `Run.cmd`, `tools/verify.cmd`, `tools/verify-desktop.cmd`: repeatable UTF-8 build/run/check entry points.
- `persistence/Database.java`, `ClinicRepository.java`, `AccountRepository.java`, and `Schema.java`: encrypted durable storage, schema migration, prepared queries, and transaction boundaries.

## Verification

Production compilation passed (exit 0). The final headless run passed 56 domain/security checks and 35 Swing checks, plus the recent-visit and 10,004-record search suites. The final native run passed 19 workflow checks (exit 0). Actual captured output is stored in `verification/build.txt`, `verification/checks.txt`, and `verification/desktop-checks.txt`. The headless suite checks domain integrity, login/lockout/deactivation, permissions, session invalidation, notification snapshots, inventory arithmetic, override validation/privacy, filtering, pagination, stale results, failed-load recovery, and role navigation. Visit-search fixtures cover 10,004 records, Monday boundaries, Today/All scopes, future exclusion, and audit privacy.

The native suite creates real Swing windows and uses Swing actions to exercise first-run setup, failed/successful login, user creation, campus creation/editing, form validation/cancellation, patient registration, consultations, dispensing, notifications and delivery confirmation, warning cancellation/override, restocking, cached inventory refresh, reports, dashboard refresh, resizing, and confirmed/cancelled application shutdown. It uses isolated in-memory test data and closes its windows. Screenshots include `desktop-inventory-1100.png` and `desktop-nurse-1280.png`.

These automated desktop checks invoke Swing actions; they do not substitute for manual keyboard, accessibility, pointer interaction, or display-scaling testing.

## Scope and remaining limitations

- Normal startup persists clinic data in an encrypted local H2 database. It is not a substitute for operational backup, recovery, access-control policy, or a production clinical-record deployment. The `--demo` fixture and automated checks intentionally use non-production data stores.
- Campuses are a real editable catalog, but patients/users are not assigned to campuses in the existing schema. No campus-specific totals are claimed.
- Guardian messages are generated locally. An operator must deliver them outside the application before using Mark sent. No email/SMS integration exists.
- The safety checker implements the existing limited allergy rules; no comprehensive clinical interaction database is present. Existing analytics thresholds remain application defaults.
- Clinical visits are immutable; patient deletion, record editing, password recovery, and appointments are outside existing implemented contracts. Account editing/deactivation and campus editing remain available.
- Table data is paged in memory, with at most 25 rendered rows per page. Filtering scans stored records. No global sorting feature or database pagination is claimed.
- `ModulePage` and `NurseDashboard` are unused legacy classes retained for source compatibility. Routed workspaces use the implemented panels; no runtime placeholder page uses them.
- Multiple-monitor layouts, non-default DPI, screen readers, and full keyboard traversal require manual review.

## Run

From the repository root with JDK 21 on PATH:

```text
cmd /c Run.cmd
cmd /c tools\verify.cmd
cmd /c tools\verify-desktop.cmd
```

Normal startup offers first-administrator setup and contains no sample accounts. `Demo.cmd` explicitly starts the existing sample-account fixture. The role launchers continue to require authentication.
