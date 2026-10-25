# SCMS audit, implementation, and release verdict

Date: October 25, 2026. Scope: the current local repository, including changes already present before this audit. Scores are engineering judgments on a 1–10 scale, not certification, benchmark measurements, or clinical validation.

## Verdict

**Before:** a substantial academic/local desktop prototype with functioning clinic transactions, durable embedded storage, separate administration, and useful regression coverage. The AI integration was the least reliable component: its request path lacked session revocation, response parsing searched for arbitrary text fields, configuration was silently overridden by the demo launcher, and the UI had no cancellation or bounded chat retention. Existing cloud errors did not establish their exact provider-side cause.

**After:** the same clinic workflows remain operational in automated checks. AI access now captures a nurse session and checks it before sending and after receiving; parsing validates the response structure, complete finish status, and non-thought text parts; the panel cancels and ignores stale work, preserves failed input, limits displayed history, and wraps errors. Sensitive exception details are no longer written by the generic popup logger. These are concrete reliability improvements, not proof of successful live cloud integration or production privacy compliance.

**Deployment verdict: demonstration and controlled synthetic-data evaluation only.** Real student health data needs institutional privacy review, backup/recovery verification, operational access controls, and a reviewed cloud boundary. A free-text checkbox and regular expressions cannot guarantee de-identification. No live provider request was made during this audit, and previously exposed credentials were not used.

## Evidence and coverage

The audit traced Main/GUI/Router/ApplicationContext; Login and PasswordHasher; role subclasses and SecuredClinicSystem; the patient, consultation, dispensing, inventory, notification, reporting, settings and administration panels; shared forms/tables/background loading; Database/Schema/ClinicRepository; GeminiAiService; launch scripts; and executable test assertions. The preceding documentation review inspected the same domain and schema. No appointments module or appointment table exists. The audit did not modify real records, change schema, or claim exhaustive path coverage.

`git status` initially contained user changes to `.project` and `.vscode/launch.json`, documentation artifacts, and verification screenshots. Those user edits were preserved. Screenshots refreshed by regression suites are fixture evidence, not production data.

## Scores before and after

| Area | Before → After | Evidence / strengths | Weakness and required improvement |
|---|---|---|---|
| Functional completeness | 7 → 7 | Patient registration/allergies, immutable consultations, stock/dispensing, reports, notifications and account/campus management pass core checks. | General patient edit, appointments, exports and password recovery are absent. Define requirements before implementation. |
| UI/UX consistency | 6 → 7 | Shared Theme, forms, tables and sidebar; assistant now has wrapping status, retained input, cancellation and explicit newline binding. | ID entry is manual; chat cards use excessive space; selected-patient workflow and long-content/DPI review remain. |
| Architecture | 7 → 7 | UI/service/repository separation, ClinicSystem abstraction, reusable domain logic. | Aggregate loading and reflection add complexity; legacy screens remain. Session-aware AI factory currently depends on ApplicationContext. |
| Database integrity | 8 → 8 | Foreign keys, check constraints, composite notification ownership, encryption, rollback and restart checks. | Most list operations load aggregates; backup restoration and large-database measurements are missing. |
| Authentication/authorization | 6 → 7 | PBKDF2, lockout, active status, role checks and retained-clinic-service invalidation. AI now captures/rechecks nurse session. | No MFA, inactivity timeout or password-change workflow. These are requirements gaps, not newly demonstrated exploits. |
| Privacy | 4 → 5 | No automatic patient-context transfer; sanitized general audit events; generic exception logs now omit values/stack traces. | Typed text can still contain names/health information. Desktop credentials remain accessible to the operating-system user. Institutional processing basis and cloud agreements unresolved. |
| Reliability/performance | 6 → 7 | Rollback/stale table checks; AI cancellation, bounded chat and stale-result rejection; failed nurse summary clears old numbers. | Full aggregate reads, login-lock contention during clinic I/O, and body buffering need profiling and hardening. No measured performance SLA. |
| AI integration | 3 → 5 | Real HTTPS path; 28 new simulated transport/parser/session checks; explicit model override preserved. | Live generation still unverified. No model-discovery UI, backend proxy or full provider compatibility validation. |
| Accessibility/ease of use | 5 → 6 | Existing form labels; new accessible names for assistant input/status and explicit Shift+Enter. | Screen reader announcements, contrast audit, complete keyboard traversal and alternate DPI remain unverified. |
| Maintainability/testability | 6 → 7 | Existing plain-Java suites plus deterministic fake HttpClient checks; obsolete AI parser removed. | Custom JSON implementation should eventually be assessed against an approved maintained library; no CI result is claimed. |
| Uniqueness/practical value | 6 → 6 | Local operation, staff separation, expiry-aware dispensing, documented overrides and notification ownership are useful together. | No local nurse usability study establishes differentiation. Queue/handoff and actionable stock tasks would add more value than extra cards. |
| Deployment readiness | 3 → 3 | Repeatable Windows launchers, persistent local store, startup/shutdown checks. | Recovery, provider privacy review, support ownership, packaging, clinical policy validation and operational monitoring remain open. |

