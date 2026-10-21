package com.mkwang.backend.modules.spreadsheet.service;
import com.mkwang.backend.common.exception.BaseException;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import java.util.*;
import java.util.function.*;
public final class ExcelValidation {
    private ExcelValidation() {}
    public static List<ExcelRowErrorResponse> rows(List<ExcelRowResponse> rows,Function<ExcelRowResponse,String> key,Consumer<ExcelRowResponse> check) {
        List<ExcelRowErrorResponse> errors=new ArrayList<>(); Map<String,Integer> seen=new HashMap<>();Set<Integer> marked=new HashSet<>();
        for(var row:rows) {
            String identity=key.apply(row);
            if(identity!=null&&!identity.isBlank()) {
                Integer previous=seen.putIfAbsent(identity,row.row());
                if(previous!=null) {
                    errors.add(new ExcelRowErrorResponse(row.row(),"","Dữ liệu trùng dòng "+previous));
                    if(marked.add(previous)) errors.add(new ExcelRowErrorResponse(previous,"","Dữ liệu trùng dòng "+row.row()));
                }
            }
            try { check.accept(row); }
            catch(BaseException error) { errors.add(new ExcelRowErrorResponse(row.row(),"",error.getMessage())); }
        }
        return errors;
    }
}
