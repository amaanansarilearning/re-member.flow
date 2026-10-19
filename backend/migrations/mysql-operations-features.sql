-- Apply once to existing MySQL installations before deploying with ddl-auto=validate.
ALTER TABLE user_accounts MODIFY COLUMN role VARCHAR(32) NOT NULL;
ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS member_pin VARCHAR(8) NULL;
ALTER TABLE pt_sessions ADD COLUMN IF NOT EXISTS verification_method VARCHAR(32) NULL;

ALTER TABLE memberships
    ADD COLUMN plan_name_snapshot VARCHAR(80) NULL,
    ADD COLUMN plan_description_snapshot VARCHAR(1000) NULL,
    ADD COLUMN plan_duration_months_snapshot INT NULL,
    ADD COLUMN plan_duration_days_snapshot INT NULL,
    ADD COLUMN cancelled_at DATETIME(6) NULL,
    ADD INDEX idx_memberships_created_at (created_at),
    ADD INDEX idx_memberships_cancelled_at (cancelled_at);

UPDATE memberships m
JOIN membership_plans p ON p.id = m.plan_id
SET m.plan_name_snapshot = p.name,
    m.plan_description_snapshot = p.description,
    m.plan_duration_months_snapshot = p.duration_months,
    m.plan_duration_days_snapshot = p.duration_days
WHERE m.plan_name_snapshot IS NULL;

CREATE TABLE action_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    actor_email VARCHAR(254) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    before_data TEXT NULL,
    after_data TEXT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_action_history_org_time (organization_id, occurred_at)
);

CREATE TABLE membership_freezes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    membership_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_by VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_membership_freezes_membership (membership_id),
    CONSTRAINT fk_membership_freezes_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id)
);

CREATE TABLE renewal_reminders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    membership_id BIGINT NOT NULL,
    days_before INT NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    last_attempt_at DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_renewal_reminder_membership_notice_recipient
        (membership_id, days_before, recipient_email),
    KEY idx_renewal_reminders_status_retry (status, next_attempt_at),
    CONSTRAINT fk_renewal_reminders_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id)
);

-- SaaS Tier Expansion & Custom Branding
ALTER TABLE action_history MODIFY COLUMN before_data LONGTEXT NULL;
ALTER TABLE action_history MODIFY COLUMN after_data LONGTEXT NULL;
ALTER TABLE gym_organizations ADD COLUMN IF NOT EXISTS logo_data LONGTEXT NULL;
ALTER TABLE gym_organizations MODIFY COLUMN logo_data LONGTEXT NULL;
ALTER TABLE memberships ADD COLUMN IF NOT EXISTS payment_method VARCHAR(40) NULL;
ALTER TABLE memberships ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(120) NULL;

CREATE TABLE IF NOT EXISTS membership_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    membership_id BIGINT NOT NULL UNIQUE,
    receipt_image LONGBLOB NOT NULL,
    receipt_image_content_type VARCHAR(32) NOT NULL,
    receipt_image_filename VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_membership_receipts_membership FOREIGN KEY (membership_id) REFERENCES memberships (id)
);
ALTER TABLE membership_receipts MODIFY COLUMN receipt_image LONGBLOB NOT NULL;


