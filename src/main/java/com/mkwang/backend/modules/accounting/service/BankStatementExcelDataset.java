package com.mkwang.backend.modules.accounting.service;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.modules.spreadsheet.dto.response.*;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Component @RequiredArgsConstructor
public class BankStatementExcelDataset implements ExcelDataset {
    private final BankEvidenceService bank;
    public String code(){return "bank-statements";}public String label(){return "Sao kê ngân hàng công ty · chỉ nhập chứng cứ đối soát";}
    public Set<String> roles(){return Set.of("CFO","ACCOUNTANT");}
    public List<ExcelColumnResponse> columns(){return List.of(column("bankAccount","Tài khoản công ty","TEXT",true),column("bankReference","Mã giao dịch ngân hàng","TEXT",true),column("bookingDate","Ngày sao kê YYYY-MM-DD","TEXT",true),column("direction","IN/OUT","TEXT",true),column("amount","Số tiền","MONEY",true),column("state","BOOKED/PENDING","TEXT",true),column("transactionId","ID giao dịch IFMS nếu đã xác minh khớp","TEXT",false),column("description","Diễn giải","TEXT",false));}
    public void authorize(User actor,Long scope){if(scope!=null) throw new BadRequestException("Sao kê dùng tài khoản công ty được cấu hình");bank.account();}
    public List<Map<String,Object>> references(User actor,Long scope){return List.of(values("bankAccount",bank.account()));}
    public List<ExcelRowErrorResponse> validate(List<ExcelRowResponse> rows,User actor,Long scope){
        return ExcelValidation.rows(rows,row->row.values().get("bankAccount")+":"+row.values().get("bankReference").toUpperCase(Locale.ROOT),row->{
            var v=row.values();
            if(!bank.account().equals(v.get("bankAccount"))) throw new BadRequestException("Sao kê không thuộc tài khoản công ty đã cấu hình");
            if(v.get("bankReference").isBlank()||v.get("bankReference").length()>100) throw new BadRequestException("Mã sao kê không hợp lệ");
            if(!Set.of("IN","OUT").contains(v.get("direction"))||!Set.of("BOOKED","PENDING").contains(v.get("state"))) throw new BadRequestException("Chiều/trạng thái sao kê không hợp lệ");
            var amount=money(v,"amount");var date=date(v,"bookingDate");
            if(amount.signum()<=0||date==null||date.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) throw new BadRequestException("Số tiền/ngày sao kê không hợp lệ");
            bank.validateImport(v.get("bankAccount"),v.get("bankReference").toUpperCase(Locale.ROOT),amount,v.get("direction"),v.get("state"));
            Long transaction=id(v,"transactionId");if(transaction!=null) bank.validateMatch(transaction,amount,v.get("direction"));
        });
    }
    public String commit(ExcelRowResponse row,User actor,Long scope){var v=row.values();return "BANK_LINE:"+bank.importLine(v.get("bankAccount"),v.get("bankReference").toUpperCase(Locale.ROOT),date(v,"bookingDate"),v.get("direction"),money(v,"amount"),v.get("state"),v.get("description"),actor.getId(),id(v,"transactionId"));}
    public List<ExcelColumnResponse> exportColumns(){var result=new ArrayList<>(columns());result.add(column("matchingStatus","MATCHED/UNMATCHED/IN_TRANSIT","TEXT",false));return result;}
    public List<Map<String,Object>> export(User actor,Long scope,Map<String,String> filters,int max){return bank.lines(date(filters,"from"),date(filters,"to"),max);}
}
