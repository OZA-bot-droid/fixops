-- Run on RDS ai_db, ensure availability BEFORE CREATE EXTENSION.
SELECT name, version FROM pg_available_extension_versions WHERE name = 'vector' ORDER BY version;
-- Conditional after verifying availability:
-- CREATE EXTENSION IF NOT EXISTS vector VERSION '0.8.2';
SELECT extname, extversion FROM pg_extension WHERE extname='vector';
