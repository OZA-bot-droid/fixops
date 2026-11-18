-- This runs once when PostgreSQL data directory is first initialized.
CREATE DATABASE member_db;
CREATE DATABASE asset_db;
CREATE DATABASE workorder_db;
CREATE DATABASE inventory_db;
CREATE DATABASE ai_db;
-- Separate least-privilege database roles are required before remote deployment.
-- ai_db pgvector 0.8.2 is NOT installed by the plain postgres:16 image.
