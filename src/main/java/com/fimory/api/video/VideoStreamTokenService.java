package com.fimory.api.video;

import com.fimory.api.config.VideoProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class VideoStreamTokenService {

    private final VideoProperties videoProperties;

    public VideoStreamTokenService(VideoProperties videoProperties) {
        this.videoProperties = videoProperties;
    }

    public String issueToken(Long videoId, Long userId) {
        long now = Instant.now().getEpochSecond();
        long exp = now + Math.max(60, videoProperties.getStreamTokenTtlSec());

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = "{\"vid\":" + videoId + ",\"uid\":" + userId + ",\"iat\":" + now + ",\"exp\":" + exp + "}";

        String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + payload;
        String signature = sign(unsigned);
        return unsigned + "." + signature;
    }

    public StreamTokenPayload verify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing stream token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid stream token");
        }
        String unsigned = parts[0] + "." + parts[1];
        String actual = sign(unsigned);
        if (!constantTimeEquals(actual, parts[2])) {
            throw new IllegalArgumentException("Invalid stream token signature");
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

        Map<String, Long> claims = JsonUtil.parseLongClaims(payloadJson);
        Long vid = claims.get("vid");
        Long uid = claims.get("uid");
        Long iat = claims.get("iat");
        Long exp = claims.get("exp");

        if (vid == null || uid == null || iat == null || exp == null) {
            throw new IllegalArgumentException("Invalid stream token payload");
        }

        long now = Instant.now().getEpochSecond();
        if (now >= exp) {
            throw new IllegalArgumentException("Stream token expired");
        }

        return new StreamTokenPayload(vid, uid, iat, exp);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(videoProperties.getStreamTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signed = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return base64Url(signed);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign stream token", ex);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
