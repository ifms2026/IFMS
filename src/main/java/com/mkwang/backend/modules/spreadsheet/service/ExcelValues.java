package com.mkwang.backend.modules.spreadsheet.service;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.spreadsheet.dto.response.ExcelColumnResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public final class ExcelValues {
    private ExcelValues() {}
    public static ExcelColumnResponse column(String key,String label,String type,boolean required) {
        return new ExcelColumnResponse(key,label,type,required);
    }
    public static Long id(Map<String,String> row,String key) {
        String value=row.get(key); if(value==null||value.isBlank()) return null;
        try { long result=Long.parseLong(value); if(result<=0) throw new NumberFormatException(); return result; }
        catch(NumberFormatException error) { throw new BadRequestException(key+": mã tham chiếu phải là số nguyên dương"); }
    }
    public static BigDecimal money(Map<String,String> row,String key) {
        String value=row.get(key); if(value==null||value.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal amount=new BigDecimal(value);
            if(amount.signum()<0 || amount.scale()>2 || amount.precision()>15) throw new NumberFormatException();
            return amount;
        } catch(NumberFormatException error) { throw new BadRequestException(key+": tiền phải là số không âm, tối đa 15 chữ số và 2 số lẻ"); }
    }
    public static LocalDate date(Map<String,String> row,String key) {
        String value=row.get(key); if(value==null||value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch(java.time.format.DateTimeParseException error) { throw new BadRequestException(key+": ngày phải có dạng YYYY-MM-DD"); }
    }
    public static Map<String,Object> values(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
}
