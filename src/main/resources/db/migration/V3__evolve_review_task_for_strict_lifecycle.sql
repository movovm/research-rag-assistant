ALTER TABLE sm_review_task
    MODIFY COLUMN review_goal VARCHAR(2000) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN failure_code VARCHAR(64) NULL AFTER report_json,
    ADD COLUMN failure_reason VARCHAR(500) NULL AFTER failure_code;
