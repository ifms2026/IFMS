package com.mkwang.backend.modules.accounting.entity;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
@Entity @Immutable @Subselect("""
 SELECT s.id,s.bank_reference AS reference_code,COALESCE(c.booking_date,s.booking_date) AS candidate_date,
 s.amount,s.bank_account FROM bank_statement_lines s
 LEFT JOIN bank_booking_confirmations c ON c.statement_line_id=s.id
 WHERE s.direction='IN' AND (s.state='BOOKED' OR c.statement_line_id IS NOT NULL)
 AND NOT EXISTS (SELECT 1 FROM bank_statement_matches m WHERE m.statement_line_id=s.id)
 """)
public class UnusedTopupCandidate extends BankCandidateFields {}
