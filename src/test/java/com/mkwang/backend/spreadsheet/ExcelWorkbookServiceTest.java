package com.mkwang.backend.spreadsheet;

import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.spreadsheet.service.ExcelDataset;
import com.mkwang.backend.modules.spreadsheet.service.ExcelWorkbookService;
import com.mkwang.backend.modules.spreadsheet.service.ExcelValues;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelWorkbookServiceTest {
    private final ExcelWorkbookService service = new ExcelWorkbookService();
    private final ExcelDataset dataset = mock(ExcelDataset.class);

    private void configure() {
        ReflectionTestUtils.setField(service, "maxFileBytes", 10_485_760L);
        ReflectionTestUtils.setField(service, "maxRows", 2000);
        when(dataset.code()).thenReturn("payroll");
        when(dataset.columns()).thenReturn(List.of(
                ExcelValues.column("employeeCode", "Mã nhân viên", "TEXT", true),
                ExcelValues.column("baseSalary", "Lương cơ bản", "MONEY", true)));
    }

    private MockMultipartFile workbook(boolean extraHeader, boolean extraRow) throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var schema = workbook.createSheet("_IFMS").createRow(0);
            schema.createCell(0).setCellValue("payroll");
            schema.createCell(1).setCellValue("1");
            var data = workbook.createSheet("Data");
            var header = data.createRow(0);
            header.createCell(0).setCellValue("employeeCode");
            header.createCell(1).setCellValue("baseSalary");
            var row = data.createRow(1);
            row.createCell(0).setCellValue("MK004");
            row.createCell(1).setCellValue(20_000_000);
            var style = workbook.createCellStyle();
            for (var item : List.of(header, row)) {
                for (int col = 2; col < 6; col++) item.createCell(col).setCellStyle(style);
            }
            if (extraHeader) header.getCell(2).setCellValue("unexpected");
            if (extraRow) row.getCell(2).setCellValue("unexpected");
            workbook.write(bytes);
            return new MockMultipartFile("file", "payroll.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    @Test void ignoresStyledBlankCellsOutsideSchema() throws Exception {
        configure();
        var result = service.parse(workbook(false, false), dataset);
        assertEquals(1, result.rows().size());
        assertTrue(result.errors().isEmpty());
        assertEquals("20000000", result.rows().get(0).values().get("baseSalary"));
    }

    @Test void rejectsActualContentOutsideSchema() throws Exception {
        configure();
        assertThrows(BadRequestException.class, () -> service.parse(workbook(true, false), dataset));
        var result = service.parse(workbook(false, true), dataset);
        assertEquals(1, result.rows().size());
        assertEquals(1, result.errors().size());
        assertEquals("Dòng có cột ngoài schema", result.errors().get(0).message());
    }
}
