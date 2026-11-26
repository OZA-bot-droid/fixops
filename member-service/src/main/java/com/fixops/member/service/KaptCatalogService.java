package com.fixops.member.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class KaptCatalogService {
    static final List<String> PROVINCES = List.of("11","26","27","28","29","30","31","36","41","43","44","46","47","48","50","51","52");
    public record Coverage(int readyCount, int totalCount, boolean nationwideReady, List<String> missingProvinces) {}
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final KaptClient kapt;
    private final KaptSearchCache cache;
    public KaptCatalogService(JdbcTemplate db, TransactionTemplate tx, KaptClient kapt, KaptSearchCache cache) {
        this.db = db; this.tx = tx; this.kapt = kapt; this.cache = cache;
    }

    public KaptClient.Search search(String region, String q, int page, int size) {
        if (region == null || region.isBlank()) return searchNationwide(q,page,size);
        kapt.listRequest(region, q, page, size); // reuse validation
        var local = tx.execute(status -> {
            // A single read transaction with a shared advisory lock prevents a concurrent replacement.
            db.queryForList("select pg_advisory_xact_lock_shared(60421, ?)", Integer.parseInt(region.substring(0,2)));
            var versions = db.queryForList("""
                select snapshot_version::text from kapt_catalog_sync
                  where province_code=? and synced_at > now()-interval '7 days'
                """, String.class, region.substring(0,2));
            if (versions.isEmpty()) return Optional.<KaptClient.Search>empty();
            String needle = KaptClient.normalizedName(q.trim());
            String prefix = region + "%";
            String cacheKey = KaptSearchCache.key(versions.getFirst(), region, needle, page, size);
            var cached = cache.get(cacheKey, page, size);
            if (cached.isPresent()) return cached;
            Integer total = db.queryForObject("""
                select count(*)::int from kapt_complex_catalog
                 where bjd_code like ? and strpos(normalized_name, ?) > 0
                """, Integer.class, prefix, needle);
            var rows = db.query("""
                select kapt_code,kapt_name,road_address,bjd_code,region_name
                  from kapt_complex_catalog
                 where bjd_code like ? and strpos(normalized_name, ?) > 0
                 order by kapt_name,kapt_code limit ? offset ?
                """, (rs,n) -> new KaptClient.SearchItem(rs.getString(1),rs.getString(2),
                    rs.getString(3),rs.getString(4),rs.getString(5)),
                prefix, needle, size, (page-1L)*size);
            var result = new KaptClient.Search(rows,page,total);
            cache.put(cacheKey, result);
            return Optional.of(result);
        });
        return local.orElseGet(() -> kapt.search(region,q,page,size));
    }

    public Coverage coverage() {
        return coverageFor(db.queryForList("select province_code from kapt_catalog_sync where synced_at > now()-interval '7 days'", String.class));
    }
    static Coverage coverageFor(Collection<String> ready) {
        var missing = PROVINCES.stream().filter(p -> !ready.contains(p)).toList();
        return new Coverage(PROVINCES.size()-missing.size(),PROVINCES.size(),missing.isEmpty(),missing);
    }
    static String nationwideVersion(Map<String,String> versions) {
        if (!coverageFor(versions.keySet()).nationwideReady())
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");
        return PROVINCES.stream().map(p -> p+"-"+versions.get(p)).collect(java.util.stream.Collectors.joining("."));
    }
    static void validateNationwide(String q,int page,int size) {
        if (q == null || q.length()>100 || KaptClient.normalizedName(q).length()<2 || page<1 || size<1 || size>100)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_INPUT");
    }
    KaptClient.Search searchNationwide(String q,int page,int size) {
        validateNationwide(q,page,size);
        return tx.execute(status -> {
            for (String province : PROVINCES)
                db.queryForList("select pg_advisory_xact_lock_shared(60421, ?)", Integer.parseInt(province));
            var versions = new HashMap<String,String>();
            db.query("select province_code,snapshot_version::text from kapt_catalog_sync where synced_at > now()-interval '7 days'",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> versions.put(rs.getString(1),rs.getString(2)));
            String version = nationwideVersion(versions);
            String needle = KaptClient.normalizedName(q.trim());
            String key = KaptSearchCache.key(version,"ALL",needle,page,size);
            var cached = cache.get(key,page,size);
            if (cached.isPresent()) return cached.get();
            String filter = " where left(bjd_code,2) in ('" + String.join("','",PROVINCES) + "') and strpos(normalized_name, ?) > 0";
            Integer total = db.queryForObject("select count(*)::int from kapt_complex_catalog"+filter,Integer.class,needle);
            var rows = db.query("select kapt_code,kapt_name,road_address,bjd_code,region_name from kapt_complex_catalog"+filter+
                " order by kapt_name,kapt_code limit ? offset ?",
                (rs,n) -> new KaptClient.SearchItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),
                needle,size,(page-1L)*size);
            var result = new KaptClient.Search(rows,page,total);
            cache.put(key,result);
            return result;
        });
    }

    // Network requests run outside DB transactions. Only a complete validated snapshot is published.
    public int synchronize(String province, Runnable beforeRequest) {
        if (!province.matches("[0-9]{2}")) throw new IllegalArgumentException("province");
        var rows = kapt.snapshotProvince(province, beforeRequest);
        for (var row : rows) {
            if (!row.bjdCode().matches("[0-9]{10}") || !row.bjdCode().startsWith(province)
                || !row.kaptCode().matches("A[0-9]{1,39}") || row.kaptName().isBlank()
                || row.kaptName().length()>200 || row.regionName().length()>500 || row.roadAddress().length()>500)
                throw new IllegalStateException("Invalid KAPT snapshot record");
        }
        if (rows.isEmpty()) throw new IllegalStateException("Refusing empty province snapshot");
        tx.executeWithoutResult(status -> {
            db.queryForList("select pg_advisory_xact_lock(60421, ?)", Integer.parseInt(province));
            db.update("delete from kapt_complex_catalog where bjd_code like ?", province+"%");
            db.batchUpdate("""
                insert into kapt_complex_catalog(kapt_code,kapt_name,normalized_name,bjd_code,region_name,road_address)
                values (?,?,?,?,?,?)
                
                """, rows, 500, (ps,row) -> {
                    ps.setString(1,row.kaptCode()); ps.setString(2,row.kaptName());
                    ps.setString(3,KaptClient.normalizedName(row.kaptName()));
                    ps.setString(4,row.bjdCode()); ps.setString(5,row.regionName()); ps.setString(6,row.roadAddress());
                });
            db.update("""
                insert into kapt_catalog_sync(province_code,synced_at,item_count,snapshot_version) values (?,now(),?,nextval('kapt_catalog_snapshot_version_seq'))
                on conflict(province_code) do update set synced_at=excluded.synced_at,item_count=excluded.item_count,snapshot_version=excluded.snapshot_version
                """,province,rows.size());
        });
        return rows.size();
    }
}
