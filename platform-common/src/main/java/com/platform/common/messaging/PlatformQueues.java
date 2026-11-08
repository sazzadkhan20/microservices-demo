package com.platform.common.messaging;

/**
 * Canonical exchange / routing keys for the platform broker (guide §1 messaging).
 */
public final class PlatformQueues {

    public static final String EXCHANGE = "platform.events";
    public static final String DLX = "platform.events.dlx";

    /** Payroll asks Leave to apply/reverse absence cover for a finalize/reopen operation. */
    public static final String PAYROLL_LEAVE_COVER_CMD = "payroll.leave-cover.command";
    public static final String PAYROLL_LEAVE_COVER_Q = "q.payroll.leave-cover.command";
    public static final String PAYROLL_LEAVE_COVER_DLQ = "q.payroll.leave-cover.command.dlq";

    /** Leave replies with cover result for a payroll operation. */
    public static final String LEAVE_COVER_RESULT = "leave.cover.result";
    public static final String LEAVE_COVER_RESULT_Q = "q.leave.cover.result";
    public static final String LEAVE_COVER_RESULT_DLQ = "q.leave.cover.result.dlq";

    /** Best-effort notification create commands (durable attempts). */
    public static final String NOTIFICATION_CREATE = "notification.create.command";
    public static final String NOTIFICATION_CREATE_Q = "q.notification.create.command";
    public static final String NOTIFICATION_CREATE_DLQ = "q.notification.create.command.dlq";

    /** File scan jobs after quarantine upload. */
    public static final String FILE_SCAN = "files.scan.command";
    public static final String FILE_SCAN_Q = "q.files.scan.command";
    public static final String FILE_SCAN_DLQ = "q.files.scan.command.dlq";

    /** Report export jobs. */
    public static final String REPORT_EXPORT = "reports.export.command";
    public static final String REPORT_EXPORT_Q = "q.reports.export.command";
    public static final String REPORT_EXPORT_DLQ = "q.reports.export.command.dlq";

    /**
     * People publishes employee upserts; Leave and Time&amp;Work each have a fan-out queue
     * bound to the same routing key (guide §3 reference projection).
     */
    public static final String PEOPLE_EMPLOYEE_UPSERTED = "people.employee.upserted";
    public static final String PEOPLE_EMPLOYEE_UPSERTED_LEAVE_Q = "q.people.employee.upserted.leave";
    public static final String PEOPLE_EMPLOYEE_UPSERTED_LEAVE_DLQ = "q.people.employee.upserted.leave.dlq";
    public static final String PEOPLE_EMPLOYEE_UPSERTED_TIMEWORK_Q = "q.people.employee.upserted.timework";
    public static final String PEOPLE_EMPLOYEE_UPSERTED_TIMEWORK_DLQ = "q.people.employee.upserted.timework.dlq";

    private PlatformQueues() {
    }
}
