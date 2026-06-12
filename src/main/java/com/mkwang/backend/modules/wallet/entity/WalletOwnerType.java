package com.mkwang.backend.modules.wallet.entity;

/**
 * Identifies the type of entity that owns a wallet.
 * Supports the unified wallet model across all financial actors.
 *
 * Special wallets:
 *   COMPANY_FUND — company's residual cash after allocations to departments/projects/payroll.
 *                  This is the authoritative balance tracked by WalletService + LedgerEntry.
 *   FLOAT_MAIN   — system-wide control wallet. Invariant: balance = SUM(all other wallets).
 *                  Updated ONLY when money crosses the system boundary (topup / deposit / withdraw).
 *                  Used exclusively for discrepancy detection — does NOT participate in LedgerEntry.
 */
public enum WalletOwnerType {
  USER,
  DEPARTMENT,
  PROJECT,
  COMPANY_FUND,
  FLOAT_MAIN
}
