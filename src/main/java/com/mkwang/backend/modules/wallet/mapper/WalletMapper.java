package com.mkwang.backend.modules.wallet.mapper;

import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerEntryResponse;
import com.mkwang.backend.modules.wallet.dto.response.AccountantLedgerItemResponse;
import com.mkwang.backend.modules.wallet.dto.response.LedgerEntryResponse;
import com.mkwang.backend.modules.wallet.dto.response.TransactionResponse;
import com.mkwang.backend.modules.wallet.dto.response.WalletResponse;
import com.mkwang.backend.modules.wallet.entity.LedgerEntry;
import com.mkwang.backend.modules.wallet.entity.Transaction;
import com.mkwang.backend.modules.wallet.entity.Wallet;
import com.mkwang.backend.modules.wallet.entity.WalletOwnerType;
import com.mkwang.backend.modules.user.repository.UserRepository;
import com.mkwang.backend.modules.organization.repository.DepartmentRepository;
import com.mkwang.backend.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletMapper {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;

    public WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .ownerType(wallet.getOwnerType())
                .ownerId(wallet.getOwnerId())
                .balance(wallet.getBalance())
                .lockedBalance(wallet.getLockedBalance())
                .availableBalance(wallet.getAvailableBalance())
                .build();
    }

    public TransactionResponse toTransactionResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .transactionCode(txn.getTransactionCode())
                .amount(txn.getAmount())
                .type(txn.getType())
                .status(txn.getStatus())
                .referenceType(txn.getReferenceType())
                .referenceId(txn.getReferenceId())
                .description(txn.getDescription())
                .createdAt(txn.getCreatedAt())
                .build();
    }

    public LedgerEntryResponse toLedgerEntryResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .transactionId(entry.getTransaction().getId())
                .transactionCode(entry.getTransaction().getTransactionCode())
                .direction(entry.getDirection())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    public AccountantLedgerEntryResponse toAccountantLedgerEntryResponse(LedgerEntry entry) {
        return new AccountantLedgerEntryResponse(
                entry.getId(),
                entry.getTransaction().getTransactionCode(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getWallet().getOwnerType(),
                entry.getWallet().getOwnerId(),
                resolveWalletOwnerName(entry.getWallet().getOwnerType(), entry.getWallet().getOwnerId()),
                entry.getCreatedAt()
        );
    }

    public AccountantLedgerItemResponse toAccountantLedgerItemResponse(LedgerEntry entry) {
        return new AccountantLedgerItemResponse(
                entry.getId(),
                entry.getTransaction().getTransactionCode(),
                entry.getTransaction().getType(),
                entry.getTransaction().getStatus(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getWallet().getOwnerType(),
                entry.getWallet().getOwnerId(),
                resolveWalletOwnerName(entry.getWallet().getOwnerType(), entry.getWallet().getOwnerId()),
                entry.getCreatedAt()
        );
    }

    public String resolveWalletOwnerName(WalletOwnerType ownerType, Long ownerId) {
        if (ownerType == null) {
            return null;
        }
        return switch (ownerType) {
            case USER -> ownerId == null
                    ? null
                    : userRepository.findById(ownerId).map(user -> user.getFullName()).orElse(null);
            case DEPARTMENT -> ownerId == null
                    ? null
                    : departmentRepository.findById(ownerId).map(department -> department.getName()).orElse(null);
            case PROJECT -> ownerId == null
                    ? null
                    : projectRepository.findById(ownerId).map(project -> project.getName()).orElse(null);
            case COMPANY_FUND -> "Quỹ hệ thống";
            case FLOAT_MAIN -> "Ví kiểm soát hệ thống";
        };
    }
}
