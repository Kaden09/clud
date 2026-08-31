CREATE TABLE share_links (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  file_id UUID NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_share_links_token_hash CHECK (length(token_hash) = 64)
);

CREATE UNIQUE INDEX ux_share_links_token_hash
  ON share_links (token_hash);

CREATE UNIQUE INDEX ux_share_links_active_file
  ON share_links (owner_id, file_id)
  WHERE revoked_at IS NULL;

CREATE INDEX ix_share_links_expiration
  ON share_links (expires_at)
  WHERE revoked_at IS NULL AND expires_at IS NOT NULL;
