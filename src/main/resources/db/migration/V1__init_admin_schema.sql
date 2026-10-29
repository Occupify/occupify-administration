-- V1__init_admin_schema.sql
-- Administration and Audit Service Schema Initialization

-- 1. Users (Local Replicated Entity from occupify-identity)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    avatar_url VARCHAR(500),
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- 2. Projects (Local Replicated Entity from occupify-core)
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    employer_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP
);

-- 3. Contracts (Local Replicated Entity from occupify-core)
CREATE TABLE IF NOT EXISTS contracts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    employer_id UUID NOT NULL REFERENCES users(id),
    employee_id UUID REFERENCES users(id),
    source_type VARCHAR(50),
    source_id UUID,
    agreed_rate DECIMAL(15, 2),
    salary_type VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50),
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

-- 4. Cancellation Contracts
CREATE TABLE IF NOT EXISTS cancellation_contracts (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL REFERENCES contracts(id),
    requested_by_user_id UUID NOT NULL REFERENCES users(id),
    reason TEXT,
    status VARCHAR(50) NOT NULL
);

-- 5. Reports (Native Domain Entity)
CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY,
    reporter_user_id UUID NOT NULL REFERENCES users(id),
    target_type VARCHAR(50) NOT NULL,
    target_user_id UUID REFERENCES users(id),
    target_project_id UUID REFERENCES projects(id),
    reason_category VARCHAR(50) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Report Evidences (Native Domain Entity)
CREATE TABLE IF NOT EXISTS report_evidences (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    file_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Flagged Accounts (Native Domain Entity)
CREATE TABLE IF NOT EXISTS flagged_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    admin_id UUID REFERENCES users(id),
    flag_reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    action_taken VARCHAR(50) NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

-- 8. System Audit Logs (Native Domain Entity)
CREATE TABLE IF NOT EXISTS system_audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users(id),
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_service VARCHAR(50) NOT NULL,
    target_entity VARCHAR(100) NOT NULL,
    target_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_projects_employer_id ON projects(employer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_employer_id ON contracts(employer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_employee_id ON contracts(employee_id);
CREATE INDEX IF NOT EXISTS idx_cancellation_contract_id ON cancellation_contracts(contract_id);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_user_id ON reports(reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_reports_target_user_id ON reports(target_user_id);
CREATE INDEX IF NOT EXISTS idx_reports_target_project_id ON reports(target_project_id);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status);
CREATE INDEX IF NOT EXISTS idx_report_evidences_report_id ON report_evidences(report_id);
CREATE INDEX IF NOT EXISTS idx_flagged_accounts_user_id ON flagged_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_system_audit_logs_actor_id ON system_audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_system_audit_logs_created_at ON system_audit_logs(created_at);
