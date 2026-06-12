-- =============================================================================
-- V2: Add PostgreSQL Sequences for Business Code Generation
-- =============================================================================
-- These sequences are consumed by the Application Layer (BusinessCodeGenerator)
-- via SELECT nextval('sequence_name') before entity persistence.
--
-- Benefits of DB Sequences over application-level counters:
--   1. Atomic & Lock-free (O(1) performance)
--   2. Cluster-safe (shared across multiple JVM instances)
--   3. Persistent (survives JVM restart, unlike AtomicInteger)
--   4. No table scan needed (unlike SELECT COUNT(*)+1 or SELECT MAX(id)+1)
--
-- Sequence naming convention: seq_{entity_type}_code
-- All sequences START WITH 1 and INCREMENT BY 1.
-- CACHE 10 improves performance by pre-allocating 10 values in memory per session.
-- =============================================================================

-- Employee Code Sequence: MK001, MK002, ...
-- Used in: user_profiles.employee_code
-- NOTE: START WITH 8 because DataInitializer seeds MK000 through MK007.
-- New employees created via the system will get MK008, MK009, etc.
CREATE SEQUENCE seq_employee_code
    START WITH 8
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

-- Project Code Sequence: PRJ-ERP-2026-001, PRJ-CRM-2026-002, ...
-- Used in: projects.project_code
CREATE SEQUENCE seq_project_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

-- Phase Code Sequence: PH-UIUX-01, PH-DEV-02, ...
-- Used in: project_phases.phase_code
-- NOTE: This is a global sequence. Phase numbering is sequential across all projects.
-- If per-project numbering is needed, manage it at the application layer.
CREATE SEQUENCE seq_phase_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

-- Request Code Sequence: REQ-IT-0326-001, REQ-FIN-0326-002, ...
-- Used in: requests.request_code
CREATE SEQUENCE seq_request_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;


