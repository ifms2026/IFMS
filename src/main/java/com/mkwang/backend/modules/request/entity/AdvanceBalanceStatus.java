package com.mkwang.backend.modules.request.entity;

/**
 * Lifecycle status of an advance balance record.
 *
 * OUTSTANDING       : no reimbursement or return has occurred yet
 * PARTIALLY_SETTLED : some amount has been reimbursed/returned, remainder still owed
 * SETTLED           : remaining_amount = 0, advance fully accounted for
 */
public enum AdvanceBalanceStatus {
  OUTSTANDING,
  PARTIALLY_SETTLED,
  SETTLED
}
