const fs = require('fs');
const path = require('path');
const PDFDocument = require('../.documentation-tools/node_modules/pdfkit');
const root = path.resolve(__dirname, '..');
const dest = path.join(__dirname, 'SCMS_Final_Project_Documentation.pdf');
const doc = new PDFDocument({size:'A4', margins:{top:72,bottom:72,left:72,right:72}, bufferPages:true,
  info:{Title:'School Clinic Management System - Software Development Document',Author:'SCMS Project',Subject:'Verified project documentation, October 25, 2026'}});
doc.pipe(fs.createWriteStream(dest));
const W=451.28, ink='#17212b', accent='#a61e4d';
let toc=[];
function page(){doc.addPage();doc.fillColor(ink).font('Times-Roman').fontSize(12);}
function h(t){if(doc.y>680)page();doc.moveDown(.6).fillColor(accent).font('Times-Bold').fontSize(14).text(t);doc.moveDown(.4).fillColor(ink).font('Times-Roman').fontSize(12);}
function sub(t){if(doc.y>690)page();doc.moveDown(.4).font('Times-Bold').fontSize(12).text(t);doc.moveDown(.25).font('Times-Roman');}
function p(t){doc.fillColor(ink).font('Times-Roman').fontSize(12).text(t,{width:W,lineGap:1.2});doc.moveDown(.55);}
function bullet(t){p('• '+t);}
function section(t){page();toc.push([t,doc.bufferedPageRange().count]);h(t);}
function table(head,rows,widths){
  const x0=72; widths=widths||head.map(()=>W/head.length);
  const draw=(cells,header=false)=>{
    doc.font(header?'Times-Bold':'Times-Roman').fontSize(10);
    const height=Math.max(...cells.map((c,i)=>doc.heightOfString(String(c),{width:widths[i]-12,lineGap:1})))+14;
    if(doc.y+height>755){page();draw(head,true);}
    const y=doc.y;let x=x0;
    cells.forEach((c,i)=>{doc.rect(x,y,widths[i],height).fillAndStroke(header?'#f9e8ef':'#ffffff','#bcc2c8');doc.fillColor(ink).text(String(c),x+6,y+6,{width:widths[i]-12,lineGap:1});x+=widths[i];});
    doc.x=x0;doc.y=y+height;
  };
  draw(head,true);rows.forEach(r=>draw(r));doc.moveDown(.7);
}
function code(t){doc.font('Courier').fontSize(8.5).fillColor(ink).text(t,{width:W,lineGap:1});doc.moveDown(.8).font('Times-Roman').fontSize(12);}
function screenshot(name,caption){const f=path.join(root,'verification',name+'.png');if(!fs.existsSync(f))return;if(doc.y>400)page();doc.image(f,72,doc.y,{fit:[W,255]});doc.y+=265;doc.font('Times-Italic').fontSize(10).text(caption,{width:W});doc.moveDown().font('Times-Roman').fontSize(12);}
function box(x,y,w,hh,title,body){doc.rect(x,y,w,hh).fillAndStroke('#fff8fb','#a61e4d');doc.fillColor(ink).font('Times-Bold').fontSize(10).text(title,x+7,y+7,{width:w-14,align:'center'});if(body)doc.font('Times-Roman').fontSize(9).text(body,x+7,y+26,{width:w-14});}
function arrow(x1,y1,x2,y2){doc.moveTo(x1,y1).lineTo(x2,y2).strokeColor('#697580').stroke();let a=Math.atan2(y2-y1,x2-x1),s=5;doc.moveTo(x2,y2).lineTo(x2-s*Math.cos(a-.5),y2-s*Math.sin(a-.5)).moveTo(x2,y2).lineTo(x2-s*Math.cos(a+.5),y2-s*Math.sin(a+.5)).stroke();}
function sequence(title,actors,steps){page();h(title);const top=doc.y+12, xs=actors.map((_,i)=>82+i*(W-20)/(actors.length-1));actors.forEach((a,i)=>{doc.fillColor(accent).font('Times-Bold').fontSize(9).text(a,xs[i]-36,top,{width:72,align:'center'});doc.moveTo(xs[i],top+35).lineTo(xs[i],top+35+steps.length*54).dash(3).strokeColor('#aab2bb').stroke().undash();});steps.forEach(([a,b,t],i)=>{let yy=top+55+i*54;arrow(xs[a],yy,xs[b],yy);doc.fillColor(ink).font('Times-Roman').fontSize(9).text((i+1)+'. '+t,Math.min(xs[a],xs[b])+4,yy-27,{width:Math.max(Math.abs(xs[b]-xs[a])-8,80),align:'center'});});doc.x=72;doc.y=top+75+steps.length*54;}

doc.fillColor(accent).font('Times-Bold').fontSize(16).text('SCHOOL CLINIC MANAGEMENT SYSTEM',{align:'center'});
doc.moveDown().fontSize(14).text('Software Development Document',{align:'center'});
doc.moveDown(5).fillColor(ink).font('Times-Roman').fontSize(12).text('An Object-Oriented Programming Project\nPresented to the Faculty of the\nCollege of Computer Studies\nUniversity of the Immaculate Conception\nFather Selga St., Davao City, Philippines',{align:'center',lineGap:5});
doc.moveDown(4).text('Team Members: Author names were not supplied in the available project materials.',{align:'center'});
doc.moveDown(4).text('OCTOBER 2026\nDocumentation finalized: October 25, 2026',{align:'center',lineGap:5});
doc.moveDown(3).fontSize(10).text('Prepared from the current source tree, supplied sample template, and executed checks. Institutional wording follows the template; this document does not assert instructor approval.',{align:'center'});

