package com.fixops.member.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class KaptSearchCacheTest {
    @Test void separatesVersionsRegionsAndPages() {
        String key = KaptSearchCache.key("1","11","래미안",1,20);
        assertEquals(key,KaptSearchCache.key("1","11","래미안",1,20));
        assertNotEquals(key,KaptSearchCache.key("2","11","래미안",1,20));
        assertNotEquals(key,KaptSearchCache.key("1","11680","래미안",1,20));
        assertNotEquals(key,KaptSearchCache.key("1","11","래미안",2,20));
        assertNotEquals(key,KaptSearchCache.key("1","11","래미안",1,100));
        assertFalse(key.contains("래미안"));
    }
    @Test void roundTripsJsonAndTtl() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String,String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        var cache = new KaptSearchCache(redis);
        var result = new KaptClient.Search(List.of(new KaptClient.SearchItem("A1","래미안","","1168010100","서울")),1,1);
        cache.put("key",result);
        var json = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq("key"),json.capture(),eq(Duration.ofMinutes(5)));
        when(ops.get("key")).thenReturn(json.getValue());
        assertEquals(result,cache.get("key",1,20).orElseThrow());
        assertTrue(cache.get("key",2,20).isEmpty());
    }
    @Test void cachesEmptyResultsBriefly() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String,String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        var cache = new KaptSearchCache(redis);
        cache.put("empty",new KaptClient.Search(List.of(),1,0));
        verify(ops).set(eq("empty"),anyString(),eq(Duration.ofMinutes(1)));
    }
    @Test void ignoresRedisFailureAndCorruptJson() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String,String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        var cache = new KaptSearchCache(redis);
        when(ops.get("key")).thenReturn("not-json");
        assertTrue(cache.get("key",1,20).isEmpty());
        when(ops.get("key")).thenThrow(new IllegalStateException("unavailable"));
        assertTrue(cache.get("key",1,20).isEmpty());
        doThrow(new IllegalStateException("unavailable")).when(ops).set(eq("key"),anyString(),any(Duration.class));
        assertDoesNotThrow(() -> cache.put("key",new KaptClient.Search(List.of(),1,0)));
    }
}
