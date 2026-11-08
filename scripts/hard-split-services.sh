#!/usr/bin/env bash
# Hard-split hrms-domain into people / leave / time-work (no shared domain JAR).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/hrms-domain/src/main/java/com/platform/hrms"

copy_tree() {
  local dest="$1"
  mkdir -p "$dest"
  rm -rf "$dest"
  mkdir -p "$(dirname "$dest")"
  cp -a "$SRC" "$dest"
}

PEOPLE="$ROOT/people-service/src/main/java/com/platform/hrms"
LEAVE="$ROOT/leave-service/src/main/java/com/platform/hrms"
TIME="$ROOT/time-work-service/src/main/java/com/platform/hrms"

echo "Copying domain → people / leave / time-work..."
copy_tree "$PEOPLE"
copy_tree "$LEAVE"
copy_tree "$TIME"

# --- PEOPLE: remove leave + time-work owned surfaces ---
echo "Pruning people-service..."
rm -f "$PEOPLE"/api/rest/Leave*.java \
      "$PEOPLE"/api/rest/ApprovalsController.java \
      "$PEOPLE"/api/rest/Attendance*.java \
      "$PEOPLE"/api/rest/WorkScheduleController.java \
      "$PEOPLE"/api/rest/HolidayController.java \
      "$PEOPLE"/api/rest/TaskController.java \
      "$PEOPLE"/api/rest/WorkLogController.java
rm -f "$PEOPLE"/application/Leave*.java \
      "$PEOPLE"/application/Attendance*.java \
      "$PEOPLE"/application/WorkScheduleService.java \
      "$PEOPLE"/application/WorkLogService.java \
      "$PEOPLE"/application/TaskService.java \
      "$PEOPLE"/application/ApprovalAuthorityService.java
rm -f "$PEOPLE"/api/dto/Leave*.java \
      "$PEOPLE"/api/dto/ReliefAssignRequest.java \
      "$PEOPLE"/api/dto/AttendanceActionRequest.java
rm -f "$PEOPLE"/domain/Leave*.java \
      "$PEOPLE"/domain/StoreShiftCoverage.java \
      "$PEOPLE"/domain/Attendance*.java \
      "$PEOPLE"/domain/WorkSchedule.java \
      "$PEOPLE"/domain/Holiday.java \
      "$PEOPLE"/domain/WorkLog.java \
      "$PEOPLE"/domain/WorkTask*.java
rm -f "$PEOPLE"/infrastructure/persistence/Leave*.java \
      "$PEOPLE"/infrastructure/persistence/StoreShiftCoverageRepository.java \
      "$PEOPLE"/infrastructure/persistence/Attendance*.java \
      "$PEOPLE"/infrastructure/persistence/WorkScheduleRepository.java \
      "$PEOPLE"/infrastructure/persistence/HolidayRepository.java \
      "$PEOPLE"/infrastructure/persistence/WorkLogRepository.java \
      "$PEOPLE"/infrastructure/persistence/WorkTask*.java
rm -f "$PEOPLE"/messaging/LeaveCoverCommandListener.java \
      "$PEOPLE"/messaging/EmployeeProjectionListener.java \
      "$PEOPLE"/messaging/EmployeeProjectionApplier.java
rm -f "$PEOPLE"/extension/engine/LeaveCalcEngine.java

# --- LEAVE: remove people + time-work owned surfaces ---
echo "Pruning leave-service..."
rm -f "$LEAVE"/api/rest/Employee*.java \
      "$LEAVE"/api/rest/OrgController.java \
      "$LEAVE"/api/rest/TeamController.java \
      "$LEAVE"/api/rest/LocationController.java \
      "$LEAVE"/api/rest/RolesController.java \
      "$LEAVE"/api/rest/AuthController.java \
      "$LEAVE"/api/rest/InternalAuthController.java \
      "$LEAVE"/api/rest/MeController.java \
      "$LEAVE"/api/rest/SettingsController.java \
      "$LEAVE"/api/rest/NotificationSettingsController.java \
      "$LEAVE"/api/rest/AuditController.java \
      "$LEAVE"/api/rest/ChecklistController.java \
      "$LEAVE"/api/rest/PayrollFactsController.java \
      "$LEAVE"/api/rest/DashboardController.java \
      "$LEAVE"/api/rest/BusinessRulesController.java \
      "$LEAVE"/api/rest/ReportFacadeController.java \
      "$LEAVE"/api/rest/CompatibilityAliasesController.java \
      "$LEAVE"/api/rest/Attendance*.java \
      "$LEAVE"/api/rest/WorkScheduleController.java \
      "$LEAVE"/api/rest/HolidayController.java \
      "$LEAVE"/api/rest/TaskController.java \
      "$LEAVE"/api/rest/WorkLogController.java
