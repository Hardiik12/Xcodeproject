-- ===================================================================
-- CommunityOTT Seed Default RBAC Data Migration (Flyway V3__seed_rbac_data.sql)
-- Seeds system roles, system permissions, and initial role-permission mappings
-- ===================================================================

-- 1. Seed Roles
INSERT INTO roles (name, description, is_system_role)
VALUES 
    ('SUPER_ADMIN', 'Full system control with unrestricted administrative privileges', true),
    ('MANAGER', 'Platform management with administrative, user, and analytical oversight', true),
    ('CONTENT_MANAGER', 'Content creation, editing, asset upload, and pipeline management', true),
    ('USER', 'Standard end-user subscriber with streaming and catalogue viewing access', true)
ON CONFLICT (name) DO NOTHING;

-- 2. Seed Permissions
INSERT INTO permissions (name, description, module, action)
VALUES
    -- User Management Permissions
    ('USER_VIEW', 'View user profiles and details', 'user', 'view'),
    ('USER_CREATE', 'Create new user accounts', 'user', 'create'),
    ('USER_UPDATE', 'Update user profile information', 'user', 'update'),
    ('USER_SUSPEND', 'Suspend user access', 'user', 'suspend'),
    ('USER_DELETE', 'Delete user accounts', 'user', 'delete'),

    -- Role Management Permissions
    ('ROLE_VIEW', 'View roles and assigned permissions', 'role', 'view'),
    ('ROLE_CREATE', 'Create custom roles', 'role', 'create'),
    ('ROLE_UPDATE', 'Update custom roles', 'role', 'update'),
    ('ROLE_DELETE', 'Delete custom roles', 'role', 'delete'),
    ('ROLE_ASSIGN', 'Assign roles to users', 'role', 'assign'),

    -- Permission Management Permissions
    ('PERMISSION_VIEW', 'View system permissions', 'permission', 'view'),
    ('PERMISSION_ASSIGN', 'Assign permissions to roles', 'permission', 'assign'),

    -- Content Catalogue Permissions
    ('CONTENT_VIEW', 'View content catalogue', 'content', 'view'),
    ('CONTENT_CREATE', 'Create content items', 'content', 'create'),
    ('CONTENT_UPDATE', 'Update content items', 'content', 'update'),
    ('CONTENT_DELETE', 'Delete content items', 'content', 'delete'),
    ('CONTENT_SUBMIT', 'Submit content for editorial review', 'content', 'submit'),
    ('CONTENT_PUBLISH', 'Publish content items', 'content', 'publish'),
    ('CONTENT_ARCHIVE', 'Archive content items', 'content', 'archive'),

    -- Video Stream Permissions
    ('VIDEO_UPLOAD', 'Upload video streams', 'video', 'upload'),
    ('VIDEO_VIEW', 'View and play video streams', 'video', 'view'),
    ('VIDEO_EDIT', 'Edit video metadata', 'video', 'edit'),
    ('VIDEO_DELETE', 'Delete video streams', 'video', 'delete'),
    ('VIDEO_PROCESS', 'Process and encode video streams', 'video', 'process'),
    ('VIDEO_RETRY', 'Retry failed video processing tasks', 'video', 'retry'),
    ('VIDEO_PUBLISH', 'Publish video streams', 'video', 'publish'),

    -- Analytics Permissions
    ('ANALYTICS_VIEW', 'View analytics dashboards', 'analytics', 'view'),
    ('ANALYTICS_EXPORT', 'Export analytics reports', 'analytics', 'export'),

    -- Notification Permissions
    ('NOTIFICATION_VIEW', 'View system notifications', 'notification', 'view'),
    ('NOTIFICATION_SEND', 'Send system notifications', 'notification', 'send'),

    -- Audit Logging Permissions
    ('AUDIT_VIEW', 'View system audit logs', 'audit', 'view'),
    ('AUDIT_EXPORT', 'Export audit logs', 'audit', 'export'),

    -- System Configuration Permissions
    ('SYSTEM_SETTINGS_VIEW', 'View system settings', 'system', 'view_settings'),
    ('SYSTEM_SETTINGS_UPDATE', 'Update system settings', 'system', 'update_settings'),
    ('SYSTEM_HEALTH_VIEW', 'View system health metrics', 'system', 'view_health')
ON CONFLICT (name) DO NOTHING;

-- 3. Seed Role-Permission Mappings

-- SUPER_ADMIN: All permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MANAGER: Selected administrative & oversight permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'USER_VIEW',
    'CONTENT_VIEW',
    'ANALYTICS_VIEW',
    'ANALYTICS_EXPORT',
    'NOTIFICATION_VIEW',
    'NOTIFICATION_SEND',
    'AUDIT_VIEW',
    'SYSTEM_HEALTH_VIEW'
)
WHERE r.name = 'MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- CONTENT_MANAGER: Content & Video management permissions (excluding publish permissions)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'CONTENT_VIEW',
    'CONTENT_CREATE',
    'CONTENT_UPDATE',
    'CONTENT_SUBMIT',
    'VIDEO_UPLOAD',
    'VIDEO_VIEW',
    'VIDEO_EDIT',
    'VIDEO_PROCESS',
    'VIDEO_RETRY'
)
WHERE r.name = 'CONTENT_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- USER: Standard content & video viewing permissions only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'CONTENT_VIEW',
    'VIDEO_VIEW'
)
WHERE r.name = 'USER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
