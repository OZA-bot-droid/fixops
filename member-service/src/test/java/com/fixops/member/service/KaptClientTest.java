package com.fixops.member.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletionException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class KaptClientTest {
    private final KaptClient client = new KaptClient("test");
    @Test void parsesObservedListResponse() {
        var body = client.parseBody("""
            {"response":{"body":{"items":[{"kaptCode":"A10019883","kaptName":"역삼동월드메르디앙아파트","bjdCode":"1168010100","as1":"서울특별시","as2":"강남구","as3":"역삼동","as4":null}],"numOfRows":20,"pageNo":1,"totalCount":236},"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."}}}
            """.getBytes(StandardCharsets.UTF_8), "list");
        var result = client.parseSearch(body, "", 1);
        assertEquals(236, result.totalCount());
        assertEquals("서울특별시 강남구 역삼동", result.items().getFirst().regionName());
        assertEquals("", result.items().getFirst().roadAddress());
        assertEquals("1168010100", result.items().getFirst().bjdCode());
        assertTrue(client.parseSearch(body, "없는단지", 1).items().isEmpty());
    }
    @Test void parsesObservedDetailResponse() {
        var body = client.parseBody("""
            {"response":{"body":{"item":{"kaptCode":"A10019883","kaptName":"역삼동월드메르디앙아파트","doroJuso":"서울특별시 강남구 도곡로21길 15","zipcode":null}},"header":{"resultCode":"00"}}}
            """.getBytes(StandardCharsets.UTF_8), "detail");
        assertEquals("서울특별시 강남구 도곡로21길 15", client.parseDetail(body,"A10019883").orElseThrow().roadAddress());
        assertThrows(ResponseStatusException.class, () -> client.parseDetail(body,"A10020216"));
    }
    @Test void rejectsUpstreamError() {
        assertThrows(ResponseStatusException.class, () -> client.parseBody(
            "{\"response\":{\"header\":{\"resultCode\":\"30\"}}}".getBytes(StandardCharsets.UTF_8), "list"));
    }
    @Test void mapsRegionCodesToCorrectRequests() {
        String[][] cases = {{"11","getSidoAptList4","sidoCode"},
            {"11680","getSigunguAptList4","sigunguCode"},
            {"1168010100","getLegaldongAptList4","bjdCode"}};
        for (String[] row : cases) {
            var r = client.listRequest(row[0], "", 1, 20);
            assertEquals("AptListService4/" + row[1], r.path());
            assertEquals(Map.of(row[2],row[0],"pageNo","1","numOfRows","20"), r.params());
            var uri = client.requestUri(r.path(), r.params());
            assertTrue(uri.getRawQuery().contains(row[2] + "=" + row[0]));
            assertTrue(uri.getRawQuery().contains("dataType=JSON"));
        }
        assertThrows(ResponseStatusException.class, () -> client.listRequest("111", "", 1, 20));
    }
    @Test void encodesServiceKeyExactlyOnce() {
        var special = new KaptClient("abc+def/ghi=");
        var uri = special.requestUri("AptListService4/getSidoAptList4", Map.of("sidoCode","11"));
        assertTrue(uri.getRawQuery().contains("serviceKey=abc%2Bdef%2Fghi%3D"));
        assertFalse(uri.getRawQuery().contains("%25"));
    }
    @Test void rejectsMalformedAndNonJsonResponses() {
        for (String response : List.of("<html>error</html>", "{", "null", "{}")) {
            assertThrows(ResponseStatusException.class, () ->
                client.parseBody(response.getBytes(StandardCharsets.UTF_8), "list"));
        }
    }
    @Test void rejectsOversizedBodyAndCancelsSubscription() {
        var subscriber = new KaptClient.LimitedBodySubscriber();
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            public void request(long n) {}
            public void cancel() { cancelled.set(true); }
        });
        subscriber.onNext(List.of(ByteBuffer.allocate(2 * 1024 * 1024 + 1)));
        assertTrue(cancelled.get());
        assertThrows(CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
    }
    @Test void acceptsBodyAtSizeLimit() {
        var subscriber = new KaptClient.LimitedBodySubscriber();
        subscriber.onSubscribe(new Flow.Subscription() {
            public void request(long n) {}
            public void cancel() {}
        });
        subscriber.onNext(List.of(ByteBuffer.allocate(2 * 1024 * 1024)));
        subscriber.onComplete();
        assertEquals(2 * 1024 * 1024, subscriber.getBody().toCompletableFuture().join().length);
    }
    @Test void searchesAcrossAllUpstreamPagesAndPaginatesMatches() {
        var calls = new java.util.ArrayList<Integer>();
        var result = client.scanRegion("역삼 자이", 1, 20, page -> {
            calls.add(page);
            var items = new java.util.ArrayList<KaptClient.SearchItem>();
            int count = page == 1 ? 100 : 1;
            for (int i = 0; i < count; i++) {
                items.add(new KaptClient.SearchItem("A" + (page * 1000 + i),
                    page == 2 ? "역삼자이아파트" : "기타단지", "", "", ""));
            }
            return new KaptClient.Search(items, page, 101);
        });
        assertEquals(List.of(1, 2), calls);
        assertEquals(1, result.totalCount());
        assertEquals("역삼자이아파트", result.items().getFirst().kaptName());
    }
    @Test void rejectsIncompleteRegionalScan() {
        assertThrows(ResponseStatusException.class, () -> client.scanRegion("자이", 1, 20,
            page -> new KaptClient.Search(List.of(), page, 101)));
    }
    @Test void rejectsRegionExceedingScanLimit() {
        assertThrows(ResponseStatusException.class, () -> client.scanRegion("자이", 1, 20,
            page -> new KaptClient.Search(List.of(), page, 10001)));
    }
}