Overall qualitative assessment improves from approximately **6/10 to 6.5/10** for the prototype. Deployment readiness remains **3/10**. These are deliberately separate judgments; passing tests does not make the application suitable for unsupervised clinical use.

## Workflow findings

| Workflow | Trace and observed boundary |
|---|---|
| Login | GUI → LoginPanel → Login → salted password comparison / active flag / lockout → Router. Role launchers reject mismatches. Production bootstrap differs from demo fixtures. |
| Patient registration | PatientsPanel → form validation → session-bound ClinicSystem → SecuredClinicSystem → repository → domain → SQL insert. Duplicate ID rejected; returned profiles detached. |
| Consultation | NewConsultationForm checks registered patient, submits symptom/reason/remarks and authenticated staff ID. Repository stores visit and symptoms transactionally. Full visit editing is absent. |
| Dispensing | UI checks warning then re-submits to domain validation; inventory consumes earliest unexpired batches. Transaction covers stock and dispensing rows. Legacy boolean override API remains more permissive than the UI's documented-reason overload. |
| Notifications | Create validates patient/transaction ownership; preview formats text; Mark sent records externally completed delivery. No email/SMS is sent. |
| Reports | Selected encounter formats into text. No PDF/Excel report export or date-range report builder is implemented. |
| Administration | Account/campus operations require administrator service access. Account active/name/role and campuses persist. Campus membership is not represented in patients/users. |
| AI | Nurse panel → session-aware client factory → bounded input screening → HTTPS → structured JSON candidate validation → session recheck → UI generation check. No DB/action tools are provided to the model. |

## Prioritized roadmap and acceptance criteria

The first dependency is preserving baseline core tests. Next comes session/error containment, then protocol correctness and request lifecycle, then documentation and regression. Optional workflows follow only after their requirements are established.

