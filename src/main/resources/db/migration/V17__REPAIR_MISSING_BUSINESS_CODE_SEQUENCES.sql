-- Repair business-code sequences for databases created before Flyway history
-- was initialized. Existing rows are preserved and sequence values are
-- advanced past the largest numeric suffix already stored.

CREATE SEQUENCE IF NOT EXISTS seq_employee_code
    START WITH 8
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

CREATE SEQUENCE IF NOT EXISTS seq_project_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

CREATE SEQUENCE IF NOT EXISTS seq_phase_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

CREATE SEQUENCE IF NOT EXISTS seq_request_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

SELECT setval(
    'seq_employee_code',
    COALESCE(
        (SELECT MAX(CAST(substring(employee_code FROM '[0-9]+$') AS BIGINT))
         FROM user_profiles
         WHERE employee_code ~ '[0-9]+$'),
        7
    ),
    true
);

SELECT setval(
    'seq_project_code',
    COALESCE(
        (SELECT MAX(CAST(substring(project_code FROM '[0-9]+$') AS BIGINT))
         FROM projects
         WHERE project_code ~ '[0-9]+$'),
        1
    ),
    EXISTS (SELECT 1 FROM projects WHERE project_code ~ '[0-9]+$')
);

SELECT setval(
    'seq_phase_code',
    COALESCE(
        (SELECT MAX(CAST(substring(phase_code FROM '[0-9]+$') AS BIGINT))
         FROM project_phases
         WHERE phase_code ~ '[0-9]+$'),
        1
    ),
    EXISTS (SELECT 1 FROM project_phases WHERE phase_code ~ '[0-9]+$')
);

SELECT setval(
    'seq_request_code',
    COALESCE(
        (SELECT MAX(CAST(substring(request_code FROM '[0-9]+$') AS BIGINT))
         FROM requests
         WHERE request_code ~ '[0-9]+$'),
        1
    ),
    EXISTS (SELECT 1 FROM requests WHERE request_code ~ '[0-9]+$')
);