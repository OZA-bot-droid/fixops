-- Only runs when the Docker database volume is first created.
CREATE DATABASE member_db;
CREATE DATABASE asset_db;
CREATE DATABASE workorder_db;
CREATE DATABASE inventory_db;
CREATE DATABASE ai_db;
-- Do NOT install vector across all DBs. Only ai_db needs it.
