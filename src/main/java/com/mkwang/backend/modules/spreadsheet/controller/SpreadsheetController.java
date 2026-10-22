package com.mkwang.backend.modules.spreadsheet.controller;
import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import com.mkwang.backend.modules.spreadsheet.service.SpreadsheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController @RequestMapping("/spreadsheets") @RequiredArgsConstructor
public class SpreadsheetController {
    private final SpreadsheetService excel;
    @GetMapping("/{dataset}/scopes") public ResponseEntity<ApiResponse<List<Map<String,Object>>>> scopes(@PathVariable String dataset) {
        return ResponseEntity.ok(ApiResponse.success(excel.scopes(dataset)));
    }
    @GetMapping public ResponseEntity<ApiResponse<List<ExcelDatasetResponse>>> datasets() {
        return ResponseEntity.ok(ApiResponse.success(excel.list()));
    }
    @GetMapping("/exports/history") public ResponseEntity<ApiResponse<List<ExcelExportLogResponse>>> exportHistory() {
        return ResponseEntity.ok(ApiResponse.success(excel.exportHistory()));
    }
    @GetMapping("/{dataset}/template") public ResponseEntity<byte[]> template(@PathVariable String dataset,@RequestParam(required=false)Long scope) {
        return file(excel.template(dataset,scope),dataset+"-template.xlsx");
    }
    @PostMapping(value="/{dataset}/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExcelPreviewResponse>> preview(@PathVariable String dataset,@RequestParam(required=false)Long scope,@RequestPart("file")MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(excel.preview(dataset,scope,file)));
    }
    @PostMapping("/batches/{id}/commit") public ResponseEntity<ApiResponse<ExcelPreviewResponse>> commit(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(excel.commit(id)));
    }
    @GetMapping("/batches/{id}/errors") public ResponseEntity<byte[]> errors(@PathVariable String id) { return file(excel.errors(id),"excel-errors.xlsx"); }
    @GetMapping("/{dataset}/export") public ResponseEntity<byte[]> export(@PathVariable String dataset,@RequestParam(required=false)Long scope,@RequestParam Map<String,String> filters) {
        return file(excel.export(dataset,scope,filters),dataset+"-export.xlsx");
    }
    private ResponseEntity<byte[]> file(byte[] data,String filename) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).body(data);
    }
}
