SELECT version();
SELECT name, version FROM pg_available_extension_versions WHERE name='vector';
SELECT extname, extversion FROM pg_extension WHERE extname='vector';
-- run only in ai_db with sufficient role:
-- CREATE EXTENSION IF NOT EXISTS vector VERSION '0.8.2';
