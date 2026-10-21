package com.mkwang.backend.modules.wallet.service;
import com.mkwang.backend.modules.wallet.entity.*;
import org.springframework.data.jpa.domain.Specification;
public final class TransactionExcelSpecification {
    private TransactionExcelSpecification(){}
    public static Specification<Transaction> corporate(){
        return (root,query,cb)->{
            var exists=query.subquery(Long.class);var line=exists.from(LedgerEntry.class);
            exists.select(line.get("id")).where(cb.equal(line.get("transaction"),root),
                    line.get("wallet").get("ownerType").in(WalletOwnerType.COMPANY_FUND,WalletOwnerType.DEPARTMENT,WalletOwnerType.PROJECT));
            return cb.exists(exists);
        };
    }
}
