# File manifest

Files in the completed working-tree change, including the earlier SCMS refactor and subsequent reviewer fixes. Runtime class files are ignored.

| File | Purpose |
|---|---|
| [README.md](../README.md) | Architecture, limitations, file inventory, or usage documentation. |
| [src/clinic/app/GUI.java](../src/clinic/app/GUI.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/Main.java](../src/clinic/app/Main.java) | Previously edited entry point retained; delegates to GUI. |
| [src/clinic/app/UICClinicDashboard.java](../src/clinic/app/UICClinicDashboard.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/core/Login.java](../src/clinic/core/Login.java) | Authentication or password hashing. |
| [src/clinic/model/Alert.java](../src/clinic/model/Alert.java) | Domain model implementation. |
| [src/clinic/model/Employee.java](../src/clinic/model/Employee.java) | Domain model implementation. |
| [src/clinic/model/MedicalRecord.java](../src/clinic/model/MedicalRecord.java) | Domain model implementation. |
| [src/clinic/model/Medicine.java](../src/clinic/model/Medicine.java) | Domain model implementation. |
| [src/clinic/model/MedicineTransaction.java](../src/clinic/model/MedicineTransaction.java) | Domain model implementation. |
| [src/clinic/model/Patient.java](../src/clinic/model/Patient.java) | Domain model implementation. |
| [src/clinic/model/Person.java](../src/clinic/model/Person.java) | Domain model implementation. |
| [src/clinic/model/Student.java](../src/clinic/model/Student.java) | Domain model implementation. |
| [src/clinic/model/User.java](../src/clinic/model/User.java) | Domain model implementation. |
| [src/clinic/service/AuditLog.java](../src/clinic/service/AuditLog.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/Inventory.java](../src/clinic/service/Inventory.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/PatternDetector.java](../src/clinic/service/PatternDetector.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/RestockForecast.java](../src/clinic/service/RestockForecast.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/SafetyChecker.java](../src/clinic/service/SafetyChecker.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/SecuredClinicSystem.java](../src/clinic/service/SecuredClinicSystem.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [Demo.cmd](../Demo.cmd) | Application composition, role navigation, or build/demo launcher. |
| [PagedTableDemo.cmd](../PagedTableDemo.cmd) | Application composition, role navigation, or build/demo launcher. |
| [demo/DashboardConsultationChecks.java](../demo/DashboardConsultationChecks.java) | Standalone sample data or executable verification/demo. |
| [demo/DashboardConsultationDemo.java](../demo/DashboardConsultationDemo.java) | Standalone sample data or executable verification/demo. |
| [demo/DatedDemoVisit.java](../demo/DatedDemoVisit.java) | Standalone sample data or executable verification/demo. |
| [demo/DemoEmployeeWithoutPosition.java](../demo/DemoEmployeeWithoutPosition.java) | Standalone sample data or executable verification/demo. |
| [demo/DemoStudentWithoutSection.java](../demo/DemoStudentWithoutSection.java) | Standalone sample data or executable verification/demo. |
| [demo/DummyTableModel.java](../demo/DummyTableModel.java) | Standalone sample data or executable verification/demo. |
| [demo/PagedTableDemo.java](../demo/PagedTableDemo.java) | Standalone sample data or executable verification/demo. |
| [demo/PatientSectionPositionDemo.java](../demo/PatientSectionPositionDemo.java) | Standalone sample data or executable verification/demo. |
| [demo/SafetyPopupDemo.java](../demo/SafetyPopupDemo.java) | Standalone sample data or executable verification/demo. |
| [demo/VisitSearchDemo.java](../demo/VisitSearchDemo.java) | Standalone sample data or executable verification/demo. |
| [docs/FILES.md](../docs/FILES.md) | Architecture, limitations, file inventory, or usage documentation. |
| [docs/IMPLEMENTATION.md](../docs/IMPLEMENTATION.md) | Architecture, limitations, file inventory, or usage documentation. |
| [docs/RECENT_VISIT_SEARCH.md](../docs/RECENT_VISIT_SEARCH.md) | Architecture, limitations, file inventory, or usage documentation. |
| [src/clinic/app/ApplicationContext.java](../src/clinic/app/ApplicationContext.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/ClinicWorkspace.java](../src/clinic/app/ClinicWorkspace.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/LoginPanel.java](../src/clinic/app/LoginPanel.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/ModulePage.java](../src/clinic/app/ModulePage.java) | Existing legacy workspace component retained; not used by the new routed screens. |
| [src/clinic/app/NurseDashboard.java](../src/clinic/app/NurseDashboard.java) | Existing legacy workspace component retained; not used by the new routed screens. |
| [src/clinic/app/Router.java](../src/clinic/app/Router.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/Theme.java](../src/clinic/app/Theme.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/nav/MainShell.java](../src/clinic/app/nav/MainShell.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/nav/NavItem.java](../src/clinic/app/nav/NavItem.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/nav/Navigator.java](../src/clinic/app/nav/Navigator.java) | Application composition, role navigation, or build/demo launcher. |
| [src/clinic/app/ui/admin/AdminDashboardPanel.java](../src/clinic/app/ui/admin/AdminDashboardPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/admin/AdminShell.java](../src/clinic/app/ui/admin/AdminShell.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/admin/AuditLogPanel.java](../src/clinic/app/ui/admin/AuditLogPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/admin/AuditTableModel.java](../src/clinic/app/ui/admin/AuditTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/admin/CampusManagementPanel.java](../src/clinic/app/ui/admin/CampusManagementPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/admin/CampusesTableModel.java](../src/clinic/app/ui/admin/CampusesTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/admin/UserManagementPanel.java](../src/clinic/app/ui/admin/UserManagementPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/admin/UsersTableModel.java](../src/clinic/app/ui/admin/UsersTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/auth/LoginPanel.java](../src/clinic/app/ui/auth/LoginPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/common/Actions.java](../src/clinic/app/ui/common/Actions.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/AsyncLoader.java](../src/clinic/app/ui/common/AsyncLoader.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/BasePanel.java](../src/clinic/app/ui/common/BasePanel.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/PageSource.java](../src/clinic/app/ui/common/PageSource.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/PagedTableModel.java](../src/clinic/app/ui/common/PagedTableModel.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/PagedTablePanel.java](../src/clinic/app/ui/common/PagedTablePanel.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/SearchBar.java](../src/clinic/app/ui/common/SearchBar.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/popup/FormField.java](../src/clinic/app/ui/common/popup/FormField.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/popup/FormPopup.java](../src/clinic/app/ui/common/popup/FormPopup.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/popup/MessagePopup.java](../src/clinic/app/ui/common/popup/MessagePopup.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/common/popup/WarningOverridePopup.java](../src/clinic/app/ui/common/popup/WarningOverridePopup.java) | Reusable UI control, paging, background loading, or popup. |
| [src/clinic/app/ui/consultations/ConsultationsPanel.java](../src/clinic/app/ui/consultations/ConsultationsPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/consultations/ConsultationsTableModel.java](../src/clinic/app/ui/consultations/ConsultationsTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/consultations/NewConsultationForm.java](../src/clinic/app/ui/consultations/NewConsultationForm.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/dashboard/DashboardPanel.java](../src/clinic/app/ui/dashboard/DashboardPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/dashboard/RecentVisitRow.java](../src/clinic/app/ui/dashboard/RecentVisitRow.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/dashboard/RecentVisitsPanel.java](../src/clinic/app/ui/dashboard/RecentVisitsPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/dashboard/RecentVisitsTableModel.java](../src/clinic/app/ui/dashboard/RecentVisitsTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/dashboard/VisitPeriod.java](../src/clinic/app/ui/dashboard/VisitPeriod.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/dispense/DispensePanel.java](../src/clinic/app/ui/dispense/DispensePanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/inventory/InventoryPanel.java](../src/clinic/app/ui/inventory/InventoryPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/inventory/InventoryTableModel.java](../src/clinic/app/ui/inventory/InventoryTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/notifications/NotificationsPanel.java](../src/clinic/app/ui/notifications/NotificationsPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/notifications/NotificationsTableModel.java](../src/clinic/app/ui/notifications/NotificationsTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/patients/PatientsPanel.java](../src/clinic/app/ui/patients/PatientsPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/patients/PatientsTableModel.java](../src/clinic/app/ui/patients/PatientsTableModel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/reports/ReportsPanel.java](../src/clinic/app/ui/reports/ReportsPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/app/ui/settings/SettingsPanel.java](../src/clinic/app/ui/settings/SettingsPanel.java) | Separated feature panel, table model, or form configuration. |
| [src/clinic/core/PasswordHasher.java](../src/clinic/core/PasswordHasher.java) | Authentication or password hashing. |
| [src/clinic/model/Campus.java](../src/clinic/model/Campus.java) | Domain model implementation. |
| [src/clinic/service/AdminService.java](../src/clinic/service/AdminService.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/InMemoryClinicSystem.java](../src/clinic/service/InMemoryClinicSystem.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/PageResult.java](../src/clinic/service/PageResult.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/service/VisitSearch.java](../src/clinic/service/VisitSearch.java) | Domain, authorization, bounded-query, inventory, or administration service. |
| [src/clinic/util/PatientDisplay.java](../src/clinic/util/PatientDisplay.java) | Shared validation, presentation, or formatting utility. |
| [src/clinic/util/Validation.java](../src/clinic/util/Validation.java) | Shared validation, presentation, or formatting utility. |
| [tests/DomainChecks.java](../tests/DomainChecks.java) | Regression checks. |
| [tests/UiChecks.java](../tests/UiChecks.java) | Regression checks. |
| [tools/verify.cmd](../tools/verify.cmd) | Application composition, role navigation, or build/demo launcher. |
| [verification/admin.png](../verification/admin.png) | Captured verification output or offscreen UI snapshot. |
| [verification/build.txt](../verification/build.txt) | Captured verification output or offscreen UI snapshot. |
| [verification/checks.txt](../verification/checks.txt) | Captured verification output or offscreen UI snapshot. |
| [verification/dashboard-without-status.png](../verification/dashboard-without-status.png) | Captured verification output or offscreen UI snapshot. |
| [verification/login.png](../verification/login.png) | Captured verification output or offscreen UI snapshot. |
| [verification/nurse.png](../verification/nurse.png) | Captured verification output or offscreen UI snapshot. |
| [verification/paged-10000.png](../verification/paged-10000.png) | Captured verification output or offscreen UI snapshot. |
