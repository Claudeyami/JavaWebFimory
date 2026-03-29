package com.fimory.api.video;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonUtil {

    private static final Pattern LONG_CLAIM = Pattern.compile("\\\"([a-zA-Z0-9_]+)\\\"\\s*:\\s*([0-9]+)");

    private JsonUtil() {
    }

    static Map<String, Long> parseLongClaims(String json) {
        Matcher matcher = LONG_CLAIM.matcher(json);
        Map<String, Long> result = new HashMap<>();
        while (matcher.find()) {
            result.put(matcher.group(1), Long.parseLong(matcher.group(2)));
        }
        return result;
    }
}
