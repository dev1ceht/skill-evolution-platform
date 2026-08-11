CREATE TABLE schools (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE canteens (
    id VARCHAR(64) PRIMARY KEY,
    school_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_canteen_school FOREIGN KEY (school_id) REFERENCES schools(id)
);

CREATE INDEX idx_canteens_school ON canteens(school_id);

CREATE TABLE ledger_cycles (
    id VARCHAR(64) NOT NULL,
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, id),
    CONSTRAINT fk_ledger_cycle_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_ledger_cycle_canteen FOREIGN KEY (canteen_id) REFERENCES canteens(id),
    CONSTRAINT ck_ledger_cycle_dates CHECK (period_end >= period_start),
    CONSTRAINT ck_ledger_cycle_status CHECK (status IN ('OPEN', 'CLEARED'))
);

CREATE INDEX idx_ledger_cycles_scope
    ON ledger_cycles(school_id, canteen_id, period_start, period_end, id);

CREATE TABLE ledger_cycle_requirements (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    cycle_id VARCHAR(64) NOT NULL,
    ledger_code VARCHAR(64) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (school_id, canteen_id, cycle_id, ledger_code),
    CONSTRAINT fk_cycle_requirement_cycle
        FOREIGN KEY (school_id, canteen_id, cycle_id)
        REFERENCES ledger_cycles(school_id, canteen_id, id)
);

CREATE TABLE ledger_alerts (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    cycle_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cleared_at TIMESTAMP NULL,
    PRIMARY KEY (school_id, canteen_id, cycle_id),
    CONSTRAINT fk_ledger_alert_cycle
        FOREIGN KEY (school_id, canteen_id, cycle_id)
        REFERENCES ledger_cycles(school_id, canteen_id, id),
    CONSTRAINT ck_ledger_alert_status CHECK (status IN ('OPEN', 'CLEARED'))
);

INSERT INTO schools (id, name)
VALUES ('SCHOOL-001', '示例学校');

INSERT INTO canteens (id, school_id, name)
VALUES ('CANTEEN-001', 'SCHOOL-001', '示例食堂');

INSERT INTO ledger_cycles (
    id, school_id, canteen_id, period_start, period_end, status, version
)
VALUES (
    'CYCLE-001', 'SCHOOL-001', 'CANTEEN-001', CURRENT_DATE, CURRENT_DATE, 'OPEN', 0
);

INSERT INTO ledger_cycle_requirements (
    school_id, canteen_id, cycle_id, ledger_code, completed
)
SELECT 'SCHOOL-001', 'CANTEEN-001', 'CYCLE-001', ledger_code, completed
FROM ledger_requirements;

UPDATE ledger_cycles
SET status = CASE WHEN EXISTS (
    SELECT 1 FROM ledger_cycle_requirements
    WHERE school_id = 'SCHOOL-001'
      AND canteen_id = 'CANTEEN-001'
      AND cycle_id = 'CYCLE-001'
      AND completed = FALSE
) THEN 'OPEN' ELSE 'CLEARED' END
WHERE school_id = 'SCHOOL-001'
  AND canteen_id = 'CANTEEN-001'
  AND id = 'CYCLE-001';

INSERT INTO ledger_alerts (school_id, canteen_id, cycle_id, status, created_at)
SELECT
    'SCHOOL-001',
    'CANTEEN-001',
    'CYCLE-001',
    CASE WHEN EXISTS (
        SELECT 1 FROM ledger_cycle_requirements
        WHERE cycle_id = 'CYCLE-001' AND completed = FALSE
    ) THEN 'OPEN' ELSE 'CLEARED' END,
    CURRENT_TIMESTAMP;
