package com.mkwang.backend.modules.accounting.entity;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
@Entity @Immutable @Subselect("""
 SELECT t.id,t.transaction_code AS reference_code,CAST(t.created_at AS date) AS candidate_date,
 SUM(CASE WHEN e.direction='CREDIT' THEN e.amount ELSE -e.amount END) AS amount,
 CAST(NULL AS varchar) AS bank_account
 FROM transactions t JOIN ledger_entries e ON e.transaction_id=t.id JOIN wallets w ON w.id=e.wallet_id
 WHERE t.status='SUCCESS' AND w.owner_type IN ('COMPANY_FUND','DEPARTMENT','PROJECT')
 AND NOT EXISTS (SELECT 1 FROM bank_statement_matches m WHERE m.transaction_id=t.id)
 GROUP BY t.id,t.transaction_code,t.created_at
 HAVING SUM(CASE WHEN e.direction='CREDIT' THEN e.amount ELSE -e.amount END)<>0
 """)
public class UnmatchedMovementCandidate extends BankCandidateFields {}
