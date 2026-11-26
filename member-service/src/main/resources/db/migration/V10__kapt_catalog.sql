-- Public search catalog is deliberately separate from registered apartment complexes.
create table kapt_complex_catalog (
    kapt_code varchar(40) primary key,
    kapt_name varchar(200) not null,
    normalized_name varchar(200) not null,
    bjd_code varchar(10) not null check (bjd_code ~ '^[0-9]{10}$'),
    region_name varchar(500) not null,
    road_address varchar(500) not null default '',
    synced_at timestamptz not null default now()
);
create index ix_kapt_catalog_region on kapt_complex_catalog (bjd_code varchar_pattern_ops);
create table kapt_catalog_sync (
    province_code varchar(2) primary key check (province_code ~ '^[0-9]{2}$'),
    synced_at timestamptz not null,
    item_count integer not null check (item_count >= 0)
);