rm -f "$LEAVE"/api/grpc/*.java
rm -f "$LEAVE"/application/Employee*.java \
      "$LEAVE"/application/EmploymentHistoryService.java \
      "$LEAVE"/application/ReportingLineService.java \
      "$LEAVE"/application/OrgChartService.java \
      "$LEAVE"/application/OffboardingService.java \
      "$LEAVE"/application/AuthFacadeService.java \
      "$LEAVE"/application/AuditService.java \
      "$LEAVE"/application/PayrollFactsService.java \
      "$LEAVE"/application/Attendance*.java \
      "$LEAVE"/application/WorkScheduleService.java \
      "$LEAVE"/application/WorkLogService.java \
      "$LEAVE"/application/TaskService.java
rm -f "$LEAVE"/api/dto/Employee*.java \
      "$LEAVE"/api/dto/DepartmentResponse.java \
      "$LEAVE"/api/dto/DesignationResponse.java \
      "$LEAVE"/api/dto/AttendanceActionRequest.java
# Keep Employee/Team/Location/Designation/Department as local projections + RBAC for leave auth
# Remove time-work entities except we keep Holiday/WorkSchedule as projections OR delete and use client
rm -f "$LEAVE"/domain/Attendance*.java \
      "$LEAVE"/domain/WorkLog.java \
      "$LEAVE"/domain/WorkTask*.java \
      "$LEAVE"/domain/EmploymentFieldHistory.java \
      "$LEAVE"/domain/ReportingLineHistory.java \
      "$LEAVE"/domain/ExtIndexedAttribute.java \
      "$LEAVE"/domain/TenantFieldDefinition.java \
      "$LEAVE"/domain/AuditLog.java
rm -f "$LEAVE"/infrastructure/persistence/Attendance*.java \
      "$LEAVE"/infrastructure/persistence/WorkLogRepository.java \
      "$LEAVE"/infrastructure/persistence/WorkTask*.java \
      "$LEAVE"/infrastructure/persistence/EmploymentFieldHistoryRepository.java \
      "$LEAVE"/infrastructure/persistence/ReportingLineHistoryRepository.java \
      "$LEAVE"/infrastructure/persistence/ExtIndexedAttributeRepository.java \
      "$LEAVE"/infrastructure/persistence/TenantFieldDefinitionRepository.java \
      "$LEAVE"/infrastructure/persistence/AuditLogRepository.java
rm -f "$LEAVE"/messaging/EmployeeProjectionPublisher.java
rm -rf "$LEAVE"/extension

# --- TIME-WORK: remove people + leave owned surfaces ---
echo "Pruning time-work-service..."
rm -f "$TIME"/api/rest/Employee*.java \
      "$TIME"/api/rest/OrgController.java \
      "$TIME"/api/rest/TeamController.java \
      "$TIME"/api/rest/LocationController.java \
      "$TIME"/api/rest/RolesController.java \
      "$TIME"/api/rest/AuthController.java \
      "$TIME"/api/rest/InternalAuthController.java \
      "$TIME"/api/rest/MeController.java \
      "$TIME"/api/rest/SettingsController.java \
      "$TIME"/api/rest/NotificationSettingsController.java \
      "$TIME"/api/rest/AuditController.java \
      "$TIME"/api/rest/ChecklistController.java \
      "$TIME"/api/rest/PayrollFactsController.java \
      "$TIME"/api/rest/DashboardController.java \
      "$TIME"/api/rest/BusinessRulesController.java \
      "$TIME"/api/rest/ReportFacadeController.java \
      "$TIME"/api/rest/CompatibilityAliasesController.java \
      "$TIME"/api/rest/Leave*.java \
      "$TIME"/api/rest/ApprovalsController.java
rm -f "$TIME"/api/grpc/*.java
rm -f "$TIME"/application/Employee*.java \
      "$TIME"/application/EmploymentHistoryService.java \
      "$TIME"/application/ReportingLineService.java \
      "$TIME"/application/OrgChartService.java \
      "$TIME"/application/OffboardingService.java \
      "$TIME"/application/AuthFacadeService.java \
      "$TIME"/application/AuditService.java \
      "$TIME"/application/PayrollFactsService.java \
      "$TIME"/application/Leave*.java \
      "$TIME"/application/ApprovalAuthorityService.java
rm -f "$TIME"/api/dto/Employee*.java \
      "$TIME"/api/dto/DepartmentResponse.java \
      "$TIME"/api/dto/DesignationResponse.java \
      "$TIME"/api/dto/Leave*.java \
      "$TIME"/api/dto/ReliefAssignRequest.java
rm -f "$TIME"/domain/Leave*.java \
      "$TIME"/domain/StoreShiftCoverage.java \
      "$TIME"/domain/EmploymentFieldHistory.java \
      "$TIME"/domain/ReportingLineHistory.java \
      "$TIME"/domain/ExtIndexedAttribute.java \
      "$TIME"/domain/TenantFieldDefinition.java \
      "$TIME"/domain/AuditLog.java \
      "$TIME"/domain/Department.java \
      "$TIME"/domain/Designation.java \
      "$TIME"/domain/Location.java \
      "$TIME"/domain/Team.java
rm -f "$TIME"/infrastructure/persistence/Leave*.java \
      "$TIME"/infrastructure/persistence/StoreShiftCoverageRepository.java \
      "$TIME"/infrastructure/persistence/EmploymentFieldHistoryRepository.java \
      "$TIME"/infrastructure/persistence/ReportingLineHistoryRepository.java \
      "$TIME"/infrastructure/persistence/ExtIndexedAttributeRepository.java \
      "$TIME"/infrastructure/persistence/TenantFieldDefinitionRepository.java \
      "$TIME"/infrastructure/persistence/AuditLogRepository.java \
      "$TIME"/infrastructure/persistence/DepartmentRepository.java \
      "$TIME"/infrastructure/persistence/DesignationRepository.java \
      "$TIME"/infrastructure/persistence/LocationRepository.java \
      "$TIME"/infrastructure/persistence/TeamRepository.java
rm -f "$TIME"/messaging/LeaveCoverCommandListener.java \
      "$TIME"/messaging/EmployeeProjectionPublisher.java
rm -f "$TIME"/extension/engine/LeaveCalcEngine.java

echo "Done pruning."
echo "people files: $(find "$PEOPLE" -name '*.java' | wc -l)"
echo "leave files:  $(find "$LEAVE" -name '*.java' | wc -l)"
echo "time files:   $(find "$TIME" -name '*.java' | wc -l)"
