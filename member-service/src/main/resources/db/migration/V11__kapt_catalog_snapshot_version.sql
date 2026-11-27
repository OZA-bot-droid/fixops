-- Monotonic version changes on every successful catalog publication.
create sequence kapt_catalog_snapshot_version_seq;
alter table kapt_catalog_sync
    add column snapshot_version bigint not null default nextval('kapt_catalog_snapshot_version_seq');
