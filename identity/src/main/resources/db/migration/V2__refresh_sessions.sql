CREATE TABLE refresh_sessions
(
    id         UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)              NOT NULL,
    jti        VARCHAR(36)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked    BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_refresh_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_sessions_user_id ON refresh_sessions (user_id);
CREATE INDEX idx_refresh_sessions_token_hash ON refresh_sessions (token_hash);