CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (username, password, role, upload_status, status)
VALUES ('${admin_name}', crypt('${admin_password}', gen_salt('bf', 12)),
        'ADMIN', 'UNLIMITED', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;