section('Document Guide and Verification Basis');
p('This final document follows the supplied Software Development Document template: Software Purpose; Objectives, Features, and Modules; Technology Used; User Manual; Technical Manual; Project Development Phases; Limitations and Future Development; Appendix; and Comments/Grading. The requested Test Cases section is the final section. Template examples about grading software and unrelated social applications have been replaced with SCMS content.');
p('The evidence baseline is the workspace on October 25, 2026. Source code takes precedence over older README statements and prior conversational claims. The original PDF is preserved. Actual team names, a signed project schedule, instructor identity, rubric scores, and a submission deadline were not supplied; their absence is stated explicitly.');
table(['Evidence','Observed result'],[
['tools/verify.cmd','Executed successfully on October 25, 2026: 56 domain/security checks; 36 UI checks; recent-visit and 10,004-record search checks; 49 persistence checks.'],
['tools/verify-desktop.cmd','Executed successfully on October 25, 2026: 19 native Swing workflow checks.'],
['Source inspection','GUI routing, domain models/services, schema/repositories, validation, authentication, AI client, launchers, and test assertions inspected.'],
['AI service evidence','Live success remains unverified. Earlier user-provided runtime screenshots showed missing configuration, HTTP 404, and timeout failures. No cloud call was made for this documentation.'],
['Screenshots','Included screenshots are produced by the project verification fixtures. They are demonstration/test data, not real student records.']],[125,W-125]);
p('“Pass” in the final table means the stated scenario was exercised by a successful check during this documentation run. “Not Tested” covers unexecuted scenarios. Successful regression suites do not prove complete production readiness.');

section('1. Software Purpose');
sub('Project Description');
p('The School Clinic Management System (SCMS) is a Java desktop application for recording school-clinic encounters, registering student and employee patients, tracking medicine inventory and dispensing, generating visit reports, and preparing guardian notification messages. It separates administrative account management from daily nursing operations. Normal startup stores information in a local encrypted H2 database. An explicit demo mode uses session-only fixtures.');
sub('Background and Problem Statement');
p('The implementation addresses the need to organize patient identification, visit documentation, allergy checks, stock balances, and follow-up messages within one role-aware workspace. Its rationale is to reduce fragmented record handling and make the relationships between patients, staff, medicine transactions, and notifications explicit. No survey, institutional deployment statistics, or measured efficiency improvement was supplied, so no quantitative benefit is claimed.');
sub('Purpose and Key Value');
bullet('For school nurses: searchable patient and consultation records, stock visibility, and controlled medicine recording in a consistent workspace.');
bullet('For administrators: authenticated staff-account creation, role and activation management, a campus catalog, and an audit viewer.');
bullet('For record integrity: duplicate checks, foreign keys, transaction rollback, immutable visit records, and permissions enforced through services.');
bullet('For maintainers and evaluators: a concrete object-oriented structure with shared interfaces, subtype behavior, reusable Swing components, and executable checks.');
sub('Target Users and Scope');
p('Target users are school nurses, nurse assistants, doctors, and administrators. Patients are students and employees; patients and guardians do not have self-service login screens. The system is a single-process desktop application using one local database instance. The campus catalog is maintained independently: user and patient campus membership is not defined in the schema.');

section('2. Objectives, Features, and Modules');
sub('2.1 Core Objectives');
bullet('Provide authenticated, role-specific access to clinic and administrative screens.');
bullet('Record patient registration, allergies, consultations, and medicine transactions using established model fields.');
bullet('Maintain unexpired medicine balances and retain a reason for warnings overridden through the nurse UI.');
bullet('Persist accounts, clinical records, stock, notification status, and audit events across restarts.');
bullet('Demonstrate encapsulation, inheritance, polymorphism, abstraction, and separation of interface, domain logic, and persistence.');
sub('2.2 Key Features and Implementation Status');
table(['Feature','Current behavior and status'],[
['Authentication','Implemented: first-administrator setup, salted password hashing, active-account checks, lockout, logout, and role-specific launchers.'],
['Patients','Implemented: register Student or Employee, search, view details, and add allergy. General profile editing/deletion is not exposed.'],
['Consultations','Implemented: registered patient lookup, symptom choice, reason, optional remarks, staff-bound save, history/search. Visits are immutable.'],
['Medicine inventory','Implemented: add batch, restock, quantity/expiry validation, stock totals, low-stock and expiry summaries, forecast display.'],
['Dispensing','Implemented: positive quantity, patient/medicine validation, allergy warning, cancellation, documented override, earliest-expiry stock consumption.'],
['Notifications','Implemented: transaction-linked draft, preview, and manual delivery confirmation. No SMS/email transmission.'],
['Reports','Implemented: format the selected stored consultation as text in a dialog. No built-in PDF/Excel export claimed.'],
['Superadmin','Implemented: account creation/edit/deactivation, campus creation/edit, searchable audit log, actual system totals.'],
['AI Nurse Assistant','Implemented client and UI. Optional external Gemini request; live successful response not verified. It receives typed text only and cannot execute clinic actions.']],[118,W-118]);
sub('2.3 System Modules (OOP Structure)');
code('clinic.app.Main -> GUI -> ApplicationContext -> Router\n  app.nav: NurseShell / SuperadminShell / MainShell\n  app.ui: auth, dashboard, patients, consultations, dispense,\n          inventory, notifications, reports, settings, assistant\nclinic.core: Login, PasswordHasher, ClinicSystem\nclinic.model: Person -> Patient -> Student / Employee\n              User -> Administrator / SchoolNurse /\n                      NurseAssistant / Doctor\n              MedicalRecord, MedicineTransaction, Medicine\nclinic.service: SecuredClinicSystem, InMemoryClinicSystem,\n                Inventory, SafetyChecker, PatternDetector,\n                RestockForecast, AdminService, AuditLog\nclinic.persistence: Database, Schema, Sql,\n                    AccountRepository, ClinicRepository,\n                    AdminRepository\nclinic.ai: GeminiAiService, AiServiceException');
sub('Object-Oriented Principles');
p('Encapsulation: patient allergies/history are held privately and returned through copies; medicine quantities change through validated operations. Inheritance: Patient extends Person; Student and Employee specialize Patient. User is a separate hierarchy, not a Person subclass. Polymorphism: role subclasses override canAccess; MedicalRecord and MedicineTransaction implement Formattable, whose toReport contract is used by the formatter. Abstraction: ClinicSystem specifies clinic operations, while secured services and repositories provide implementations. Composition: ApplicationContext supplies authentication, administration, storage, and session-bound clinic services to screens.');
sub('Role Permissions');
table(['Operation','Nurse','Assistant','Doctor','Admin'],[
['Register/view patient; add allergy','Yes','Yes','No','No'],['Record consultation','Yes','Yes','No','No'],['Read full visit history','Yes','Own visits via UI','No','No'],['Dispense / view stock','Yes','Yes','No','No'],['Add / restock medicine','Yes','No','No','No'],['Generate visit report','Yes','No','Yes','No'],['Users / campuses / audit','No','No','No','Yes'],['AI panel in navigation','Yes','No','No','No']],[167,71,71,71,71.28]);
p('The assistant own-visit list is an application query restriction; broader getVisitHistory is denied to that role. Doctor access is report-oriented. The AI panel checks SchoolNurse on construction; its standalone Gemini service does not enforce a session or role. This is a known security boundary gap, not a backend authorization guarantee.');