| Priority / status | Problem and evidence | User benefit / affected files | Dependencies and risks | Acceptance / verification |
|---|---|---|---|---|
| P0 — implemented | AI panel checked nurse only at construction; service had no session context. | Prevent retained sessions from requesting/receiving AI output. ApplicationContext, GeminiAiService. | Do not hold login lock during network I/O. Already transmitted requests cannot be retracted. | Non-nurse denied; logout prevents send; relogin does not revive old client; logout during send suppresses result. New AiChecks pass. |
| P0 — implemented, broader review open | MessagePopup logged arbitrary exception and stack; provider errors could reflect prompt data. | Reduce sensitive values in UI/log error paths. MessagePopup, GeminiAiService. | Less raw diagnostic detail; use controlled status/model metadata instead. | Simulated provider bodies containing secret/private text do not appear in public errors. Source confirms logger outputs exception class only. |
| P0 — open deployment gate | Free text can contain identifiable health information despite regex/checkbox. | Institution-approved disclosure boundary. AI service/panel, future authenticated backend. | Requires school data owner/privacy officer decisions; cannot invent consent or legal basis. | Review data flow, provider terms/retention, appropriate processing basis and adversarial privacy cases before real-data rollout. |
| P1 — implemented | Arbitrary first-text extraction ignored candidates, thought parts, blocked/truncated results; incomplete escaping. | Correct answers or explicit failure. GeminiAiService, new Json reader. | Preserve no-extra-runtime-dependency build; custom parser still needs broader conformance review. | Unicode/control escapes, duplicate/trailing malformed JSON, multipart output, safety block, token truncation tested. |
| P1 — implemented | No cancellation, stale-response suppression, retry input preservation or chat bound. | Predictable nurse navigation and recoverable input. AiNurseAssistantPanel. | Cancellation is best effort; cannot undo provider processing/cost. | Worker interrupted on cancel/hide/disposal; generation guards callbacks; max 20 messages; text remains on failure. Core UI rendering passes; full delayed-network UI race test remains open. |
| P1 — implemented | NurseDemo removed GEMINI_MODEL even when user intentionally set it. | Explicit configuration is honored. NurseDemo.cmd, README. | Available models are provider/account dependent. No automatic model switching. | Script no longer removes environment setting; README states default and override. Live availability unverified. |
| P1 — implemented | Failed dashboard refresh left previously successful totals visible. | Avoid treating stale counts as current. NurseDashboardPanel. | Larger “Unavailable” text may need further DPI review. | Error path clears all four totals. Core UI regression passes; dedicated failed-dashboard screenshot not executed. |
| P1 — open | No verified backup/restore operation. | Recover local records after disk/device loss. Database, Settings, backup tooling. | Encryption key must accompany correct DB; scope and retention need operator policy. | Closed-store backup restored into separate folder; integrity checks and documented recovery rehearsal. |
| P2 — implemented | AI status was a single-line label and newline binding implicit. | Readable errors, named input/status, explicit Shift+Enter. Assistant panel. | Screen-reader announcements not validated. | Offscreen 1280x800 snapshot inspected; existing role-navigation and native workflow checks pass. |
| P2 — open | Patient selection requires re-entering identifiers. | Faster patient → history → consultation workflow, useful offline. PatientsPanel, consultation form, ClinicSystem readers. | Preserve visit immutability and role filtering; define identity confirmation. | Select a patient and prefill the correct immutable ID; cancellation changes nothing; unauthorized history unavailable. |
| P2 — open | Repository materializes full clinical aggregate for many lists. | Predictable latency as records grow. ClinicRepository/ClinicData/PageResult. | Must preserve filtering, scope and ordering semantics. | SQL paged equivalents match existing fixture results and improve measured memory/time at agreed scale. |
| P2 — open | Permissions are audited before execution; allowed does not mean completed. | Clear operational accountability. SecuredClinicSystem/AuditLog. | Define outcome fields and migrations; never log clinical free text. | Success/failure distinguishable without recording patient notes; failed writes not logged as completed. |
| P3 — proposed | No queue/handoff or stock-task workspace. | Nurse throughput, pending guardian delivery, actionable expiry/restock list. Dashboard/Inventory/Notifications. | Queue status and ownership need explicit school policy. | Nurse-reviewed task ordering, accurate counts, useful when cloud is unavailable; evaluate with staff before adding schema. |
| P3 — proposed | No appointment contract or reporting export policy. | Follow-up tracking and authorized operational summaries. New modules only after requirements. | Scheduling transitions, access, retention, and export destinations are consequential choices. | Approved acceptance cases and privacy review precede implementation. |

## Research and product strategy

The following sources inform priorities rather than prove this application meets a standard:

