package com.mkwang.backend.accounting;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only; URL must identify a disposable, isolated test database. */
class JournalDatabaseIntegrationTest {
    private static Connection connection;
    @BeforeAll static void migrate() throws Exception {
        String url = System.getenv("IFMS_TEST_DATABASE_URL");
        Assumptions.assumeTrue(url != null && url.contains("ledger_review"), "Requires isolated ledger_review database");
        String user = System.getenv("IFMS_TEST_DATABASE_USER");
        String password = System.getenv("IFMS_TEST_DATABASE_PASSWORD");
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
        connection = DriverManager.getConnection(url, user, password);
    }
    @AfterAll static void close() throws Exception { if (connection != null) connection.close(); }
    @AfterEach void rollback() throws Exception { connection.rollback(); connection.setAutoCommit(true); }
    private long header(String suffix) throws Exception {
        try (var ps = connection.prepareStatement("""
                INSERT INTO accounting_journals(journal_code,event_type,posting_date,posting_period,description,source_type,source_id,total_amount)
                VALUES (?, 'EXPENSE_VERIFIED', DATE '2090-01-15','2090-01','Integration fixture','TEST',?,100) RETURNING id
                """)) {
            ps.setString(1, "TEST-" + suffix + "-" + System.nanoTime()); ps.setString(2, suffix + System.nanoTime());
            var rs = ps.executeQuery(); rs.next(); return rs.getLong(1);
        }
    }
    private void line(long id, int n, int debit, int credit) throws Exception {
        try (var ps = connection.prepareStatement("""
                INSERT INTO accounting_journal_lines(journal_id,line_number,account_code,account_name,debit_amount,credit_amount,effect_description)
                VALUES (?,?,'PROJECT_EXPENSE','Test',?,?,'Fixture')
                """)) {
            ps.setLong(1,id); ps.setInt(2,n); ps.setInt(3,debit); ps.setInt(4,credit); ps.executeUpdate();
        }
    }
    @Test void balancedJournalCanCommitAndIsSealed() throws Exception {
        connection.setAutoCommit(false);
        long id = header("balanced"); line(id,1,100,0); line(id,2,0,100); connection.commit();
        assertThrows(SQLException.class, () -> line(id,3,1,0));
        connection.rollback();
        assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("UPDATE accounting_journals SET description='Changed' WHERE id="+id));
        connection.rollback();
        assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("DELETE FROM accounting_journal_lines WHERE journal_id="+id));
    }
    @Test void incompleteJournalCannotCommit() throws Exception {
        connection.setAutoCommit(false); header("missing-lines");
        assertThrows(SQLException.class, () -> connection.commit());
    }
    @Test void unequalSidesCannotCommit() throws Exception {
        connection.setAutoCommit(false); long id = header("unbalanced");
        line(id,1,100,0); line(id,2,0,99); assertThrows(SQLException.class, () -> connection.commit());
    }
    @Test void headerTotalMustEqualBothSides() throws Exception {
        connection.setAutoCommit(false); long id = header("wrong-total");
        line(id,1,90,0); line(id,2,0,90); assertThrows(SQLException.class, () -> connection.commit());
    }
    @Test void closedPeriodCannotReceiveJournal() throws Exception {
        connection.setAutoCommit(false);
        connection.createStatement().executeUpdate("INSERT INTO accounting_periods(period,closed) VALUES ('2090-01',true)");
        assertThrows(SQLException.class, () -> header("closed-period"));
    }
}
