package com.fixops.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Component
@EnableScheduling
@ConditionalOnProperty(name="fixops.kapt.catalog-sync-enabled", havingValue="true")
public class KaptCatalogScheduler {
    private static final Logger log=LoggerFactory.getLogger(KaptCatalogScheduler.class);
    private static final List<String> PROVINCES=KaptCatalogService.PROVINCES;
    private int cursor;
    private final KaptCatalogService catalog;
    private final JdbcTemplate db;
    private final RequestRateLimiter limiter;
    public KaptCatalogScheduler(KaptCatalogService catalog, JdbcTemplate db, RequestRateLimiter limiter) {
        this.catalog=catalog; this.db=db; this.limiter=limiter;
    }
    // One province per run; retry failed provinces on subsequent runs. No startup traffic by default.
    @Scheduled(initialDelay=30000, fixedDelay=300000)
    public void syncNext() {
        try {
            var completed=db.queryForList("select province_code from kapt_catalog_sync where synced_at > now()-interval '24 hours'",String.class);
            for (int i=0;i<PROVINCES.size();i++) {
                String province=PROVINCES.get(Math.floorMod(cursor++,PROVINCES.size()));
                if (completed.contains(province)) continue;
                // Global Redis gate across instances, with expiry to recover after crashes.
                limiter.check("kapt-catalog-job","all",1,240);
                int count=catalog.synchronize(province, () -> limiter.check("kapt-catalog-upstream","all",100,60));
                log.info("KAPT catalog synchronized: province={}, count={}",province,count);
                return;
            }
        } catch(Exception ex) {
            log.warn("KAPT catalog synchronization failed: type={}",ex.getClass().getSimpleName());
        }
    }
}
