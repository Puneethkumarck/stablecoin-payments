-- V2: Create role_permissions table
-- Stores individual permissions assigned to each role

CREATE TABLE role_permissions (
    role_permission_id UUID            NOT NULL DEFAULT gen_random_uuid(),
    role_id            UUID            NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission         VARCHAR(50)     NOT NULL,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT role_permissions_pkey PRIMARY KEY (role_permission_id),
    CONSTRAINT role_permissions_unique UNIQUE (role_id, permission)
);

CREATE INDEX role_permissions_role_id_idx ON role_permissions (role_id);