section('3. Technology Used');
table(['Category','Verified technology'],[
['Language/runtime','Java source compiled with JDK 21.0.7 during current checks; Java 21 is the documented baseline.'],['GUI','Java Swing/AWT, SwingWorker, CardLayout, layout managers, reusable table/dialog components.'],['Database','H2 2.5.252 embedded JDBC; encrypted file storage with AES; schema migration to version 2.'],['Build','javac and java through CMD launchers plus inline PowerShell. No Maven or Gradle build is present.'],['Development tools','VS Code Java configuration is present; Git workspace is available. IDE use is not required to run.'],['Testing','Standalone Java assertion/check executables in tests/ and demo/. No JUnit dependency is used.'],['Cloud client','Built-in java.net.http.HttpClient, HTTPS, Gemini generateContent REST call. No provider SDK.'],['Runtime dependency','Pinned H2 JAR downloaded and SHA-256 checked by tools/dependencies.cmd.'],['Documentation generation','This PDF is generated using PDFKit; the sample is read with pdf-parse. These are documentation tools only and are not app runtime dependencies.']],[120,W-120]);
sub('Operating Environment');
p('The provided launchers target Windows CMD and PowerShell. Desktop UI checks were run in the available Windows environment. The application sets a minimum window size of 1100 x 700 and initially opens at 1280 x 800. Cross-platform packaging, alternate DPI, screen readers, and multiple-monitor behavior have not been fully validated. No benchmark establishes minimum RAM or processor requirements. A JDK, a writable project/data location, and the H2 dependency are required. Internet is needed for the first driver download and optional AI calls; local clinic operations use embedded storage.');

section('4. User Manual');
sub('4.1 Installation & Startup');
p('Place the complete project folder in a writable location. Install JDK 21 and verify that java and javac are on PATH. Open PowerShell in the SchoolClinicSystem folder. Run the normal launcher below; it verifies/downloads H2, compiles src into out, and opens the login window.');
code('java -version\njavac -version\n.\\Run.cmd');
p('There is no required splash screen or public role-selection registration page. The entry point goes to shared authentication. A dedicated role launcher restricts the accepted role; it does not create accounts or bypass login.');
sub('4.2 First-Time Setup and Login');
p('On an empty production database, use first-run administrator setup to enter an administrator ID, name, and password. Sign in with those credentials, open Users, and add the staff accounts with their appropriate roles. Sign out, then sign in as the nurse or other authorized staff member. Production does not install the sample credentials. Three unsuccessful attempts temporarily lock the account for 60 seconds. Inactive accounts cannot sign in.');
p('For isolated demonstration only, run .\\NurseDemo.cmd and use the sample Nurse ID N001 with password nurse123. This launcher recompiles and uses --demo. Demo data disappears at exit. Do not interpret a demo save as a production database write.');
screenshot('login','Figure 1. Shared login screen from the automated fixture; sample data only.');
sub('4.3 Nurse Dashboard and Navigation');
p('After nurse login, the sidebar opens Nurse dashboard. Summary cards show registered patients, today\'s consultations, low-stock medicines, and stock expiring within 30 days. Recent Clinic Visits provides a name/ID search, Clear control, This week / Today / All scope, pagination, and New Consultation. This week begins Monday at midnight and ends at query time. Refresh requests actual service data. Sidebar selection identifies the active page. Revisited cached data pages reload their tables.');
screenshot('nurse','Figure 2. Nurse dashboard rendered by UI checks using an isolated fixture.');
sub('4.4 Register, Search, and Review a Patient');
p('Open Patients. Search by name, ID, section, or position. Select Register patient and supply ID, name, contact, Student/Employee type, course/department, section/position, and a positive year level for students. Save submits through the clinic service; duplicate IDs and invalid values are rejected. After success, the table reloads. Select a row and use Details for profile information or Add allergy to record a nonblank allergy. There is no general Edit patient or Delete patient action.');
sub('4.5 Record a Consultation');
p('Register the patient first. From the dashboard or Consultations page, open New Consultation / Record visit. Enter the existing Patient ID, choose a symptom, enter the reason, and optionally enter remarks. Save validates the patient and records the signed-in staff ID, then refreshes the relevant list. Blank symptom/reason or unknown patient prevents saving. The form offers one symptom choice even though the service model accepts a list. Consultations can be retrieved; editing a saved visit is not a supported UI action.');
screenshot('nurse-consultations','Figure 3. Consultation listing from UI verification.');
sub('4.6 Add and Restock Medicines');
p('Open Inventory. Add medicine records a named batch with positive quantity, expiry in YYYY-MM-DD format, and optional remarks. Restock uses the selected medicine plus quantity and expiry. A past expiry or invalid quantity is rejected. Stock and forecast displays unexpired available stock and estimated days remaining; -1 means there is no usage history. Low-stock totals combine available batches for each medicine.');
sub('4.7 Dispense Medicine');
p('Open Dispense medicine. Enter an existing Patient ID, medicine name, and positive whole quantity, then choose Check safety and dispense. Review any allergy warning. Cancel leaves stock unchanged. An override through the UI requires a meaningful reason of at least ten characters. A successful operation returns a transaction ID and reduces stock across available batches, earliest expiry first. Insufficient stock, invalid identity, or a database failure must not be reported as success. Retain the transaction ID if creating a guardian notification.');
sub('4.8 Guardian Notifications');
p('Open Notifications and choose Create notification. Provide Patient ID, that patient\'s dispensing Transaction ID, and reason. Use View message to inspect the generated text. Deliver it outside the application. Choose Mark sent only after delivery and confirm the prompt. The status changes from PENDING to SENT; repeating the same sent update is rejected. The application does not actually send email or SMS.');
sub('4.9 Reports, Accounts, and Logout');
p('In Reports, select a consultation and choose Generate selected report to view its formatted text. A doctor can access reports through the restricted clinic workspace. In nurse Settings, review signed-in ID, role, and storage location. Settings is informational; password change is not implemented. Use Sign out to clear the session and return to authentication. Closing the application requires confirmation; cancel retains the window.');
sub('4.10 Superadmin Workflow');
p('Sign in with an administrator account. Users provides account creation, role/name/active-status editing, and deactivation. The current administrator cannot modify their own account through this workflow. Campuses adds unique IDs and edits names. Audit log searches events and denied operations. Admin screens do not expose nurse dispensing. Campus entries do not assign users or patients to campuses.');
sub('4.11 AI Nurse Assistant');
p('The AI panel is available in nurse navigation. Set GEMINI_API_KEY privately in the launching process environment and optionally GEMINI_MODEL before starting the app. Never place a real key in source or submission files. The current source default is gemini-3.1-flash-lite; model availability depends on the configured provider project. NurseDemo.cmd currently removes GEMINI_MODEL for its Java child process, so that launcher uses the source default.');
p('Enter a general non-identifying question, confirm the privacy checkbox, and Send. Enter sends; the UI advertises Shift+Enter for a new line, whose exact key binding is not independently verified here. Send is disabled while a background request runs. Clear conversation removes local displayed messages when idle. Errors appear below the privacy control. The UI retains a visible conversation, but each request sends only the current question, not previous messages.');
p('Do not enter student identifiers, contact details, credentials, or sensitive records. Basic patterns block obvious emails, phone numbers, and labeled IDs, but cannot guarantee de-identification. No patient records are automatically loaded into the prompt. The system instruction requests cautious support and draft labeling; it is not a clinical validation mechanism. Live successful operation remains unverified and prior failures are acknowledged.');

