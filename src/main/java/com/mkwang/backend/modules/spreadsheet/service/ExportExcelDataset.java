package com.mkwang.backend.modules.spreadsheet.service;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.spreadsheet.dto.response.ExcelColumnResponse;
import com.mkwang.backend.modules.user.entity.User;
import java.util.*;
public record ExportExcelDataset(String code,String label,Set<String> roles,List<ExcelColumnResponse> columns,ExportAction action) implements ExcelDataset {
    @FunctionalInterface public interface ExportAction { List<Map<String,Object>> get(User actor,Map<String,String> filters,int max); }
    public boolean importEnabled(){return false;}
    public boolean supportsDateFilter(){return !Set.of("project-financial-summary","advance-balances","department-employees","department-budgets","project-cost-details","bank-reconciliation-summary").contains(code);}
    public void authorize(User actor,Long scope){if(scope!=null) throw new BadRequestException("Bộ xuất này tự xác định phạm vi theo tài khoản đăng nhập");}
    public List<Map<String,Object>> export(User actor,Long scope,Map<String,String> filters,int max){return action.get(actor,filters,max);}
}
