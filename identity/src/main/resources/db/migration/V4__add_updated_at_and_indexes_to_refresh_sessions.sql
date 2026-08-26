ALTER TABLE refresh_sessions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires_at ON refresh_sessions (expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_revoked ON refresh_sessions (revoked);