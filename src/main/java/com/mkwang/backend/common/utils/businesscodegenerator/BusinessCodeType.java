package com.mkwang.backend.common.utils.businesscodegenerator;

import com.mkwang.backend.common.exception.BadRequestException;
import java.time.LocalDate;

/**
 * Enum định nghĩa tất cả loại Business Code trong IFMS.
 * Mỗi constant tự chứa logic format của mình (Strategy pattern via abstract method).
 *
 * <pre>
 * | Type        | Format                        | Example             |
 * |-------------|-------------------------------|---------------------|
 * | EMPLOYEE    | MK{SEQ:03d}                   | MK011               |
 * | DEPARTMENT  | {NORMALIZED_CODE}             | IT, FIN, HR         |
 * | PROJECT     | PRJ-{SLUG}-{YYYY}-{SEQ:03d}   | PRJ-ERP-2026-001    |
 * | PHASE       | PH-{SLUG}-{SEQ:02d}           | PH-UIUX-01          |
 * | REQUEST     | REQ-{DEPT}-{MMYY}-{SEQ:03d}   | REQ-IT-0426-001     |
 * | TRANSACTION | TXN-{8 hex}                   | TXN-8829145A        |
 * | PERIOD      | PR-{YYYY}-{MM}                | PR-2026-04          |
 * | PAYSLIP     | PSL-{EMP_CODE}-{MMYY}         | PSL-MK001-0426      |
 * | WITHDRAWAL  | WD-{YYYY}-{SEQ:06d}           | WD-2026-000012      |
 * </pre>
 */
public enum BusinessCodeType {

    EMPLOYEE("seq_employee_code") {
        /** params[0] = prefix (optional, default "MK") */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            String prefix = (params.length > 0 && params[0] != null && !params[0].isBlank())
                    ? utils.sanitizeSlug(params[0], 5) : "MK";
            return prefix + CodeFormatUtils.padLeft(seq, 3);
        }
    },

    DEPARTMENT(null) {
        /** params[0] = rawCode (bắt buộc) */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            if (params.length == 0 || params[0] == null || params[0].isBlank()) {
                throw new BadRequestException("Department code must be provided");
            }
            return utils.sanitizeSlug(params[0], 20);
        }
    },

    PROJECT("seq_project_code") {
        /** params[0] = projectNameSlug (e.g. "ERP", "CRM") */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            String slug = (params.length > 0 && params[0] != null && !params[0].isBlank())
                    ? utils.sanitizeSlug(params[0], 10) : "GEN";
            return "PRJ-" + slug + "-" + LocalDate.now().getYear() + "-" + CodeFormatUtils.padLeft(seq, 3);
        }
    },

    PHASE("seq_phase_code") {
        /** params[0] = phaseNameSlug (e.g. "UIUX", "DEV") */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            String slug = (params.length > 0 && params[0] != null && !params[0].isBlank())
                    ? utils.sanitizeSlug(params[0], 10) : "PH";
            return "PH-" + slug + "-" + CodeFormatUtils.padLeft(seq, 2);
        }
    },

    REQUEST("seq_request_code") {
        /** params[0] = departmentCode (e.g. "IT", "FIN") */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            String deptCode = (params.length > 0 && params[0] != null && !params[0].isBlank())
                    ? utils.sanitizeSlug(params[0], 10) : "GEN";
            LocalDate now = LocalDate.now();
            String mmyy = CodeFormatUtils.padLeft(now.getMonthValue(), 2)
                    + CodeFormatUtils.padLeft(now.getYear() % 100, 2);
            return "REQ-" + deptCode + "-" + mmyy + "-" + CodeFormatUtils.padLeft(seq, 3);
        }
    },

    TRANSACTION(null) {
        /** Không cần params — random hex */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            return "TXN-" + utils.randomHex(8);
        }
    },

    PERIOD(null) {
        /** params[0] = year, params[1] = month (optional — default current date) */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            int year, month;
            if (params.length >= 2 && params[0] != null && params[1] != null) {
                year = Integer.parseInt(params[0]);
                month = Integer.parseInt(params[1]);
                if (month < 1 || month > 12) {
                    throw new BadRequestException("Month must be between 1 and 12, got: " + month);
                }
            } else {
                LocalDate now = LocalDate.now();
                year = now.getYear();
                month = now.getMonthValue();
            }
            return "PR-" + year + "-" + CodeFormatUtils.padLeft(month, 2);
        }
    },

    PAYSLIP(null) {
        /** params[0] = employeeCode, params[1] = month, params[2] = year */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            if (params.length < 3 || params[0] == null || params[1] == null || params[2] == null) {
                throw new BadRequestException("PAYSLIP requires: employeeCode, month, year");
            }
            String empCode = utils.sanitizeSlug(params[0], 10);
            int month = Integer.parseInt(params[1]);
            int year = Integer.parseInt(params[2]);
            return "PSL-" + empCode + "-"
                    + CodeFormatUtils.padLeft(month, 2)
                    + CodeFormatUtils.padLeft(year % 100, 2);
        }
    },

    WITHDRAWAL("seq_withdraw_code") {
        /** Không cần params — format WD-{YYYY}-{SEQ:06d} */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            return "WD-" + LocalDate.now().getYear() + "-" + CodeFormatUtils.padLeft(seq, 6);
        }
    },

    DEPOSIT("seq_deposit_code") {
        /** Không cần params — format DEP-{YYYY}-{SEQ:06d} */
        @Override
        public String format(long seq, CodeFormatUtils utils, String... params) {
            return "DEP-" + LocalDate.now().getYear() + "-" + CodeFormatUtils.padLeft(seq, 6);
        }
    };

    // ─────────────────────────────────────────────────────────────────

    private final String sequenceName;

    BusinessCodeType(String sequenceName) {
        this.sequenceName = sequenceName;
    }

    /** Logic format mã — implemented bởi mỗi enum constant. */
    public abstract String format(long seq, CodeFormatUtils utils, String... params);

    /** true = cần gọi nextval() từ PostgreSQL trước khi format */
    public boolean needsSequence() {
        return sequenceName != null;
    }

    public String getSequenceName() {
        return sequenceName;
    }
}
