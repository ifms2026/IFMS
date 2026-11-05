package com.mkwang.backend.accounting;

import com.mkwang.backend.modules.accounting.entity.PayslipStatus;
import com.mkwang.backend.modules.notification.entity.NotificationType;
import com.mkwang.backend.modules.request.entity.RequestAction;
import com.mkwang.backend.modules.request.entity.RequestStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LedgerLegacyMigrationIntegrationTest {
    @Test void upgradeRetainsEnumChecksAndAcceptsNewFinancialLifecycle() throws Exception {
        String url=System.getenv("IFMS_TEST_DATABASE_URL");
        Assumptions.assumeTrue(url!=null && url.contains("ledger_review"),"Requires disposable ledger_review DB");
        String user=System.getenv("IFMS_TEST_DATABASE_USER"),password=System.getenv("IFMS_TEST_DATABASE_PASSWORD");
        String schema="legacy_ledger_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(url,user,password)) {
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                    .target("34").load().migrate();
            connection.setSchema(schema);
            // The V16 baseline in existing installations retained these Hibernate checks.
            connection.createStatement().execute("ALTER TABLE requests ADD CONSTRAINT requests_status_check CHECK(status IN ('PENDING','APPROVED_BY_TEAM_LEADER','APPROVED_BY_MANAGER','APPROVED_BY_CFO','PAID','REJECTED','CANCELLED'))");
            connection.createStatement().execute("ALTER TABLE request_histories ADD CONSTRAINT request_histories_action_check CHECK(action IN ('APPROVE','REJECT','PAYOUT','CANCEL'))");
            connection.createStatement().execute("ALTER TABLE request_histories ADD CONSTRAINT request_histories_status_after_action_check CHECK(status_after_action IN ('PENDING','APPROVED_BY_TEAM_LEADER','APPROVED_BY_MANAGER','APPROVED_BY_CFO','PAID','REJECTED','CANCELLED'))");
            connection.createStatement().execute("ALTER TABLE payslips ADD CONSTRAINT payslips_status_check CHECK(status IN ('DRAFT','PAID'))");
            connection.createStatement().execute("ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK(type IN ('REQUEST_SUBMITTED','REQUEST_APPROVED_BY_TL','REQUEST_REJECTED','REQUEST_PAID','PROJECT_TOPUP_APPROVED','PROJECT_TOPUP_REJECTED','DEPT_TOPUP_APPROVED','DEPT_TOPUP_REJECTED','SALARY_PAID','SYSTEM','SECURITY_ALERT'))");
            assertFalse(accepts(connection,"requests","requests_status_check","status","ACCOUNTANT_VERIFIED"));
            assertFalse(accepts(connection,"request_histories","request_histories_action_check","action","VERIFY"));
            assertFalse(accepts(connection,"notifications","notifications_type_check","type","EXPENSE_VERIFIED"));
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
            for(var status:RequestStatus.values()) {
                assertTrue(accepts(connection,"requests","requests_status_check","status",status.name()));
                assertTrue(accepts(connection,"request_histories","request_histories_status_after_action_check","status_after_action",status.name()));
            }
            for(var action:RequestAction.values()) assertTrue(accepts(connection,"request_histories","request_histories_action_check","action",action.name()));
            for(var status:PayslipStatus.values()) assertTrue(accepts(connection,"payslips","payslips_status_check","status",status.name()));
            for(var type:NotificationType.values()) assertTrue(accepts(connection,"notifications","notifications_type_check","type",type.name()));
            assertFalse(accepts(connection,"requests","requests_status_check","status","INVALID_STATUS"));
            assertFalse(accepts(connection,"request_histories","request_histories_action_check","action","INVALID_ACTION"));
            assertFalse(accepts(connection,"payslips","payslips_status_check","status","INVALID_STATUS"));
            assertFalse(accepts(connection,"notifications","notifications_type_check","type","INVALID_TYPE"));
        } finally {
            try(var connection=DriverManager.getConnection(url,user,password)) {
                connection.createStatement().execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
            }
        }
    }
    private boolean accepts(Connection c,String table,String constraint,String column,String value) throws SQLException {
        String expression;
        try(var query=c.prepareStatement("SELECT pg_get_expr(conbin,conrelid) FROM pg_constraint WHERE conrelid=?::regclass AND conname=?")) {
            query.setString(1,table);query.setString(2,constraint);
            try(var rows=query.executeQuery()){assertTrue(rows.next());expression=rows.getString(1);}
        }
        try(var query=c.prepareStatement("SELECT "+expression+" FROM (SELECT CAST(? AS varchar) AS "+column+") candidate")) {
            query.setString(1,value);try(var rows=query.executeQuery()){rows.next();return rows.getBoolean(1);}
        }
    }
}
