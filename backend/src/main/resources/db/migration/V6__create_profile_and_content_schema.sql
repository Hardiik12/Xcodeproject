-- ===================================================================
-- CommunityOTT Profile & Content Catalog Schema Migration (Flyway V6)
-- Creates tables and indexes for OTT user profiles and content catalog
-- ===================================================================

-- 1. Profiles Table
CREATE TABLE IF NOT EXISTS profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    preferred_language VARCHAR(20) NOT NULL DEFAULT 'en',
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_profiles_user_default ON profiles(user_id, is_default);

-- 2. Content Table
CREATE TABLE IF NOT EXISTS content (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    content_type VARCHAR(50) NOT NULL,
    release_date DATE,
    duration_seconds INTEGER,
    age_rating VARCHAR(20) NOT NULL DEFAULT 'U',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    thumbnail_url VARCHAR(500),
    banner_url VARCHAR(500),
    is_featured BOOLEAN NOT NULL DEFAULT false,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Content Indexes for high-performance catalog queries
CREATE INDEX IF NOT EXISTS idx_content_status ON content(status);
CREATE INDEX IF NOT EXISTS idx_content_content_type ON content(content_type);
CREATE INDEX IF NOT EXISTS idx_content_release_date ON content(release_date);
CREATE INDEX IF NOT EXISTS idx_content_created_at ON content(created_at);
CREATE INDEX IF NOT EXISTS idx_content_status_created_at ON content(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_status_content_type ON content(status, content_type, created_at DESC);
