package com.fixops.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.time.Duration;
import java.util.*;

@Component
public class KaptClient {
    private static final Logger log = LoggerFactory.getLogger(KaptClient.class);
    private static final String BASE = "https://apis.data.go.kr/1613000/";
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int SCAN_SIZE = 100;
    private static final int MAX_SCAN_PAGES = 100;
    private static final Duration SCAN_TIMEOUT = Duration.ofSeconds(20);
    private final String key;
    private final Semaphore scanSlots = new Semaphore(2);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public KaptClient(@Value("${fixops.kapt.service-key:}") String key) { this.key = key; }

    public record Complex(String kaptCode, String kaptName, String roadAddress) {}
    public record SearchItem(String kaptCode, String kaptName, String roadAddress, String bjdCode, String regionName) {}
    public record Search(List<SearchItem> items, int page, int totalCount) {}

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_INPUT");
    }
    private static String value(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
    }
    URI requestUri(String path, Map<String,String> params) {
        var uri = UriComponentsBuilder.fromUriString(BASE + path)
            .queryParam("serviceKey", "{serviceKey}").queryParam("dataType", "JSON");
        var values = new HashMap<String,String>();
        values.put("serviceKey", key);
        params.forEach((k,v) -> { uri.queryParam(k, "{" + k + "}"); values.put(k,v); });
        return uri.encode().buildAndExpand(values).toUri();
    }

    // Bound both response size and full-body completion, without logging URI or secrets.
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription s) {
            if (subscription != null) { s.cancel(); return; }
            subscription = s;
            s.request(1);
        }
        public void onNext(List<ByteBuffer> buffers) {
            try {
                for (var buffer : buffers) {
                    if (buffer.remaining() > MAX_BODY - bytes.size())
                        throw new IOException("KAPT response exceeds size limit");
                    byte[] chunk = new byte[buffer.remaining()];
                    buffer.get(chunk);
                    bytes.writeBytes(chunk);
                }
                subscription.request(1);
            } catch (Exception ex) {
                subscription.cancel();
                body.completeExceptionally(ex);
            }
        }
        public void onError(Throwable ex) { body.completeExceptionally(ex); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }

    private JsonNode call(String path, Map<String,String> params) {
        return call(path, params, REQUEST_TIMEOUT);
    }
    private JsonNode call(String path, Map<String,String> params, Duration timeout) {
        if (key.isBlank()) {
            log.warn("KAPT_SERVICE_KEY is not configured");
            throw unavailable();
        }
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(requestUri(path, params))
                .header("Accept", "application/json").timeout(timeout).GET().build();
            pending = client.sendAsync(request, info -> new LimitedBodySubscriber());
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            String contentType = response.headers().firstValue("Content-Type").orElse("missing");
            // Log only known media types; arbitrary upstream header values may contain sensitive data.
            String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!Set.of("application/json", "application/xml", "text/xml", "text/html", "text/plain").contains(mediaType))
                mediaType = "other-or-missing";
            log.debug("KAPT response: path={}, status={}, contentType={}, elapsedMs={}",
                path, response.statusCode(), mediaType, elapsedMs(started));
            if (response.statusCode() != 200) {
                log.warn("KAPT HTTP failure: path={}, status={}, contentType={}, elapsedMs={}",
                    path, response.statusCode(), mediaType, elapsedMs(started));
                throw unavailable();
            }
            if (!"application/json".equals(mediaType))
                log.warn("KAPT unexpected content type: path={}, contentType={}", path, mediaType);
            return parseBody(response.body(), path);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("KAPT request interrupted: path={}", path);
            throw unavailable();
        } catch (TimeoutException ex) {
            log.warn("KAPT full-response timeout: path={}, elapsedMs={}", path, elapsedMs(started));
            throw unavailable();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
            log.warn("KAPT request failed: path={}, type={}, elapsedMs={}",
                path, cause.getClass().getSimpleName(), elapsedMs(started));
            throw unavailable();
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }
    private static long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
    JsonNode parseBody(byte[] bytes, String path) {
        JsonNode response;
        try {
            var root = mapper.readTree(bytes);
            if (root == null || !root.isObject()) throw unavailable();
            response = root.path("response");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("KAPT invalid JSON: path={}, type={}", path, ex.getClass().getSimpleName());
            throw unavailable();
        }
        String code = value(response.path("header"), "resultCode");
        if (!Set.of("00", "0000", "0").contains(code)) {
            log.warn("KAPT rejected response: path={}, resultCode={}", path,
                code.matches("[0-9]{1,8}") ? code : "missing-or-invalid");
            throw unavailable();
        }
        var body = response.path("body");
        if (!body.isObject()) {
            log.warn("KAPT missing body: path={}", path);
            throw unavailable();
        }
        return body;
    }
    record ListRequest(String path, Map<String,String> params) {}
    ListRequest listRequest(String region, String q, int page, int size) {
        if (region == null || !region.matches("[0-9]{2}|[0-9]{5}|[0-9]{10}")
            || page < 1 || size < 1 || size > 100 || q == null || q.length() > 100) throw invalid();
        String operation = switch (region.length()) {
            case 2 -> "getSidoAptList4"; case 5 -> "getSigunguAptList4"; default -> "getLegaldongAptList4";
        };
        String param = switch (region.length()) {
            case 2 -> "sidoCode"; case 5 -> "sigunguCode"; default -> "bjdCode";
        };
        return new ListRequest("AptListService4/" + operation,
            Map.of(param, region, "pageNo", Integer.toString(page), "numOfRows", Integer.toString(size)));
    }
    public Search search(String region, String q, int page, int size) {
        var request = listRequest(region, q, page, size);
        if (q.isBlank()) return parseSearch(call(request.path(), request.params()), "", page);
        if (!scanSlots.tryAcquire()) {
            log.warn("KAPT regional name search concurrency limit reached");
            throw unavailable();
        }
        long deadline = System.nanoTime() + SCAN_TIMEOUT.toNanos();
        try {
            return scanRegion(q.trim(), page, size, upstreamPage -> {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw unavailable();
                var scan = listRequest(region, "", upstreamPage, SCAN_SIZE);
                Duration timeout = Duration.ofNanos(Math.min(remaining, REQUEST_TIMEOUT.toNanos()));
                return parseSearch(call(scan.path(), scan.params(), timeout), "", upstreamPage);
            });
        } finally {
            scanSlots.release();
        }
    }
    Search scanRegion(String q, int page, int size, java.util.function.IntFunction<Search> fetchPage) {
        var all = collectRegion(q, fetchPage);
        int from = (int)Math.min(all.size(), (page - 1L) * size);
        int to = Math.min(all.size(), from + size);
        return new Search(List.copyOf(all.subList(from, to)), page, all.size());
    }
    List<SearchItem> collectRegion(String q, java.util.function.IntFunction<Search> fetchPage) {
        var matches = new LinkedHashMap<String, SearchItem>();
        String needle = normalizedName(q);
        Search first = fetchPage.apply(1);
        int total = first.totalCount();
        long pages = Math.max(1, (total + (long) SCAN_SIZE - 1) / SCAN_SIZE);
        if (pages > MAX_SCAN_PAGES) {
            log.warn("KAPT regional search exceeds page limit: pages={}", pages);
            throw unavailable();
        }
        var seen = new HashSet<String>();
        for (int i = 1; i <= pages; i++) {
            Search current = i == 1 ? first : fetchPage.apply(i);
            int expected = (int)Math.min(SCAN_SIZE, Math.max(0L, total - (i - 1L) * SCAN_SIZE));
            if (current.totalCount() != total || current.items().size() != expected) {
                log.warn("KAPT regional list changed or returned incomplete page: page={}", i);
                throw unavailable();
            }
            for (SearchItem item : current.items()) {
                if (!seen.add(item.kaptCode())) {
                    log.warn("KAPT regional list contains duplicate codes");
                    throw unavailable();
                }
                if (normalizedName(item.kaptName()).contains(needle)) matches.put(item.kaptCode(), item);
            }
        }
        var all = new ArrayList<>(matches.values());
        all.sort(Comparator.comparing(SearchItem::kaptName).thenComparing(SearchItem::kaptCode));
        return List.copyOf(all);
    }
    static String normalizedName(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
    List<SearchItem> snapshotProvince(String region, Runnable beforeRequest) {
        if (!region.matches("[0-9]{2}")) throw invalid();
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        return collectRegion("", page -> {
            beforeRequest.run();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw unavailable();
            var request = listRequest(region, "", page, SCAN_SIZE);
            return parseSearch(call(request.path(), request.params(),
                Duration.ofNanos(Math.min(remaining, REQUEST_TIMEOUT.toNanos()))), "", page);
        });
    }
    Search parseSearch(JsonNode body, String q, int page) {
        int total;
        try {
            total = Integer.parseInt(value(body, "totalCount"));
            if (total < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            log.warn("KAPT list has invalid totalCount");
            throw unavailable();
        }
        var items = body.path("items");
        if (!items.isArray()) {
            if (total == 0 && (items.isNull() || items.isMissingNode() || (items.isTextual() && items.asText().isBlank()))) {
                return new Search(List.of(), page, total);
            }
            log.warn("KAPT list items is not an array");
            throw unavailable();
        }
        var list = new ArrayList<SearchItem>();
        for (JsonNode item : items) {
            String code = value(item, "kaptCode"), name = value(item, "kaptName");
            if (!code.isBlank() && !name.isBlank() && name.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) {
                String regionName = String.join(" ", List.of(value(item,"as1"), value(item,"as2"), value(item,"as3"), value(item,"as4"))
                    .stream().filter(v -> !v.isBlank()).toList());
                list.add(new SearchItem(code, name, value(item,"doroJuso"), value(item,"bjdCode"), regionName));
            }
        }
        return new Search(List.copyOf(list), page, total);
    }
    public Optional<Complex> detail(String code) {
        if (code == null || !code.matches("A[0-9]{1,39}")) throw invalid();
        return parseDetail(call("AptBasisInfoServiceV5/getAphusBassInfoV5", Map.of("kaptCode", code)), code);
    }
    public record ParcelHint(String sigunguCd,String bjdongCd,String platGbCd,String bun,String ji,String lotAddress,String roadAddress) {}
    public Optional<ParcelHint> parcelHint(String code) {
        if (code == null || !code.matches("A[0-9]{1,39}")) throw invalid();
        var body = call("AptBasisInfoServiceV5/getAphusBassInfoV5", Map.of("kaptCode", code));
        if (parseDetail(body,code).isEmpty()) return Optional.empty();
        return parseParcelHint(body.path("item"));
    }
    static Optional<ParcelHint> parseParcelHint(JsonNode item) {
        String bjd=value(item,"bjdCode"), address=value(item,"kaptAddr");
        if (!bjd.matches("[0-9]{10}") || address.isBlank()) return Optional.empty();
        // Accept only an unambiguous lot after a legal-dong/ri name; do not infer from road numbers.
        var match=java.util.regex.Pattern.compile("(?:^|\\s)[^\\s]+[동리]\\s+(산\\s*)?([0-9]{1,4})(?:-([0-9]{1,4}))?(?=\\s|$)").matcher(address);
        if (!match.find()) return Optional.empty();
        String land=match.group(1)==null?"0":"1";
        int main=Integer.parseInt(match.group(2));
        int sub=match.group(3)==null?0:Integer.parseInt(match.group(3));
        int end=match.end();
        if (main==0 || match.find()) return Optional.empty();
        String tail=address.substring(end).trim();
        if (tail.startsWith("외") || tail.startsWith("및") || tail.startsWith(",")) return Optional.empty();
        return Optional.of(new ParcelHint(bjd.substring(0,5),bjd.substring(5),land,
            String.format(Locale.ROOT,"%04d",main),String.format(Locale.ROOT,"%04d",sub),address,value(item,"doroJuso")));
    }
    Optional<Complex> parseDetail(JsonNode body, String code) {
        var item = body.path("item");
        if (item.isMissingNode() || item.isNull()) return Optional.empty();
        if (!item.isObject()) {
            log.warn("KAPT detail item is not an object");
            throw unavailable();
        }
        String actual = value(item,"kaptCode");
        if (actual.isBlank()) return Optional.empty();
        String name = value(item,"kaptName"), address = value(item,"doroJuso");
        if (!code.equals(actual) || name.isBlank() || name.length() > 200 || address.length() > 500) {
            log.warn("KAPT detail identity or field validation failed");
            throw unavailable();
        }
        if (address.isBlank()) return Optional.empty();
        return Optional.of(new Complex(actual,name,address));
    }
}
