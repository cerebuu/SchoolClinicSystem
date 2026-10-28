#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
. ./tools/java-env.sh
case "${1:-}" in
    ''|--desktop) ;;
    *) echo 'Usage: sh tools/verify.sh [--desktop]' >&2; exit 1 ;;
esac
sh tools/dependencies.sh
scms_root=$PWD
scms_checks=$(mktemp -d "${TMPDIR:-/tmp}/scms-checks.XXXXXX")
echo "Verification output: $scms_checks"
find src tests demo -name '*.java' -print > "$scms_checks/sources.txt"
"$scms_javac" --release 17 -encoding UTF-8 -cp 'lib/*' -d "$scms_checks/classes" "@$scms_checks/sources.txt"
mkdir -p "$scms_checks/classes/clinic/app/ui/auth" "$scms_checks/verification"
cp src/clinic/app/ui/auth/uic-logo.png "$scms_checks/classes/clinic/app/ui/auth/"
cd "$scms_checks"
scms_classpath="$scms_checks/classes:$scms_root/lib/*"
echo "Running clinic.app.ui.common.UicLogoChecks"
"$scms_java" -Djava.awt.headless=true -cp "$scms_classpath" clinic.app.ui.common.UicLogoChecks
echo "Running clinic.app.ui.common.PatientLookupFieldChecks"
"$scms_java" -Djava.awt.headless=true -cp "$scms_classpath" clinic.app.ui.common.PatientLookupFieldChecks
echo "Running clinic.app.ui.notifications.NotificationWorkflowChecks"
"$scms_java" -Djava.awt.headless=true -cp "$scms_classpath" clinic.app.ui.notifications.NotificationWorkflowChecks
if [ "${1:-}" = --desktop ]; then
    "$scms_java" -cp "$scms_classpath" PopupSubmissionChecks
    "$scms_java" -cp "$scms_classpath" DesktopChecks --persistent
    exit
fi
for scms_check in AdminPasswordResetChecks clinic.core.ResetPasswordChecks clinic.core.PasswordFormatChecks clinic.persistence.JdbcValueChecks MedicineCatalogChecks DomainChecks FeatureChecks DispenseChecks PersistentDispenseChecks \
    GuardianPersistenceChecks NotifierChecks clinic.notify.SmsSenderChecks UiChecks RowActionUiChecks DashboardConsultationChecks \
    ConsultationAdministrationChecks ConsultationEnhancementChecks VisitSearchDemo PersistenceChecks PatientEditChecks \
    AuditRepositoryChecks InventoryAuditChecks ClinicalAuditChecks CampusQueryChecks \
    MedicationHistoryChecks ConcurrentDispenseChecks AuthenticationAuditChecks NotificationCampusChecks \
    BackendWorkflowRegressionChecks SecurityMutationChecks NurseCampusAssignmentChecks MedicineSelectionUiChecks \
    clinic.app.ui.consultations.ConsultationMedicineUiChecks clinic.app.ui.common.ActionsSessionChecks \
    clinic.ai.AiChecks clinic.ai.AiConfigChecks; do
    echo "Running $scms_check"
    "$scms_java" -Djava.awt.headless=true -cp "$scms_classpath" "$scms_check"
done
