package com.mkwang.backend.common.utils.businesscodegenerator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Facade sinh Business Code — gọi {@link #generate(BusinessCodeType, String...)} từ bất kỳ service nào.
 *
 * <pre>
 * codeGenerator.generate(EMPLOYEE)                          // MK011
 * codeGenerator.generate(TRANSACTION)                       // TXN-8829145A
 * codeGenerator.generate(PERIOD)                            // PR-2026-04
 * codeGenerator.generate(PROJECT, project.getName())        // PRJ-ERP-2026-001
 * codeGenerator.generate(PHASE, phase.getName())            // PH-UIUX-01
 * codeGenerator.generate(REQUEST, dept.getCode())           // REQ-IT-0426-001
 * codeGenerator.generate(DEPARTMENT, dept.getRawCode())     // IT
 * codeGenerator.generate(PAYSLIP, empCode, "4", "2026")     // PSL-MK001-0426
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class BusinessCodeGenerator {

    private final SequenceService sequenceService;
    private final CodeFormatUtils codeFormatUtils;

    public String generate(BusinessCodeType type, String... params) {
        long seq = type.needsSequence() ? sequenceService.getNextValue(type) : 0L;
        return type.format(seq, codeFormatUtils, params);
    }
}
