# P2 K-apt catalog

The search catalog and official registration records remain separate. V10 creates the catalog and province readiness table. Search uses PostgreSQL only for a province synchronized within seven days; otherwise it uses the existing live API path. The detail and registration paths always perform official lookup.

Enable on one designated member instance via KAPT_CATALOG_SYNC_ENABLED=true after checking API quota. Every five minutes the scheduler attempts one province that has not been refreshed within 24 hours. It checks a Redis job gate and upstream request limit (100 per minute), and publishes only a complete validated nonempty snapshot in a transaction. Failed imports leave the prior data intact. The current public API page size is assumed to support 100 rows. Upstream region codes 51/52 are current legal codes; confirm provider support in deployment.

Inspect progress in member_db:
```sql
select province_code, item_count, synced_at from kapt_catalog_sync order by province_code;
select count(*) from kapt_complex_catalog;
```

If API quota or pagination assumptions fail, disable synchronization and inspect member logs. Existing searches fall back to live API for unsynchronized/stale provinces. No nationwide name-only endpoint yet; P4 requires complete coverage. The job is intentionally off by default.

## P3 search cache

V11 assigns a monotonic snapshot version on each successful province publication. Cache keys use the province snapshot version, region, SHA-256 of the normalized query, page and size. Positive results expire after five minutes and empty pages after one minute. Older keys expire naturally; no KEYS, SCAN or FLUSH operations are required. Only fresh PostgreSQL snapshots are cached; live API fallback and authoritative detail/registration are not cached. Redis cache read/write failure is best effort and falls back to SQL, but existing authentication and rate-limit Redis dependencies still fail closed. DB readiness/version and the shared advisory lock are checked on every search, including cache hits. No new Redis service or environment variable is required.

## P4 nationwide search

GET /api/complexes/search accepts omitted or empty regionCode when normalized q has at least two characters. All 17 expected province snapshots must be fresh (within seven days), otherwise returns DEPENDENCY_UNAVAILABLE (503); no live nationwide fan-out or partial result is returned. GET /api/complexes/catalog/status exposes readyCount, totalCount, nationwideReady, missingProvinces. The nationwide cache version is an ordered vector of all province snapshot versions, invalidating results on any successful province refresh. Existing regional searches and official detail/registration remain unchanged. No additional migration or environment variable is required. P1 selector data is independent of the catalog; the full official legal-dong JSON must still be generated if not already present.
