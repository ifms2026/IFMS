package com.mkwang.backend.modules.spreadsheet.controller;
import com.mkwang.backend.common.dto.*;
import com.mkwang.backend.modules.spreadsheet.dto.response.LookupOptionResponse;
import com.mkwang.backend.modules.spreadsheet.service.FinancialLookupService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/spreadsheets/lookups") @RequiredArgsConstructor
public class FinancialLookupController {
 private final FinancialLookupService lookup;
 @GetMapping("/{kind}") public ResponseEntity<ApiResponse<PageResponse<LookupOptionResponse>>> find(@PathVariable String kind,@RequestParam(required=false)String search,@RequestParam(required=false)Long id,@RequestParam(required=false)Long parent,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int limit){return ResponseEntity.ok(ApiResponse.success(lookup.find(kind,search,id,parent,page,limit)));}
}
