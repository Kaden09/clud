CREATE TABLE file_nodes (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  parent_id UUID REFERENCES file_nodes (id),
  node_type VARCHAR(16) NOT NULL,
  name VARCHAR(255) NOT NULL,
  storage_key VARCHAR(512),
  content_type VARCHAR(255),
  size_bytes BIGINT,
  deleted_at TIMESTAMPTZ,
  trash_root BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_file_nodes_type CHECK (node_type IN ('FILE', 'FOLDER')),
  CONSTRAINT ck_file_nodes_name CHECK (length(trim(name)) > 0),
  CONSTRAINT ck_file_nodes_size CHECK (size_bytes IS NULL OR size_bytes >= 0),
  CONSTRAINT ck_file_nodes_metadata CHECK (
    (node_type = 'FOLDER' AND storage_key IS NULL AND content_type IS NULL AND size_bytes IS NULL)
    OR
    (node_type = 'FILE' AND storage_key IS NOT NULL AND content_type IS NOT NULL AND size_bytes IS NOT NULL)
  )
);

CREATE INDEX ix_file_nodes_owner_parent
  ON file_nodes (owner_id, parent_id);

CREATE INDEX ix_file_nodes_trash
  ON file_nodes (owner_id, deleted_at DESC)
  WHERE trash_root = TRUE;

CREATE UNIQUE INDEX ux_file_nodes_active_name
  ON file_nodes (
    owner_id,
    COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::UUID),
    lower(name)
  )
  WHERE deleted_at IS NULL;
