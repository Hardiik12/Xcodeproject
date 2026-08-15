-- ===================================================================
-- CommunityOTT Initial Database Migration (Flyway V1__init.sql)
-- Infrastructure Verification Table (Foundation Phase 2)
-- DO NOT ADD RBAC OR USER TABLES IN THIS PHASE
-- ===================================================================

CREATE TABLE IF NOT EXISTS system_info (
    id VARCHAR(36) PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_info (id, service_name, version, environment)
VALUES ('sys-001', 'communityott-backend', '1.0.0', 'local')
ON CONFLICT (id) DO NOTHING;