- [National Privacy Commission: Data Privacy Act](https://privacy.gov.ph/data-privacy-act/) identifies health and education information within sensitive personal information. This makes the application's cloud boundary and local recovery/access policies deployment concerns. Institutional review is needed; this audit is not a legal compliance determination.
- [Magnus Health: school health department workflows](https://magnushealth.com/health-department/) describes medication tracking, inventory auditing, and student check-in queues. These vendor-described capabilities suggest operational task completion as a useful comparison; vendor outcome claims are not evidence for SCMS.
- [W3C error identification guidance](https://www.w3.org/WAI/WCAG22/Understanding/error-identification) supports identifying errors in text with actionable correction. [W3C status-message guidance](https://www.w3.org/WAI/WCAG21/Understanding/status-messages) emphasizes programmatic availability of feedback. These web guidelines inform Swing design but are not a claim of Swing WCAG conformance.

For a Philippine school with variable connectivity, the strongest practical direction is dependable offline records, patient-to-consultation continuity, stock expiry tasks, and externally confirmed guardian follow-up. A cloud chat feature should remain optional. No competitive novelty or user-efficiency percentage is claimed without a school-specific usability study.

## Implemented changes and file manifest

| File | Purpose |
|---|---|
| `src/clinic/app/ApplicationContext.java` | Capture/recheck a nurse session using current identity and session version. |
| `src/clinic/ai/GeminiAiService.java` | Session-aware creation and pre/post-send checks; structured candidate parsing; thought exclusion; blocked/truncated rejection; safe error messages; complete control-character escaping. |
| `src/clinic/ai/Json.java` | New strict bounded JSON reader/encoder; no evaluation or external runtime dependency. |
| `src/clinic/app/ui/assistant/AiNurseAssistantPanel.java` | Cancel action; cancel on hide/disposal; generation checks; retained input; bounded 20-message display; draft label; multiline status; accessible names; explicit Shift+Enter. |
| `src/clinic/app/ui/common/popup/MessagePopup.java` | Log unexpected exception type without arbitrary exception text or stack trace. |
| `src/clinic/app/ui/dashboard/NurseDashboardPanel.java` | Clear previously loaded KPI values after summary load failure. |
| `NurseDemo.cmd` | Preserve explicit GEMINI_MODEL environment configuration. |
| `README.md` | Correct H2 runtime classpaths, model/default behavior, limitations and audit reference. |
| `.gitignore` | Ignore generated compilation directories and documentation dependency directory. |
| `tests/AiChecks.java` | New deterministic tests for JSON, response handling, simulated HTTP/errors and session invalidation. |
| `tools/verify.cmd` | Include new AI check executable in existing verification. |
| `docs/SCMS_AUDIT_2026-10-25.md` | This verdict, roadmap, evidence, limitations and handoff. |

No business schema migration or clinical rule changes were made. No unsupported appointment/edit/delete operation was added. The previously generated documentation PDF remains a historical pre-audit snapshot; README points to this update.

## Verification results

Baseline `tools/verify.cmd` completed successfully before the changed build: 56 domain checks, 36 UI checks, 49 persistence checks, plus recent-visit/search fixtures. The changed build completed the same checks and **28 new AI checks**. `tools/verify-desktop.cmd` completed **19 native Swing workflow checks**, covering setup/login, users/campuses, required fields, patient/consultation saves, dispensing, safety cancellation/override, restock, notification status, reports, refresh/resizing and close confirmation.

No failures were reported by these runs. Simulated AI tests used a fake HttpClient and synthetic strings, never a real key or live cloud request. They covered successful completed multipart/Unicode output, thought-part exclusion, malformed/trailing/duplicate/deep JSON, safety blocks, output truncation, input-size/identifier rejection before transport, HTTP 400/401/403/404/429/500 safe errors, timeout, nurse-role checks, logout, same-user relogin, and logout while a response is pending.

The assistant fixture screenshot was visually inspected at 1280x800: Cancel is present and the status occupies separate multiline space below consent. This does not verify all response lengths, assistive technology, alternate DPI, or multiple monitors. A deterministic delayed-network panel cancellation/duplicate-send test is still needed; service-level session tests are not a substitute for that UI test.

## Remaining material risks and next priorities

1. Resolve cloud privacy and credential handling before real patient data; current screening cannot guarantee exclusion. A request already submitted before logout may still be processed remotely even if the result is suppressed.
2. Verify a real provider request with a rotated, locally configured credential and account-supported model. No recommendation to swap model names blindly or silently retry is made.
3. Exercise backup/restore and establish retention, staff access, encryption-key custody, incident handling, and support ownership.
4. Bound HTTP body accumulation before parsing (the JSON reader's size cap is applied after the HTTP body is buffered). Assess an approved maintained JSON library, full Unicode conformance, provider changes and dependency review.
5. Remove ambiguity from the legacy boolean override API through a separately reviewed contract change; the UI's documented-reason rule is not universal to legacy callers.
6. Profile aggregate-loading queries and login-lock contention; add meaningful latency/memory targets before claiming scalability.
7. Complete keyboard/screen-reader/DPI review and nurse-led usability sessions. Prioritize a selected-patient workflow and stock/guardian task completion over visual ornament.

Real deployment requires all critical gates above to be resolved and school-authorized workflow/clinical policies to be documented. This audit delivers tested improvements and an explicit roadmap, not a declaration that every proposed capability is complete.