section('5. Technical Manual');
sub('5.1 Development Environment Setup');
p('Open the existing folder in VS Code with Java tooling, or use the Windows command line. Confirm java and javac resolve to a compatible JDK. No Maven import or SQLite configuration is needed. The launchers compile source recursively with UTF-8. Normal startup uses Main -> GUI; NurseGUI and SuperadminGUI call the shared launch method with required role constraints.');
code('.\\Run.cmd\n.\\NurseDemo.cmd\ncmd /c tools\\verify.cmd\ncmd /c tools\\verify-desktop.cmd');
p('If compiling manually, include lib/* at runtime for production database startup. The manual command printed in older README text omits this runtime dependency; the corrected form is below. A direct java invocation does not rebuild out. verify.cmd builds out-check, so it does not refresh out.');
code('$sources = @(Get-ChildItem src -Recurse -Filter *.java |\n  ForEach-Object FullName)\njavac -encoding UTF-8 -d out @sources\njava -cp "out;lib/*" clinic.app.Main');
sub('Architecture and Component Interaction');
p('ApplicationContext.production opens Database at the clinic.database system property or data/scms. AccountRepository supplies password hashes, roles, active flags, and lockout state. AdminRepository supplies campuses/audit storage. ClinicRepository supplies a ClinicData proxy using database transactions. A clinic call is wrapped by SecuredClinicSystem and a session-version proxy. The latter checks identity/version under the login lock on every retained-service call. Logout and failed reauthentication revoke earlier clinic references.');
p('Frequently used count, patient lookup, and stock queries have direct SQL paths. Many other ClinicRepository calls load an aggregate, reuse InMemoryClinicSystem rules, and persist changed rows inside a transaction. This is durable persistence, but not fully SQL-paged retrieval: filtering/aggregation can materialize records. PageResult and PagedTablePanel bound displayed rows to 25. SwingWorker handles loading; generation checks reject stale results.');
sub('5.2 Database Schema');
p('Schema.java creates the relational tables below and applies the version-2 account lockout migration. IDs are generally VARCHAR(500); timestamps use TIMESTAMP(9). The complete creation script is reproduced in Appendix C from source, not replaced with the template\'s student/course/grades example.');
table(['Table','Fields and relationships'],[
['schema_version','id PK (must equal 1), version; controls additive schema migration.'],['accounts','id PK, name, role, password_hash, active; v2 adds failed_attempts and locked_until. Roles: ADMIN/NURSE/ASSISTANT/DOCTOR.'],['campuses','id PK, name. Independent catalog, no patient/account FK.'],['audit_entries','seq identity PK; event_time, user_id, user_name, role, action, details, allowed. No account FK.'],['patients','id PK; name, contact, patient_type, course_department, section_position, year_level. Student year > 0; employee year is NULL.'],['allergies','patient_id FK -> patients; allergy. Composite PK (patient_id, allergy).'],['patient_history','patient_id FK -> patients; ordinal, detail. Composite PK (patient_id, ordinal); no general history-edit screen.'],['visits','id PK; seq identity unique; patient_id FK, staff_id FK -> accounts; reason, assessment, medication, visit_time.'],['symptoms','visit_id FK -> visits; ordinal, symptom. Composite PK (visit_id, ordinal).'],['medicine_batches','ordinal PK; name, quantity >= 0, expiry, batch_date, remarks. Names need not be unique; multiple batches are permitted.'],['dispensing','id PK; seq identity unique; patient_id/staff_id FKs; medicine, quantity > 0, expiry, remarks, dispensed_at; unique(id, patient_id).'],['notifications','id PK; seq identity unique; patient_id FK, transaction_id, reason, created_at, status; composite FK to dispensing(id, patient_id).']],[112,W-112]);
p('The notification composite foreign key enforces ownership of the transaction by that patient. Medicine names in dispensing are text, not a foreign key to one batch, because one dispensing can consume multiple batches. The model stores the earliest consumed expiry in its transaction summary. Indexes exist on visits(patient_id, visit_time) and audit_entries(event_time).');
sub('Security, Validation, and Error Handling');
p('Passwords use PBKDF2WithHmacSHA256 with a random 16-byte salt, 210,000 iterations, a 256-bit result, and constant-time comparison. Account activation and three-attempt/60-second lockout checks occur in Login. Login clears earlier authentication before trying new credentials. Administration requires an active administrator session. Clinic permissions are checked by the service rather than only by navigation. Staff IDs on visits/dispensing must match the signed-in identity.');
p('Database uses an application lock for one process, local AES-encrypted H2 files, a random local key file, and owner-oriented filesystem permissions where supported. Prepared statements bind variable query values. Transaction failure rolls back before reporting a storage error. Audit details avoid passwords, patient names, allergy text, and free-text override reasons; the override reason remains in the clinical transaction. Audit permission entries indicate that access was allowed, not necessarily that the later domain operation completed successfully.');
p('FormPopup validates required values and saves asynchronously. Domain models check blanks and positive quantities; repositories add foreign-key/check constraints. Detached read objects prevent mutation of stored state through a read reference. The boolean legacy dispensing API still permits a confirmed override without the nurse form\'s ten-character text requirement; UI documentation must not imply that every legacy caller enforces that text rule.');
sub('AI Integration Boundaries');
p('GeminiAiService uses HttpClient with a 10-second connection timeout and 35-second request timeout; HTTPS requests carry x-goog-api-key in a header. Input is limited to 3,500 characters and output requested at 1,024 tokens. There are no automatic retries. The model name is validated, and HTTP authentication, quota, timeout, 404, and network failures receive user messages. Error detail redacts the configured key. No key is reproduced in this document.');
p('The JSON implementation is a custom string encoder/parser, not a full JSON library: it extracts the first text field and does not comprehensively validate candidate structure, finish reason, multipart content, or safety metadata. The panel does not recheck session identity for every AI request and has no complete cancellation/late-result lifecycle handling. These limitations require attention before production use. A shared privileged provider key should be kept on an authenticated server rather than distributed with a desktop app.');
sub('5.3 Project Setup, Maintenance, and Recovery');
p('Run tools/dependencies.cmd to obtain the pinned H2 JAR when missing. Its checksum is verified on every launcher run. Production creates the local schema and encryption key when first opening the database. Do not run sample schema resets against a real record store. To use a different database base path, supply -Dclinic.database before the Java main class.');
code('java -Dclinic.database=data/scms -cp "out;lib/*" clinic.app.Main');
p('For backup, close the application and copy the complete data folder, including the matching .mv.db and .key files, to a protected location. Keep these together; a missing key prevents opening an existing encrypted database. No automatic backup or tested recovery tool is included. A second process opening the same store is rejected. To update code, close the window and run the compilation launcher. Keep demo/test databases separate from real clinic data.');

