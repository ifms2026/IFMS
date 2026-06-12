package com.mkwang.backend.modules.wallet.entity;

/**
 * Identifies the business entity that triggered a Transaction.
 * Used for polymorphic reference: referenceType + referenceId.
 */
public enum ReferenceType {
  REQUEST,          // Transaction from request approval flow (ADVANCE, EXPENSE, REIMBURSE payout)
  PAYSLIP,          // Transaction from payroll (net salary credit, advance deduction)
  PROJECT,          // Fund allocation to a project (Manager approves PROJECT_TOPUP)
  DEPARTMENT,       // Quota allocation to a department (CFO approves DEPARTMENT_TOPUP)
  ADVANCE_BALANCE,  // Cash return or payroll deduction settling an outstanding advance
  SYSTEM,           // System fund top-up or manual adjustment
  WITHDRAWAL,       // referenceId = WithdrawRequest.id
  DEPOSIT           // referenceId = DepositLog.id (future)
}
