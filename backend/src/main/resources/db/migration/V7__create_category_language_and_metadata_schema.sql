-- ===================================================================
-- CommunityOTT Phase 5.2 Schema Migration (Flyway V7__create_category_language_and_metadata_schema.sql)
-- Introduces:
-- 1. categories table
-- 2. languages table
-- 3. content table metadata enhancements (subtitle, short_description, country_of_origin, original_language_id, tags)
-- 4. content_categories join table
-- 5. content_languages join table
-- 6. Seeds initial standard categories and languages
-- 7. Seeds RBAC permissions for category, language, and content metadata management
-- ===================================================================

-- 1. Categories Table
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_active ON categories(active);

-- 2. Languages Table
CREATE TABLE languages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(20) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_languages_code ON languages(code);
CREATE INDEX idx_languages_active ON languages(active);

-- 3. Content Table Enhancements
ALTER TABLE content
    ADD COLUMN subtitle VARCHAR(255),
    ADD COLUMN short_description VARCHAR(500),
    ADD COLUMN country_of_origin VARCHAR(100),
    ADD COLUMN original_language_id BIGINT REFERENCES languages(id) ON DELETE SET NULL,
    ADD COLUMN tags VARCHAR(500);

CREATE INDEX idx_content_original_language ON content(original_language_id);

-- 4. Content Categories Join Table (Explicit Many-to-Many)
CREATE TABLE content_categories (
    content_id BIGINT NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id, category_id)
);

CREATE INDEX idx_content_categories_content_id ON content_categories(content_id);
CREATE INDEX idx_content_categories_category_id ON content_categories(category_id);

-- 5. Content Languages Join Table (Available Audio/Subtitled Languages)
CREATE TABLE content_languages (
    content_id BIGINT NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    language_id BIGINT NOT NULL REFERENCES languages(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id, language_id)
);

CREATE INDEX idx_content_languages_content_id ON content_languages(content_id);
CREATE INDEX idx_content_languages_language_id ON content_languages(language_id);

-- 6. Seed Initial Standard Categories & Languages
INSERT INTO categories (name, slug, description, active)
VALUES 
    ('Documentary', 'documentary', 'In-depth real world non-fiction stories and investigative chronicles', true),
    ('History', 'history', 'Historical explorations, heritage archives, and cultural legacy', true),
    ('Culture', 'culture', 'Traditions, indigenous customs, folk arts, and community celebrations', true),
    ('Science', 'science', 'Scientific breakthroughs, astronomy, physics, and empirical research', true),
    ('Technology', 'technology', 'Engineering, software, robotics, and futuristic innovations', true),
    ('Nature & Wildlife', 'nature-wildlife', 'Flora, fauna, ecosystems, conservation, and planetary habitats', true),
    ('Cinema & Drama', 'cinema-drama', 'Narrative storytelling, theatrical features, and cinematic masterpieces', true)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO languages (name, code, active)
VALUES
    ('Telugu', 'te', true),
    ('English', 'en', true),
    ('Hindi', 'hi', true),
    ('Tamil', 'ta', true),
    ('Kannada', 'kn', true),
    ('Malayalam', 'ml', true)
ON CONFLICT (code) DO NOTHING;

-- 7. Seed RBAC Permissions for Category, Language, and Content Metadata Management
INSERT INTO permissions (name, description, module, action)
VALUES
    ('CATEGORY_VIEW', 'View categories list and taxonomy details', 'category', 'view'),
    ('CATEGORY_CREATE', 'Create new content categories', 'category', 'create'),
    ('CATEGORY_UPDATE', 'Update existing category details', 'category', 'update'),
    ('CATEGORY_DELETE', 'Deactivate or delete content categories', 'category', 'delete'),

    ('LANGUAGE_VIEW', 'View available platform languages', 'language', 'view'),
    ('LANGUAGE_CREATE', 'Create new language entries', 'language', 'create'),
    ('LANGUAGE_UPDATE', 'Update existing language details', 'language', 'update'),
    ('LANGUAGE_DELETE', 'Deactivate or delete languages', 'language', 'delete'),

    ('CONTENT_METADATA_UPDATE', 'Update content taxonomy, category, and language metadata', 'content', 'update_metadata')
ON CONFLICT (name) DO NOTHING;

-- Assign new permissions to SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN' AND p.name IN (
    'CATEGORY_VIEW', 'CATEGORY_CREATE', 'CATEGORY_UPDATE', 'CATEGORY_DELETE',
    'LANGUAGE_VIEW', 'LANGUAGE_CREATE', 'LANGUAGE_UPDATE', 'LANGUAGE_DELETE',
    'CONTENT_METADATA_UPDATE'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Assign new permissions to CONTENT_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'CATEGORY_VIEW', 'CATEGORY_CREATE', 'CATEGORY_UPDATE', 'CATEGORY_DELETE',
    'LANGUAGE_VIEW', 'LANGUAGE_CREATE', 'LANGUAGE_UPDATE', 'LANGUAGE_DELETE',
    'CONTENT_METADATA_UPDATE'
)
WHERE r.name = 'CONTENT_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Assign view permissions to MANAGER and USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('CATEGORY_VIEW', 'LANGUAGE_VIEW')
WHERE r.name IN ('MANAGER', 'USER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
