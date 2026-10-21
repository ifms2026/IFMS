package com.mkwang.backend.modules.wallet.service;
import com.mkwang.backend.modules.spreadsheet.service.*;
import com.mkwang.backend.modules.spreadsheet.repository.ExcelExportSpecification;
import com.mkwang.backend.modules.spreadsheet.dto.response.ExcelColumnResponse;
import com.mkwang.backend.modules.wallet.entity.*;
import com.mkwang.backend.modules.wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import java.util.*;
import static com.mkwang.backend.modules.spreadsheet.service.ExcelValues.*;
@Configuration @RequiredArgsConstructor
public class WalletExcelConfiguration {
    private final LedgerEntryRepository entries;private final TransactionRepository transactions;
    private final WalletFinancialEvidenceService financial;
    private static final Set<String> ALL=Set.of("ADMIN","CFO","ACCOUNTANT","MANAGER","TEAM_LEADER","EMPLOYEE");
    private List<ExcelColumnResponse> columns(){return List.of(column("transactionCode","Mã giao dịch","TEXT",false),column("time","Thời gian","TEXT",false),column("type","Nghiệp vụ","TEXT",false),column("direction","Chiều tiền","TEXT",false),column("amount","Số tiền","MONEY",false),column("balanceAfter","Số dư sau","MONEY_INFO",false),column("reference","Tham chiếu","TEXT",false));}
    private ExcelDataset personal(String code,String label,TransactionType type) {
        return new ExportExcelDataset(code,label,ALL,columns(),(actor,filters,max)->{
            Map<String,Object> dimensions=new HashMap<>();dimensions.put("wallet.ownerType",WalletOwnerType.USER);dimensions.put("wallet.ownerId",actor.getId());
            if(type!=null)dimensions.put("transaction.type",type);
            var spec=ExcelExportSpecification.<LedgerEntry>filter(filters,dimensions);
            return entries.findAll(spec,PageRequest.of(0,max+1,Sort.by("id"))).stream().map(e->values(
                    "transactionCode",e.getTransaction().getTransactionCode(),"time",e.getCreatedAt(),"type",e.getTransaction().getType(),
                    "direction",e.getDirection()==TransactionDirection.CREDIT?"Tiền vào":"Tiền ra","amount",e.getAmount(),
                    "balanceAfter",e.getBalanceAfter(),"reference",e.getTransaction().getReferenceType()+":"+e.getTransaction().getReferenceId())).toList();
        });
    }
    @Bean ExcelDataset myWalletExcel(){return personal("my-wallet","Sao kê ví của tôi",null);}
    @Bean ExcelDataset myDepositsExcel(){return personal("my-deposits","Nạp tiền cá nhân của tôi",TransactionType.DEPOSIT);}
    @Bean ExcelDataset myWithdrawalsExcel(){return personal("my-withdrawals","Rút tiền cá nhân của tôi",TransactionType.WITHDRAW);}
    @Bean ExcelDataset corporateTransactionsExcel(){
        var cols=List.of(column("transactionCode","Mã giao dịch","TEXT",false),column("time","Thời gian","TEXT",false),column("type","Nghiệp vụ","TEXT",false),column("amount","Giá trị giao dịch","MONEY_INFO",false),column("corporateChange","Biến động tổng tiền công ty","MONEY",false),column("source","Nguồn tham chiếu","TEXT",false),column("description","Diễn giải","TEXT",false));
        return new ExportExcelDataset("corporate-cashflow","Dòng tiền và phân bổ nội bộ công ty",Set.of("CFO","ACCOUNTANT"),cols,(actor,filters,max)->{
            var spec=ExcelExportSpecification.<Transaction>filter(filters,Map.of("status",TransactionStatus.SUCCESS));
            spec=spec.and(TransactionExcelSpecification.corporate());
            return transactions.findAll(spec,PageRequest.of(0,max+1,Sort.by("id"))).stream().map(t->values("transactionCode",t.getTransactionCode(),
                    "time",t.getCreatedAt(),"type",t.getType(),"amount",t.getAmount(),"corporateChange",financial.get(t.getId()).signedAmount(),
                    "source",t.getReferenceType()+":"+t.getReferenceId(),"description",t.getDescription())).toList();
        });
    }
}
