package com.fixops.member.web;

import java.time.Instant;
import java.util.Map;

public final class Api {
    private Api() {
    }

    public static Map<String, Object> ok(Object data) {
        return Map.of("success", true, "data", data, "timestamp", Instant.now().toString());
    }
}
