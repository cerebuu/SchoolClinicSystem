const fs = require('fs');
const path = require('path');
const PDFDocument = require('../.documentation-tools/node_modules/pdfkit');

const root = path.resolve(__dirname, '..');
const slides = [
  {
    title: 'UIC School Clinic Management System',
    subtitle: 'Verified presentation source | October 26, 2026',
    bullets: [
      'A Java desktop application for school-clinic operations.',
      'Staff workflows for patient records, consultations, inventory, dispensing, notifications, and reports.',
      'Prepared from the current project source and implementation; use synthetic or approved demo data during presentation.'
    ],
    sources: 'README.md; src/clinic/app/GUI.java; src/clinic/app/Router.java'
  },
  {
    title: 'Purpose and Project Scope',
    bullets: [
      'The project brings core clinic record and medicine workflows into one role-aware desktop application.',
      'Scope includes staff sign-in, student and employee registration, visit documentation, stock tracking, dispensing, alerts, notifications, and administration.',
      'Patients and guardians do not have self-service application accounts.',
      'No measured paper-process baseline or before-and-after outcome data was supplied; no time-saving or error-reduction claim is made.'
    ],
    sources: 'README.md; src/clinic/app/Router.java; src/clinic/app/nav/*'
  },
  {
    title: 'Implemented System Objectives',
    bullets: [
      'Authenticate staff and apply role permissions to clinic and administrative functions.',
      'Maintain student and employee profiles, individually stored allergies, and a separate optional guardian-contact field.',
      'Record consultations with controlled multi-symptom selection, a required concern, and optional remarks.',
      'Track medicine batches, expiry, stock movements, refill history, and dispensing transactions.',
      'Support operational review through reports, stock/visit alerts, audit entries, and an optional nurse-only AI assistant.'
    ],
    sources: 'src/clinic/core/ClinicSystem.java; src/clinic/service/ClinicData.java; src/clinic/app/ui/*'
  },
  {
    title: 'Users and Access Roles',
    bullets: [
      'Nurse: patient and consultation workflows, dispensing, stock view and restock, reports, dashboard, and AI assistant.',
      'Nurse assistant: patient and consultation workflows, dispensing, and stock view; restocking and reports are not granted.',
      'Doctor: report-viewing workspace; no patient-registration or dispensing permission.',
      'Administrator (shown as Superadmin in navigation): user and campus management plus audit-log access; no clinical workflow access.',
      'Navigation visibility is paired with service-level authorization; role checks are not only visual.'
    ],
    sources: 'src/clinic/model/{User,SchoolNurse,NurseAssistant,Doctor,Administrator}.java; src/clinic/app/nav/*; src/clinic/service/SecuredClinicSystem.java'
  },
  {
    title: 'Main Functional Modules',
    bullets: [
      'Patients: register Student or Employee records, search profiles, view details, and add allergies.',
      'Consultations: record registered-patient visits with one or more controlled symptom labels, concern, remarks, time, and staff ID.',
      'Dispensing and inventory: manage medicine batches, check safety and usable stock, record transactions, and view history.',
      'Notifications and reports: prepare transaction-linked clinic messages for manual delivery; view and format stored visit reports.',
      'Administration and dashboard: manage staff accounts/campuses, review audit events, and see clinic summaries.'
    ],
    sources: 'src/clinic/app/ui/patients/PatientsPanel.java; consultations/; dispense/; inventory/; notifications/; reports/; ui/admin/'
  },
  {
    title: 'Typical Clinic Workflow',
    bullets: [
      '1. An administrator completes first-run setup and creates staff accounts; staff sign in with their assigned role.',
      '2. Staff register a Student or Employee using the patient ID and profile fields; allergies are entered as separate items.',
      '3. Staff document a consultation by choosing symptoms from the controlled list and entering the concern.',
      '4. For dispensing, staff select a patient and medicine items; the application checks allergy records, medicine availability, expiry, and requested quantity.',
      '5. Staff review visit and transaction history, prepare a guardian/employee notification if needed, then mark it sent only after external delivery.'
    ],
    sources: 'src/clinic/app/ui/auth/LoginPanel.java; patients/; consultations/; dispense/; notifications/; src/clinic/service/DispenseWorkflow.java'
  },
  {
    title: 'Inventory and Dispensing Controls',
    bullets: [
      'Medicine is stored as batches with stable UUID identity, quantity, expiry date, batch date, and remarks.',
      'Matching unexpired batches can be merged or kept separate after confirmation; staff can also review existing duplicate groups.',
      'A zero-stock batch can be refilled in place after confirmation; the batch keeps its ID and receives the new expiry, remarks, quantity, and refill date.',
      'Dispensing preflights requested items, consumes usable stock by earliest expiry, and reports completed versus unattempted items if a later row fails.',
      'Allergy confirmation does not bypass expired-stock or out-of-stock checks.'
    ],
    sources: 'src/clinic/model/Medicine.java; src/clinic/service/Inventory.java; DispenseWorkflow.java; src/clinic/app/ui/inventory/InventoryPanel.java'
  },
  {
    title: 'Alerts, Forecasts, and Reports',
    bullets: [
      'Low-stock alert: at most 10 usable units in aggregate across a medicine name.',
      'Expiry alert: usable batches expiring within 30 days; expired batches with stock are separately surfaced.',
      'Visit-pattern rules: 3 same-symptom visits in a student section or employee department within 48 hours; recurring single-patient patterns use 3 visits in 14 days.',
      'Medicine-days estimate uses recorded dispensing over a 30-day window; with no recorded use there is no finite rate-based estimate.',
      'Reports list visit records and can format a selected record; a full export-to-PDF/Excel workflow is not implemented.'
    ],
    sources: 'src/clinic/service/FeatureRules.java; PatternDetector.java; RestockForecast.java; AlertCollector.java; src/clinic/app/ui/reports/ReportsPanel.java'
  },
  {
    title: 'Core Data Managed by the System',
    bullets: [
      'Patient: ID, name, own contact, Student/Employee type, course or department, section or position, and student year level.',
      'Guardian contact is stored separately from the patient contact; allergies are saved as individual values.',
      'Consultation: record ID, patient ID, symptom list, concern, remarks/assessment, staff ID, and visit timestamp.',
      'Medicine transaction: transaction ID, medicine, patient and staff IDs, quantity, expiry, remarks, and timestamp.',
      'Notifications reference a patient and dispensing transaction; an employee notice uses the employee contact, while a student notice requires guardian contact.'
    ],
    sources: 'src/clinic/model/{Patient,Student,Employee,MedicalRecord,MedicineTransaction,NotificationLog}.java; src/clinic/service/NotificationStore.java'
  },
  {
    title: 'Application Architecture',
    bullets: [
      'Presentation: Java Swing panels, shared forms/dialogs, paged tables, role-aware navigation, and background UI work.',
      'Composition and session: ApplicationContext assembles login, audit, admin services, clinic data, and change notifications.',
      'Domain/service: ClinicSystem is the clinic operation contract; secured services enforce permissions and domain services implement validation and workflow rules.',
      'Data access: ClinicData adds internal query/batch operations; the in-memory implementation supports fixtures, while ClinicRepository adapts operations to JDBC persistence.',
      'Data-change topics refresh relevant tables and alerts after successful operations.'
    ],
    sources: 'src/clinic/app/{ApplicationContext,Router,DataChangeBus,ClinicChangePublisher}.java; src/clinic/core/ClinicSystem.java; src/clinic/service/; src/clinic/persistence/'
  },
  {
    title: 'Technology, Persistence, and Security',
    bullets: [
      'Java 21 with plain Java Swing/AWT; the project uses javac and Windows CMD/PowerShell launchers, not Maven or Gradle.',
      'Embedded H2 2.5.252 JDBC database; normal startup stores records locally under data/scms unless clinic.database overrides the path.',
      'Database file encryption uses H2 AES mode with a separately stored local key file; keep the database and matching key together.',
      'Passwords use salted PBKDF2WithHmacSHA256; sign-in tracks failed attempts and temporary lockout. Role checks and audit logging are implemented in services.',
      'Demo and automated fixtures can use in-memory or isolated temporary data; they are distinct from normal persistent startup.'
    ],
    sources: 'README.md; src/clinic/core/{Login,PasswordHasher}.java; src/clinic/persistence/{Database,Schema}.java; tools/dependencies.cmd'
  },
  {
    title: 'Optional AI Nurse Assistant',
    bullets: [
      'The nurse-only panel can send the current typed question to Google Gemini over HTTPS when GEMINI_API_KEY is configured; GEMINI_MODEL can select a provider-supported model.',
      'The assistant receives no SCMS database context, cannot execute clinic actions, and is instructed not to diagnose or prescribe.',
      'The UI requires a non-identifying-data confirmation; the client also screens for some obvious email, phone, and labeled-ID patterns.',
      'Pattern screening is not reliable de-identification. Do not submit patient names, IDs, contact details, records, or credentials.',
      'Live provider success and institutional approval for sending data to an external service are not verified by this project documentation.'
    ],
    sources: 'README.md; src/clinic/ai/GeminiAiService.java; src/clinic/app/ui/assistant/AiNurseAssistantPanel.java'
  },
  {
    title: 'Suggested Live Demonstration',
    bullets: [
      'Use a demo or approved synthetic dataset; sign in and show how the workspace changes by role.',
      'Register a synthetic Student with separately entered allergies, then record a consultation with multiple selected symptoms.',
      'Show inventory quantity/expiry, duplicate-batch choice, and refill of a zero-stock batch.',
      'Run a multi-item dispense: review the allergy confirmation and stock/expiry behavior, then inspect transaction history.',
      'Show a visit report and a locally generated notification; confirm delivery only after demonstrating the manual external-delivery step.'
    ],
    sources: 'src/clinic/app/nav/; src/clinic/app/ui/; demo/BatchRefillDemo.java; demo/DuplicateBatchDemo.java'
  },
  {
    title: 'Limitations and Responsible Use',
    bullets: [
      'This is a local desktop project, not a certified clinical decision-support or production health-record service.',
      'Safety checking flags recorded allergies for staff review; it does not compare medicine ingredients with allergy names or provide clinical advice.',
      'Guardian messages are generated locally; email/SMS delivery is not integrated and staff must deliver them outside the application.',
      'Appointments, patient deletion/general profile editing, password recovery, and a managed multi-user server deployment are not present in the current workflow.',
      'Accessibility, institution-wide privacy approval, backup/restore operations, and live AI-provider reliability require separate evaluation.'
    ],
    sources: 'src/clinic/service/SafetyChecker.java; src/clinic/service/NotificationStore.java; docs/SCMS_AUDIT_2026-10-25.md (cross-checked against current code)'
  },
  {
    title: 'Conclusion',
    bullets: [
      'SCMS connects staff sign-in, patient and visit records, inventory, dispensing, notifications, reports, and administration in one Java desktop application.',
      'Its implemented strengths are role-aware service checks, batch/expiry handling, local persistence, audit events, and reusable Swing workflows.',
      'Present the system with synthetic or institutionally approved data and describe it as an academic/local prototype.',
      'Next steps should be set by approved project requirements, privacy review, operational recovery needs, accessibility evaluation, and user feedback.'
    ],
    sources: 'Current project implementation; README.md; docs/SCMS_AUDIT_2026-10-25.md'
  }
];