section('6. Project Development Phases');
p('The sample asks for a weekly progress snapshot. The supplied materials do not establish an actual week-by-week schedule or individual team contributions. The evidence-supported implementation phases below describe the present artifacts, not invented dates or a retrospective time sheet.');
table(['Phase','Current evidence and deliverable'],[
['Requirements and modeling','ClinicSystem contract, Person/Patient and User hierarchies, permission constants, model classes, and source comments. Original stakeholder acceptance dates unavailable.'],['Domain implementation','Patient registration, immutable visits, inventory/batch operations, allergy checks, transaction reporting, notifications, and analytics services.'],['Desktop interface','Shared Theme and reusable forms/tables, role-specific shells, dashboard, nurse modules, and administration panels.'],['Persistence and security','H2 repositories/schema, encrypted storage, lockout persistence, session proxy, prepared queries, and rollback checks.'],['Optional AI integration','Gemini cloud client and nurse chat panel. Source present; live generation success unresolved in available evidence.'],['Verification and documentation','Current automated domain, UI, persistence, native workflow, and visit-search checks executed; final PDF produced on October 25, 2026.']],[120,W-120]);
p('No statement is made that the project followed the sample\'s 13-week schedule. Project management sign-off, instructor acceptance, and actual deployment history remain undocumented.');

section('7. Limitations and Future Development');
sub('7.1 Current Limitations');
bullet('One application process owns the local database. The system is not a shared multi-workstation clinic server.');
bullet('No appointments, patient deletion/general profile editing, visit editing, password reset/change, or patient/guardian portal is exposed. Existing user and campus editing must not be confused with patient CRUD.');
bullet('Guardian messages require external manual delivery. Reports display text; export and automatic distribution are absent.');
bullet('Campus catalog entries have no enforced membership relationship with patients or accounts.');
bullet('SafetyChecker contains limited allergy matching, not a comprehensive drug interaction database. Forecast and outbreak rules are application heuristics, not diagnostic conclusions.');
bullet('Many persistent queries load aggregates into memory. The UI is paged but the persistence layer is not fully optimized for high-volume server workloads.');
bullet('AI live generation has not been established as working. Custom JSON handling, session authorization, bounded chat retention, cancellation, error wrapping, and privacy screening remain incomplete. Pattern screening cannot detect all sensitive information.');
bullet('Automatic backup, restore validation, retention policy, audit tamper resistance, institutional privacy compliance, and deployment hardening are not established.');
sub('7.2 Future Development Plan');
p('The following are recommendations arising from the audit, not implemented or approved commitments. Prioritize reliable AI model discovery/configuration, a real JSON parser, per-request nurse-session checks, cancellable requests, and bounded chat history. Strengthen usable error wrapping and keyboard/accessibility verification. Add an authenticated server-side AI proxy before distributing shared credentials.');
p('For storage and operations, introduce a tested backup/restore workflow, SQL-based filtering/pagination for larger datasets, and operational recovery guidance. New appointment, patient-edit, export, campus membership, or messaging capabilities require defined requirements, access rules, and migrations before implementation. Any expansion of medicine safety rules requires authoritative clinical input.');
sub('Technical Debt and Refactoring');
p('Retained legacy NurseDashboard and ModulePage classes are not the routed operational panels. Documentation and comments occasionally lag source changes: for example, the current Gemini default differs from older README examples. Keep tests, launchers, and documentation aligned. Preserve the public clinic contract when replacing aggregate loading with more targeted repositories.');

section('Appendix');
sub('A. UML Class Diagram');
p('The diagram below reflects actual inheritance and interfaces. User does not inherit Person. Dashed conceptual call relationships are explained in text rather than used to imply inheritance.');
let yy=doc.y+10;
box(227,yy,140,50,'Person','id, name, contactInfo');box(227,yy+85,140,60,'Patient','allergies, medicalHistory');arrow(297,yy+85,297,yy+50);
box(92,yy+185,150,60,'Student','course, yearLevel, section');box(352,yy+185,150,60,'Employee','department, position');arrow(167,yy+185,272,yy+145);arrow(427,yy+185,322,yy+145);
doc.x=72;doc.y=yy+265;p('Inheritance arrows point toward the superclass. Patient.copy returns detached subtype-preserving profiles.');
page();h('A. UML Class Diagram (continued)');yy=doc.y+10;
box(222,yy,150,60,'User','userId, name, role\ncanAccess(feature)');
['Administrator','SchoolNurse','NurseAssistant','Doctor'].forEach((n,i)=>{let x=74+i*115;box(x,yy+115,105,60,n,'canAccess override');arrow(x+52,yy+115,297,yy+60);});
box(222,yy+225,150,45,'<<interface>>','Formattable: toReport()');box(92,yy+320,165,55,'MedicalRecord','immutable encounter fields');box(337,yy+320,165,55,'MedicineTransaction','quantity, expiry, remarks');arrow(175,yy+320,272,yy+270);arrow(420,yy+320,322,yy+270);
doc.x=72;doc.y=yy+400;p('ClinicSystem is implemented by InMemoryClinicSystem and SecuredClinicSystem. The secured decorator delegates after checking role permissions; ApplicationContext supplies the session-bound proxy. Database, repositories, and Swing panels are composition dependencies, not subclasses of these model types.');

