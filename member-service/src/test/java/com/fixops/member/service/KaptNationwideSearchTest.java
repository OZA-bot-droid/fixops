package com.fixops.member.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KaptNationwideSearchTest {
    @Test void requiresAllExpectedProvinces() {
        var ready = new ArrayList<>(KaptCatalogService.PROVINCES);
        assertTrue(KaptCatalogService.coverageFor(ready).nationwideReady());
        ready.remove("11");
        ready.add("99");
        var status = KaptCatalogService.coverageFor(ready);
        assertFalse(status.nationwideReady());
        assertEquals(16,status.readyCount());
        assertEquals(List.of("11"),status.missingProvinces());
    }
    @Test void nationwideCacheChangesWhenAnyProvinceChanges() {
        var versions = new HashMap<String,String>();
        for (String p : KaptCatalogService.PROVINCES) versions.put(p,"1");
        String before = KaptCatalogService.nationwideVersion(versions);
        versions.put("11","2");
        assertNotEquals(before,KaptCatalogService.nationwideVersion(versions));
        versions.remove("11");
        assertThrows(ResponseStatusException.class, () -> KaptCatalogService.nationwideVersion(versions));
    }
    @Test void validatesNationwideQuery() {
        assertDoesNotThrow(() -> KaptCatalogService.validateNationwide("자이",1,20));
        assertThrows(ResponseStatusException.class, () -> KaptCatalogService.validateNationwide(" ",1,20));
        assertThrows(ResponseStatusException.class, () -> KaptCatalogService.validateNationwide("자",1,20));
        assertThrows(ResponseStatusException.class, () -> KaptCatalogService.validateNationwide("자이",0,20));
        assertThrows(ResponseStatusException.class, () -> KaptCatalogService.validateNationwide("자이",1,101));
    }
}