const pdfPath = path.join(__dirname, 'SCMS_Presentation_Source.pdf');
const markdownPath = path.join(__dirname, 'SCMS_Presentation_Slides.md');
const pdf = new PDFDocument({
  size: [960, 540], margin: 0, info: {
    Title: 'UIC School Clinic Management System - Presentation Source',
    Author: 'SCMS Project',
    Subject: 'Implementation-verified, slide-by-slide presentation material',
    Keywords: 'SCMS, school clinic, Java, Swing, H2, presentation'
  }
});
const output = fs.createWriteStream(pdfPath);
pdf.pipe(output);

const colors = {
  navy: '#182238', ink: '#1E293B', muted: '#657187', paper: '#F3F5F8',
  white: '#FFFFFF', pink: '#C92F68', pale: '#FBEAF1', line: '#D9DFE8'
};

function drawSlide(slide, index) {
  if (index > 0) pdf.addPage({ size: [960, 540], margin: 0 });
  pdf.rect(0, 0, 960, 540).fill(colors.paper);
  pdf.rect(0, 0, 960, 132).fill(colors.navy);
  pdf.rect(0, 0, 12, 540).fill(colors.pink);
  pdf.fillColor('#E9ECF3').font('Helvetica-Bold').fontSize(9)
    .text('UIC  /  SCHOOL CLINIC MANAGEMENT SYSTEM', 56, 24, { characterSpacing: 1.1 });
  pdf.fillColor('#F6B8CF').font('Helvetica-Bold').fontSize(10)
    .text(`SLIDE ${String(index + 1).padStart(2, '0')}  /  ${String(slides.length).padStart(2, '0')}`, 815, 24, { width: 95, align: 'right' });
  pdf.fillColor(colors.white).font('Helvetica-Bold').fontSize(index === 0 ? 30 : 27)
    .text(`Slide ${index + 1}: ${slide.title}`, 56, 56, { width: 850, height: 67, ellipsis: true, lineGap: 2 });

  pdf.roundedRect(48, 153, 864, 318, 10).fill(colors.white);
  pdf.roundedRect(48, 153, 7, 318, 3).fill(colors.pink);
  pdf.fillColor(colors.pink).font('Helvetica-Bold').fontSize(10)
    .text('CONTENT', 77, 177, { characterSpacing: 1.5 });
  if (slide.subtitle) {
    pdf.fillColor(colors.muted).font('Helvetica').fontSize(10).text(slide.subtitle, 670, 177, { width: 210, align: 'right' });
  }

  let y = 210;
  let fontSize = 15.5;
  const measure = size => slide.bullets.reduce((total, item, i) => {
    const h = pdf.font('Helvetica').fontSize(size).heightOfString(item, { width: 775, lineGap: 2 });
    return total + Math.max(22, h) + (i === slide.bullets.length - 1 ? 0 : 9);
  }, 0);
  while (fontSize > 13.2 && measure(fontSize) > 245) fontSize -= 0.5;
  slide.bullets.forEach((item, i) => {
    pdf.circle(82, y + 8, 3).fill(colors.pink);
    pdf.fillColor(colors.ink).font('Helvetica').fontSize(fontSize)
      .text(item, 99, y, { width: 775, lineGap: 2 });
    const h = pdf.heightOfString(item, { width: 775, lineGap: 2 });
    y += Math.max(22, h) + (i === slide.bullets.length - 1 ? 0 : 9);
  });

  pdf.moveTo(56, 490).lineTo(904, 490).strokeColor(colors.line).lineWidth(1).stroke();
  pdf.fillColor(colors.muted).font('Helvetica').fontSize(8.1)
    .text(`Implementation references: ${slide.sources}`, 56, 499, { width: 790, height: 22, ellipsis: true });
  pdf.fillColor(colors.pink).font('Helvetica-Bold').fontSize(8.5)
    .text('SCMS  |  26 OCT 2026', 815, 499, { width: 89, align: 'right' });
}

slides.forEach(drawSlide);
pdf.end();

const markdown = [
  '# UIC School Clinic Management System - Presentation Slides',
  '',
  'Prepared October 26, 2026 from the current implementation. Each slide below is a concise source sheet for transfer into presentation software. Statements about unverified outcomes and deployment limits are explicitly qualified.',
  '',
  ...slides.flatMap((slide, i) => [
    `## Slide ${i + 1}: ${slide.title}`,
    '',
    '**Content:**',
    ...slide.bullets.map(item => `- ${item}`),
    '',
    `**Implementation references:** ${slide.sources}`,
    ''
  ])
].join('\n');
fs.writeFileSync(markdownPath, markdown, 'utf8');

output.on('finish', () => {
  console.log(`Created ${path.basename(pdfPath)} (${slides.length} slides)`);
  console.log(`Created ${path.basename(markdownPath)}`);
});