sequence('B.1 Sequence Diagram - Login and Routing',['Staff','Login UI','Login','Repository','Router'],[[0,1,'Enter ID/password'],[1,2,'login(id, password)'],[2,3,'Persist attempts/lock state'],[3,2,'Commit or storage error'],[2,1,'User or empty result'],[1,4,'Select allowed workspace'],[4,0,'Display role shell / error']]);
p('Login verifies password/active/lock state from its account cache loaded from AccountRepository. Password verification occurs locally; the repository step records attempts. A failed attempt clears any earlier session.');
sequence('B.2 Sequence Diagram - Record Consultation',['Nurse','Form','Secured API','Repository','H2'],[[0,1,'Submit existing patient ID and visit fields'],[1,2,'findPatient; recordVisit'],[2,3,'Permission and staff identity verified'],[3,4,'Begin unit of work; load domain'],[4,3,'Records for validation'],[3,4,'Insert visit + symptoms; commit'],[3,1,'Saved record or failure'],[1,0,'Refresh table; show success after save']]);
p('The service rejects unknown patient IDs and invalid symptoms. Foreign keys reject missing staff; a failed write rolls back rather than partially storing the encounter.');
sequence('B.3 Sequence Diagram - Dispense Medicine',['Nurse','Dispense UI','Secured API','Domain','H2'],[[0,1,'Patient, medicine, positive quantity'],[1,2,'checkSafety'],[2,3,'Evaluate allergies'],[3,1,'Warnings or no conflict'],[1,0,'Review / cancel / reason'],[1,2,'dispenseMedicine with reason'],[2,3,'Validate; consume earliest expiry'],[3,4,'Update stock + insert transaction'],[4,1,'Commit result / rollback error']]);
p('Safety inspection and dispensing are separate calls; dispensing revalidates the domain state in its own transaction. Cancel does not submit a stock mutation. The database transaction is supplied through ClinicRepository around the domain call.');

page();h('C. Database Relationship Diagram');yy=doc.y+20;
box(72,yy,140,65,'accounts','PK id\nrole; active; hash');box(330,yy,190,65,'patients','PK id\ntype; contact; class/work fields');
box(72,yy+140,160,70,'visits','PK id; FK staff_id\nFK patient_id');box(330,yy+140,190,70,'dispensing','PK id; FK staff_id\nFK patient_id; quantity');
box(72,yy+280,160,60,'symptoms','PK/FK visit_id + ordinal');box(330,yy+280,190,80,'notifications','PK id; FK patient_id\nFK(transaction_id, patient_id)\n-> dispensing(id, patient_id)');
arrow(142,yy+65,142,yy+140);arrow(425,yy+65,425,yy+140);arrow(330,yy+35,232,yy+170);arrow(212,yy+35,340,yy+150);arrow(152,yy+210,152,yy+280);arrow(425,yy+210,425,yy+280);
doc.x=72;doc.y=yy+385;p('Arrows point from parent records to dependent records (one-to-many). Patients also have one-to-many allergies and patient_history entries. Campuses, schema_version, audit_entries, and medicine_batches are independent tables in the current schema. There is no foreign key from dispensing.medicine to an individual medicine batch.');
sub('C.1 Exact SQL Creation Script');
p('The following DDL is extracted verbatim from Schema.java. The account migration statements following it create the current version-2 lockout fields. Do not execute against a production store as a reset script.');
const schema=fs.readFileSync(path.join(root,'src/clinic/persistence/Schema.java'),'utf8');
code('CREATE TABLE IF NOT EXISTS schema_version (\n  id INT PRIMARY KEY CHECK(id=1), version INT NOT NULL\n);');
code(schema.split('private static final String DDL = """')[1].split('""";')[0].split('\n').map(l=>l.replace(/^        /,'')).join('\n'));
code('ALTER TABLE accounts ADD COLUMN IF NOT EXISTS failed_attempts\n  INT NOT NULL DEFAULT 0 CHECK(failed_attempts>=0);\nALTER TABLE accounts ADD COLUMN IF NOT EXISTS locked_until\n  BIGINT NOT NULL DEFAULT 0;');
sub('D. Test Cases Summary and Evidence Map');
p('The comprehensive four-column Test Cases table appears at the very end, as requested. Current execution results: 56 domain/security checks, 36 UI checks, 49 persistence checks, 19 desktop workflow checks, and additional visit/search assertions. No failure was reported by these executed suites. A live successful AI response is not established.');
table(['Check source','Scope'],[['tests/DomainChecks.java','Login, permissions, stock integrity, allergy overrides, notifications, detached reads, session revocation.'],['tests/UiChecks.java','Paged loading, stale/error recovery, role navigation, AI-page opening, fixture screenshots.'],['tests/PersistenceChecks.java','Restart preservation, FK constraints, SQL-shaped literal search, rollback, key/account persistence, migration.'],['tests/DesktopChecks.java','19 native dialog/navigation workflows using Swing actions and isolated fixture data.'],['demo/DashboardConsultationChecks.java; demo/VisitSearchDemo.java','Recent-visit ordering, role restrictions, date/name/ID filtering, 10,004-visit data set.']],[175,W-175]);
sub('E. Source References');
p('Primary sources: src/clinic/app/{GUI,Router,ApplicationContext}.java; app/nav/{NurseShell,MainShell}.java; app/ui/{patients,consultations,dispense,inventory,notifications,reports,settings,assistant}; core/{Login,PasswordHasher,ClinicSystem}.java; model/{Person,Patient,Student,Employee,User,SchoolNurse,NurseAssistant,Doctor,Administrator,Formattable,MedicalRecord,MedicineTransaction}.java; service/{SecuredClinicSystem,InMemoryClinicSystem,Inventory,SafetyChecker,PatternDetector,RestockForecast}.java; persistence/{Schema,Database,ClinicRepository,AccountRepository,AdminRepository,Sql}.java; ai/GeminiAiService.java; tools/verify.cmd; tools/verify-desktop.cmd.');
p('Template reference: OOP Final Project Document Sample (1).docx.pdf supplied from Downloads. It is used as a structural and typographic guide. Sample names, features, diagrams, and example results are not evidence about SCMS. No exposed API credentials or real patient records have been included.');

section('Comments/Grading');
p('This section preserves the template\'s evaluator-facing purpose. Scores and signatures must be assigned by the instructor; no grading has been provided in the evidence. The sample does not specify category maximums, so no weights are invented.');
table(['Criteria/Section','Maximum Score','Actual Score'],[['Software Purpose','Not specified','Not assessed'],['Objective & Feature Accomplished','Not specified','Not assessed'],['Technology Stack','Not specified','Not assessed'],['User Manual','Not specified','Not assessed'],['Code Quality','Not specified','Not assessed'],['UML Diagram','Not specified','Not assessed'],['Database Structure & ERD','Not specified','Not assessed'],['Total','100 (sample total)','Not assessed']],[W*.54,W*.23,W*.23]);
p('Instructor Feedback: Not supplied; reserved for instructor evaluation.');p('Final Grade: Not assigned / 100.');p('Instructor Signature: Not supplied.');p('Evaluation Date: Not supplied.');

section('Test Cases');
p('Executed on October 25, 2026 using isolated fixtures or temporary databases. D = DomainChecks; U = UiChecks; P = PersistenceChecks; N = DesktopChecks; V = DashboardConsultationChecks / VisitSearchDemo. IDs and source labels are included inside Test Case so the table retains exactly the four required columns. “Not Tested” does not imply success.');
const rows=[];function tc(feature,testCase,expected,status='Pass'){rows.push([feature,testCase,expected,status]);}
tc('Setup','TC01 D/P/N: Bootstrap an empty store with an administrator.','Administrator can sign in; setup is not offered again after restart.');
tc('Authentication','TC02 D/N: Submit incorrect credentials.','Login fails; no privileged session remains.');
tc('Lockout','TC03 D: Make three failed attempts, then use the correct password before 60 seconds.','Correct password is temporarily rejected; succeeds after simulated expiry.');
tc('Persistent lockout','TC04 P: Restart after three failed nurse attempts.','Lockout remains; another account can still sign in.');
tc('Account activation','TC05 D/P: Deactivate a nurse/doctor and attempt login.','Inactive account is rejected, including after restart.');
tc('Account update','TC06 D: Change a role and reactivate while retaining the password.','New role takes effect; existing password still authenticates.');
tc('Account creation','TC07 N: Administrator adds a staff user through the form.','User is created and the table refreshes.');
tc('Unauthorized account action','TC08 D: Add user without administrator session.','SecurityException; no authorized account creation.');
tc('Campus catalog','TC09 D/P/N: Add then rename a campus.','Updated name displays and survives restart.');
tc('Campus duplicate','TC10 D/P: Add an existing campus ID as new.','Duplicate is rejected.');
tc('Nurse authorization','TC11 P/U: Nurse accesses users/audit or opens Superadmin shell.','Access denied; nurse navigation omits administration.');
tc('Role launcher','TC12 U: Administrator opens nurse-only route; nurse opens admin-only route.','Role mismatch rejected.');
tc('Logout','TC13 D/P: Retain a clinic reference, log out, then call it.','SecurityException; retained service is revoked.');
tc('Relogin','TC14 D: Reuse old reference after same-user login.','Old session version is still rejected.');
tc('Patients','TC15 N: Submit required registration fields.','Patient is registered and visible after refresh.');
tc('Required fields','TC16 N: Submit an incomplete patient form.','Registration is prevented.');
tc('Cancel form','TC17 N: Cancel registration.','Patient data remains unchanged.');
tc('Patient duplicate','TC18 D/P: Register an existing patient ID.','Duplicate rejected; no duplicate row.');
tc('Detached profiles','TC19 D: Mutate an allergy on a returned patient.','Stored profile is unchanged.');
tc('Allergy persistence','TC20 P: Add Penicillin allergy, close and reopen.','Stored allergy is present after restart.');
tc('Literal search','TC21 P: Search an apostrophe-containing name and SQL-shaped text.','Name matches literally; SQL-shaped text does not return all patients.');
tc('Consultation','TC22 D/N: Save a valid registered-patient consultation.','Visit is stored; form reports success and list refreshes.');
tc('Symptom validation','TC23 D: Record a visit with blank symptom.','Domain rejects it; history count unchanged.');
tc('Visit persistence','TC24 P: Restart after storing visit ID, timestamp, symptoms and remarks.','All checked fields retain their saved values.');
tc('Visit foreign key','TC25 P: Store a visit with nonexistent staff ID at repository boundary.','Database rejects it and no partial visit remains.');
tc('Staff identity','TC26 D: Dispense using another staff ID.','Service rejects impersonation.');
tc('Assistant history','TC27 P/V: Assistant queries visits recorded by nurse.','Other staff visits are excluded from assistant list.');
tc('Recent visits','TC28 V: Query recent visits and page sizes 25 and 15.','Newest first; each page respects requested limit.');
tc('Visit filters','TC29 V: Search name/ID with This week, Today, All and no-match query.','Filtering occurs before paging; scope results match fixtures.');
tc('Date boundaries','TC30 V: Include Monday midnight and future-dated fixtures.','Monday is included in week; future visits excluded.');
tc('Search privacy','TC31 V: Inspect audit after patient-name search.','Audit records search action without the query text.');
tc('Inventory','TC32 N: Restock selected medicine with valid quantity/expiry.','New batch appears and total stock increases.');
tc('Expiry validation','TC33 D: Add a batch expiring yesterday.','Expired new batch rejected.');
tc('Low stock','TC34 D: Aggregate multiple batches of same medicine.','One medicine summary with combined available quantity.');
tc('Expiry summary','TC35 D: Scan expiry alerts with emptied batch present.','Empty batches excluded.');
tc('Safety warning','TC36 D: Check Amoxicillin for Penicillin-allergic patient.','Warning is returned.');
tc('Safety enforcement','TC37 D/P: Dispense warning medicine without confirmation.','Rejected; stored stock remains unchanged.');
tc('Cancel warning','TC38 N: Cancel safety warning dialog.','No stock deduction.');
tc('Override text','TC39 D/N: Submit short override reason.','Reason is rejected before stock change.');
tc('Documented override','TC40 D/P/N: Supply a sufficient override reason.','Dispensing succeeds; reason retained in transaction, excluded from general audit.');
tc('Dispensing','TC41 D/P/N: Dispense six units across two batches.','Stock reduced correctly; earliest expiry selected first.');
tc('Insufficient stock','TC42 D: Request more than available quantity.','Rejected atomically; stock unchanged.');
tc('Read isolation','TC43 D: Mutate returned medicine quantity.','Stored stock is unchanged.');
tc('Assistant restriction','TC44 D/P: Assistant restocks or generates report.','Service denies both restricted operations.');
tc('Doctor/admin restriction','TC45 D: Doctor or administrator dispenses medicine.','Service denies dispensing.');
tc('Cached inventory','TC46 N: Dispense, then return to cached inventory page.','Table reloads with updated quantity.');
tc('Rollback','TC47 P: Inject test constraint failure after stock update.','Stock and dispensing unit of work rolls back; connection remains usable.');
tc('Closed storage','TC48 P: Register against closed database.','Storage failure is raised; no success result.');
tc('Process lock','TC49 P: Open same database in a second context.','Second opening rejected.');
tc('Notification create','TC50 D/N: Create from existing patient/dispensing transaction.','Message identifies fixture patient and medicine; status PENDING.');
tc('Notification ownership','TC51 P: Link a transaction to another patient.','Rejected.');
tc('Missing transaction','TC52 D: Create notification for unknown transaction.','Rejected.');
tc('Notification status','TC53 D/N: Confirm delivery then mark sent; repeat.','First update records SENT; repeat update rejected.');
tc('Notification snapshots','TC54 D/P: Mutate status on a returned notification.','Stored status is unchanged until authorized service update.');
tc('Notification restart','TC55 P: Restart after marking sent.','ID and SENT status persist.');
tc('Reports','TC56 D/N: Generate selected saved-visit report.','Report contains stored record/concern and dialog opens/closes.');
tc('Doctor reports','TC57 D: Doctor generates visit report.','Authorized report text includes record ID.');
tc('Paging scale','TC58 U/V: Load 10,000+ fixture records and last page.','At most 25 displayed rows; last/filter pages have expected values.');
tc('Background loading','TC59 U: Record thread used by page source.','Query runs off Swing EDT.');
tc('Stale results','TC60 U: Let an old request complete after a newer query.','Old result cannot replace current rows.');
tc('Load failure/retry','TC61 U: Force source exception then restore source.','Rows/count/selection clear; subsequent reload succeeds.');
tc('Role navigation','TC62 U: Open nurse, assistant, doctor, admin shells.','Allowed screens appear; disallowed role shells fail.');
tc('AI navigation','TC63 U: Nurse opens AI Nurse Assistant.','Assistant page constructs and renders.');
tc('UI resize','TC64 N: Refresh dashboard and resize test window.','Workflow completes and window stays usable in tested dimensions.');
tc('Shutdown','TC65 N: Cancel close, then confirm close.','Cancel retains window; confirmation closes application.');
tc('Restart data','TC66 P: Reopen saved database.','Accounts, campuses, audit, patients, visits and stock checked by suite survive.');
tc('Migration','TC67 P: Re-run interrupted version-1-to-2 migration fixture.','Existing accounts and patients preserved.');
tc('Password storage','TC68 D/P: Inspect audit/repository hash values in test fixture.','Password strings absent from audit and stored hashes.');
tc('AI live generation','TC69: Send non-identifying prompt with valid key/model.','A genuine provider response is displayed; no success established here.','Not Tested');
tc('AI missing key','TC70: Clear key in a fresh Java process and Send.','Useful configuration error without provider request. Source inspected; not executed in current run.','Not Tested');
tc('AI HTTP failures','TC71: Reproduce 404/429/timeout with current build.','Specific user error; UI re-enables Send. Earlier screenshots showed errors; current scenarios not executed.','Not Tested');
tc('AI privacy patterns','TC72: Submit email, phone, labeled student ID.','Reject before cloud send; source rule not exercised here.','Not Tested');
tc('AI input boundaries','TC73: Send empty, over-3,500-character and duplicate-pending messages.','Validation prevents invalid or duplicate request; not executed here.','Not Tested');
tc('AI long output','TC74: Receive long/multipart response and resize.','Readable wrapped text with correct response extraction; multipart parser limitation unresolved.','Not Tested');
tc('AI lifecycle','TC75: Start request then log out/navigate away.','Session revocation and late-result handling need additional implementation/review.','Not Tested');
tc('Manual accessibility','TC76: Keyboard-only, screen reader, non-default DPI and multiple monitors.','Controls remain accessible and unclipped; no current manual evidence.','Not Tested');
tc('Recovery','TC77: Restore closed database and key backup into isolated folder.','Records reopen correctly; complete recovery workflow not exercised.','Not Tested');
table(['Feature','Test Case','Expected Outcome','Pass/Fail'],rows,[70,165,160,56.28]);

const range=doc.bufferedPageRange();
for(let i=0;i<range.count;i++){doc.switchToPage(i);const saved=doc.y;const bottom=doc.page.margins.bottom;doc.page.margins.bottom=0;doc.fillColor('#68717c').font('Times-Roman').fontSize(9).text(String(i+1),72,795,{width:W,align:'center',lineBreak:false});doc.page.margins.bottom=bottom;doc.y=saved;}
doc.end();
fs.writeFileSync(path.join(__dirname,'SCMS_Documentation_Validation.json'),JSON.stringify({date:'2026-10-25',pdf:dest,pages:range.count,sections:toc,testCases:rows.length,statuses:rows.reduce((a,r)=>(a[r[3]]=(a[r[3]]||0)+1,a),{}),execution:{domain:56,ui:36,persistence:49,native:19,visitSearch:'passed'},liveAiVerified:false},null,2));
console.log(JSON.stringify({pdf:dest,pages:range.count,testCases:rows.length}));
