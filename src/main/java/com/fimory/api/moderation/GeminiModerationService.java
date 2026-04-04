package com.fimory.api.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fimory.api.config.IntegrationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Primary
public class GeminiModerationService implements ModerationService {

    private static final List<String> API_VERSIONS = List.of("v1", "v1beta");
    private static final int MAX_IMAGE_DIMENSION = 1280;
    private static final int MAX_IMAGE_BYTES = 1_500_000;
    private static final List<String> MODEL_CANDIDATES = List.of(
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp"
    );

    private final IntegrationProperties integrationProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final MockModerationService mockModerationService;
    private final Path uploadRoot;

    public GeminiModerationService(IntegrationProperties integrationProperties,
                                   ObjectMapper objectMapper,
                                   MockModerationService mockModerationService,
                                   @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.integrationProperties = integrationProperties;
        this.objectMapper = objectMapper;
        this.mockModerationService = mockModerationService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public ModerationResult scanText(String text) {
        String apiKey = resolveModerationKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new ModerationResult("REVIEW", "Gemini moderation key is missing.");
        }

        try {
            String responseText = callGemini(apiKey, text);
            return parseModerationResponse(responseText);
        } catch (Exception ex) {
            return new ModerationResult("REVIEW", simplifyGeminiError("Gemini text moderation failed", ex.getMessage()));
        }
    }

    @Override
    public ModerationResult scanImage(String imageUrl) {
        String apiKey = resolveModerationKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new ModerationResult("REVIEW", "Gemini moderation key is missing for image scan.");
        }

        try {
            ImagePayload imagePayload = loadImagePayload(imageUrl);
            if (imagePayload == null || imagePayload.base64Data().isBlank()) {
                return new ModerationResult("REVIEW", "Image moderation could not read the image payload.");
            }
            ModerationResult imageResult = parseModerationResponse(
                    callGeminiForImage(apiKey, imagePayload)
            );
            return applyConservativeImagePolicy(imageResult);
        } catch (Exception ex) {
            return new ModerationResult("REVIEW", simplifyGeminiError("Gemini image moderation failed", ex.getMessage()));
        }
    }

    private String resolveModerationKey() {
        String moderationKey = integrationProperties.getGemini().getModerationApiKey();
        if (moderationKey != null && !moderationKey.isBlank()) {
            return moderationKey.trim();
        }
        String chatKey = integrationProperties.getGemini().getChatApiKey();
        return chatKey == null ? "" : chatKey.trim();
    }

    private String callGemini(String apiKey, String text) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of(
                                                "text",
                                                """
                                                You are a content moderation classifier for a public entertainment platform.
                                                Analyze the following user-generated text and return exactly one JSON object.
                                                Allowed JSON format only:
                                                {"decision":"ALLOW|BLOCK|REVIEW","reason":"short explanation"}

                                                Moderation rules:
                                                - Focus only on sexual or pornographic content moderation.
                                                - BLOCK only for explicit sexual content, pornography, sexual exploitation, or clearly disallowed sexual content.
                                                - REVIEW only when the content is sexually suggestive or suspicious but not explicit enough to block.
                                                - ALLOW for benign, educational, technical, entertainment, violent, action, horror, or ambiguous-but-non-sexual content.
                                                - Do not include markdown fences.

                                                Text to analyze:
                                                """ + (text == null ? "" : text)
                                        )
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0
                )
        );

        String requestBody = objectMapper.writeValueAsString(body);
        IOException lastIo = null;
        InterruptedException lastInterrupted = null;
        String lastError = "No Gemini model attempt succeeded.";

        for (String apiVersion : API_VERSIONS) {
            for (String model : resolveModelCandidates(apiKey, apiVersion)) {
                String endpoint = "https://generativelanguage.googleapis.com/"
                        + apiVersion
                        + "/models/"
                        + model
                        + ":generateContent?key="
                        + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonNode responseJson = objectMapper.readTree(response.body());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        JsonNode textNode = responseJson.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                        if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                            return textNode.asText();
                        }
                        lastError = "Gemini returned empty moderation content.";
                        continue;
                    }
                    String message = responseJson.path("error").path("message").asText();
                    lastError = (message == null || message.isBlank())
                            ? ("HTTP " + response.statusCode())
                            : message;
                } catch (IOException ex) {
                    lastIo = ex;
                    lastError = ex.getMessage();
                } catch (InterruptedException ex) {
                    lastInterrupted = ex;
                    lastError = ex.getMessage();
                }
            }
        }

        if (lastInterrupted != null) {
            throw lastInterrupted;
        }
        if (lastIo != null) {
            throw lastIo;
        }
        throw new IllegalStateException(lastError);
    }

    private String callGeminiForImage(String apiKey, ImagePayload imagePayload) throws IOException, InterruptedException {
        String prompt = """
                You are a strict image moderation classifier for a public entertainment platform.
                Analyze the image and return exactly one JSON object only.
                Allowed JSON format only:
                {"decision":"ALLOW|BLOCK|REVIEW","reason":"short explanation","visible_nipples":true|false,"visible_genitals":true|false,"explicit_nudity":true|false}

                Decision policy:
                - Focus only on sexual or pornographic content moderation.
                - BLOCK for explicit nudity, exposed breasts with visible nipples or areolae, visible genitals, visible vulva, visible penis, visible anus, pornography, explicit sexual acts, explicit masturbation, or sex toys used sexually.
                - REVIEW for clearly sexualized but non-explicit imagery such as lingerie, underwear, fetish styling, erotic posing, explicit cleavage focus, butt-focused sexual posing, or obvious swimsuit glamour shots intended to be sexually provocative.
                - ALLOW all non-sexual content, including action scenes, violence, horror, fantasy, anime posters, combat imagery, or artistic content without sexual focus.
                - If there is no sexual focus, choose ALLOW even when the image is intense, violent, or dramatic.
                - If visible nipples or visible genitals are present, set decision to BLOCK and set the related boolean fields to true.
                - Mention the strongest sexual triggering detail in reason, such as visible nipples, visible genitals, nudity, sexual pose, lingerie, or pornography.
                - Do not include markdown fences or extra text.
                """;

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of(
                                                "text", prompt
                                        ),
                                        Map.of(
                                                "inlineData",
                                                Map.of(
                                                        "mimeType", imagePayload.mimeType(),
                                                        "data", imagePayload.base64Data()
                                                )
                                        )
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0
                )
        );

        return sendGeminiRequest(apiKey, body);
    }

    private ModerationResult applyConservativeImagePolicy(ModerationResult result) {
        if (result == null) {
            return new ModerationResult("REVIEW", "Image moderation returned no result.");
        }
        if (!"ALLOW".equalsIgnoreCase(result.decision())) {
            return result;
        }

        String reason = result.reason() == null ? "" : result.reason().toLowerCase();
        List<String> safePhrases = List.of(
                "no suggestive content",
                "non-suggestive",
                "non-sexual",
                "not sexual",
                "no nudity",
                "no explicit sexual content",
                "no sexual content",
                "no sexual focus",
                "without sexual focus",
                "no erotic content",
                "no pornographic content",
                "safe anime poster",
                "action scene"
        );
        for (String phrase : safePhrases) {
            if (reason.contains(phrase)) {
                return result;
            }
        }

        List<String> suggestiveKeywords = List.of(
                "bikini",
                "lingerie",
                "underwear",
                "cleavage",
                "erotic",
                "sexualized",
                "revealing",
                "fetish",
                "nudity",
                "nude",
                "nipple",
                "genital",
                "porn"
        );
        for (String keyword : suggestiveKeywords) {
            if (reason.contains(keyword)) {
                return new ModerationResult(
                        "REVIEW",
                        "Conservative image policy escalated to REVIEW due to keyword: " + keyword + ". Original reason: " + result.reason()
                );
            }
        }
        return result;
    }

    private String sendGeminiRequest(String apiKey, Map<String, Object> body) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(body);
        IOException lastIo = null;
        InterruptedException lastInterrupted = null;
        String lastError = "No Gemini model attempt succeeded.";

        for (String apiVersion : API_VERSIONS) {
            for (String model : resolveModelCandidates(apiKey, apiVersion)) {
                String endpoint = "https://generativelanguage.googleapis.com/"
                        + apiVersion
                        + "/models/"
                        + model
                        + ":generateContent?key="
                        + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonNode responseJson = objectMapper.readTree(response.body());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        JsonNode textNode = responseJson.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                        if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                            return textNode.asText();
                        }
                        lastError = "Gemini returned empty moderation content.";
                        continue;
                    }
                    String message = responseJson.path("error").path("message").asText();
                    lastError = (message == null || message.isBlank())
                            ? ("HTTP " + response.statusCode())
                            : message;
                } catch (IOException ex) {
                    lastIo = ex;
                    lastError = ex.getMessage();
                } catch (InterruptedException ex) {
                    lastInterrupted = ex;
                    lastError = ex.getMessage();
                }
            }
        }

        if (lastInterrupted != null) {
            throw lastInterrupted;
        }
        if (lastIo != null) {
            throw lastIo;
        }
        throw new IllegalStateException(lastError);
    }

    private List<String> resolveModelCandidates(String apiKey, String apiVersion) {
        Set<String> candidates = new LinkedHashSet<>(MODEL_CANDIDATES);
        try {
            candidates.addAll(fetchAvailableModels(apiKey, apiVersion));
        } catch (Exception ignored) {
            // Fall back to preferred candidates when model discovery is unavailable.
        }
        return new ArrayList<>(candidates);
    }

    private List<String> fetchAvailableModels(String apiKey, String apiVersion) throws IOException, InterruptedException {
        String endpoint = "https://generativelanguage.googleapis.com/"
                + apiVersion
                + "/models?key="
                + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode models = root.path("models");
        if (!models.isArray()) {
            return List.of();
        }

        List<String> discovered = new ArrayList<>();
        for (JsonNode modelNode : models) {
            String name = modelNode.path("name").asText();
            if (name == null || name.isBlank()) {
                continue;
            }
            JsonNode supportedActions = modelNode.path("supportedGenerationMethods");
            boolean supportsGenerateContent = false;
            if (supportedActions.isArray()) {
                for (JsonNode action : supportedActions) {
                    if ("generateContent".equalsIgnoreCase(action.asText())) {
                        supportsGenerateContent = true;
                        break;
                    }
                }
            }
            if (!supportsGenerateContent) {
                continue;
            }

            String modelName = name.startsWith("models/") ? name.substring("models/".length()) : name;
            if (modelName.contains("flash")) {
                discovered.add(modelName);
            }
        }
        return discovered;
    }

    private ImagePayload loadImagePayload(String imageUrl) throws IOException, InterruptedException {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        byte[] imageBytes;
        String mimeType;
        String normalized = imageUrl.trim();

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalized))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Failed to download image. HTTP " + response.statusCode());
            }
            imageBytes = response.body();
            mimeType = response.headers().firstValue("Content-Type").orElseGet(() -> guessMimeType(normalized, imageBytes));
        } else {
            Path imagePath = resolveStoredFilePath(normalized);
            if (imagePath == null || !Files.exists(imagePath)) {
                throw new IllegalStateException("Image file not found: " + imageUrl);
            }
            imageBytes = Files.readAllBytes(imagePath);
            mimeType = Files.probeContentType(imagePath);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = guessMimeType(imagePath.getFileName().toString(), imageBytes);
            }
        }

        ResizedImage resizedImage = optimizeImagePayload(imageBytes, mimeType);
        return new ImagePayload(
                Base64.getEncoder().encodeToString(resizedImage.bytes()),
                normalizeMimeType(resizedImage.mimeType())
        );
    }

    private Path resolveStoredFilePath(String rawPath) {
        String normalized = rawPath.trim().replace("\\", "/");
        if (normalized.startsWith("/storage/")) {
            normalized = normalized.substring("/storage/".length());
        } else if (normalized.startsWith("storage/")) {
            normalized = normalized.substring("storage/".length());
        } else if (normalized.startsWith("/uploads/")) {
            normalized = normalized.substring("/uploads/".length());
        } else if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        } else if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path resolved = uploadRoot.resolve(normalized).normalize();
        return resolved.startsWith(uploadRoot) ? resolved : null;
    }

    private String guessMimeType(String source, byte[] imageBytes) {
        String lower = source == null ? "" : source.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        try {
            String detected = java.net.URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imageBytes));
            return detected == null ? "image/jpeg" : detected;
        } catch (IOException ignored) {
            return "image/jpeg";
        }
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "image/jpeg";
        }
        if (mimeType.startsWith("image/")) {
            return mimeType;
        }
        return "image/jpeg";
    }

    private ModerationResult parseModerationResponse(String responseText) {
        try {
            String cleaned = extractJsonObject(stripMarkdownFence(responseText));
            JsonNode json = objectMapper.readTree(cleaned);
            String decision = json.path("decision").asText("REVIEW").trim().toUpperCase();
            String reason = json.path("reason").asText("Technical error").trim();

            boolean visibleNipples = json.path("visible_nipples").asBoolean(false);
            boolean visibleGenitals = json.path("visible_genitals").asBoolean(false);
            boolean explicitNudity = json.path("explicit_nudity").asBoolean(false);

            if (!"ALLOW".equals(decision) && !"BLOCK".equals(decision) && !"REVIEW".equals(decision)) {
                decision = "REVIEW";
            }
            if (reason.isBlank()) {
                reason = "Technical error";
            }
            if (visibleNipples || visibleGenitals || explicitNudity) {
                return new ModerationResult("BLOCK", buildExplicitReason(reason, visibleNipples, visibleGenitals, explicitNudity));
            }
            return applyExplicitKeywordPolicy(new ModerationResult(decision, reason));
        } catch (Exception ex) {
            String raw = responseText == null ? "" : responseText.trim();
            return new ModerationResult("REVIEW", simplifyGeminiError("Format Error", raw));
        }
    }

    private ModerationResult applyExplicitKeywordPolicy(ModerationResult result) {
        if (result == null) {
            return new ModerationResult("REVIEW", "Image moderation returned no result.");
        }
        String reason = result.reason() == null ? "" : result.reason().toLowerCase();
        List<String> explicitKeywords = List.of(
                "visible nipples",
                "visible nipple",
                "visible areola",
                "exposed breasts",
                "bare breasts",
                "full nudity",
                "explicit nudity",
                "visible genitals",
                "visible vulva",
                "visible penis",
                "visible anus",
                "pornography"
        );
        for (String keyword : explicitKeywords) {
            if (reason.contains(keyword)) {
                return new ModerationResult("BLOCK", "Explicit sexual content detected: " + keyword + ". Original reason: " + result.reason());
            }
        }
        return result;
    }

    private String buildExplicitReason(String originalReason,
                                       boolean visibleNipples,
                                       boolean visibleGenitals,
                                       boolean explicitNudity) {
        List<String> triggers = new ArrayList<>();
        if (visibleNipples) {
            triggers.add("visible nipples");
        }
        if (visibleGenitals) {
            triggers.add("visible genitals");
        }
        if (explicitNudity) {
            triggers.add("explicit nudity");
        }
        String base = String.join(", ", triggers);
        if (originalReason == null || originalReason.isBlank()) {
            return "Explicit sexual content detected: " + base + ".";
        }
        return "Explicit sexual content detected: " + base + ". Original reason: " + originalReason;
    }

    private String simplifyGeminiError(String prefix, String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        String lower = message.toLowerCase();

        if (lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("rate limit")) {
            return prefix + ": quota exceeded or rate limited.";
        }
        if (lower.contains("not found for api version") || lower.contains("not supported for generatecontent")) {
            return prefix + ": model is not available for the current API version.";
        }
        if (lower.contains("invalid json payload")) {
            return prefix + ": invalid request payload.";
        }
        if (message.length() > 220) {
            message = message.substring(0, 217) + "...";
        }
        return prefix + ": " + message;
    }

    private String stripMarkdownFence(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("```")) {
            int firstNewLine = cleaned.indexOf('\n');
            if (firstNewLine >= 0) {
                cleaned = cleaned.substring(firstNewLine + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        return cleaned.trim();
    }

    private String extractJsonObject(String value) {
        if (value == null) {
            return "";
        }
        int firstBrace = value.indexOf('{');
        int lastBrace = value.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return value.substring(firstBrace, lastBrace + 1).trim();
        }
        return value.trim();
    }

    private ResizedImage optimizeImagePayload(byte[] originalBytes, String mimeType) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (source == null) {
                return fallbackBinaryPayload(originalBytes, mimeType);
            }

            BufferedImage resized = resizeImage(source);
            byte[] encoded = encodeJpeg(resized, 0.82f);
            if (encoded.length > MAX_IMAGE_BYTES) {
                encoded = encodeJpeg(resizeImage(scaleDown(resized, 0.8d)), 0.72f);
            }
            if (encoded.length > MAX_IMAGE_BYTES) {
                encoded = encodeJpeg(resizeImage(scaleDown(resized, 0.65d)), 0.65f);
            }
            if (encoded.length > MAX_IMAGE_BYTES) {
                encoded = encodeJpeg(scaleDown(resized, 0.5d), 0.58f);
            }

            if (encoded.length > MAX_IMAGE_BYTES) {
                return new ResizedImage(encoded, "image/jpeg");
            }
            return new ResizedImage(encoded, "image/jpeg");
        } catch (Exception ignored) {
            return fallbackBinaryPayload(originalBytes, mimeType);
        }
    }

    private ResizedImage fallbackBinaryPayload(byte[] originalBytes, String mimeType) {
        byte[] bytes = originalBytes;
        if (bytes.length > MAX_IMAGE_BYTES) {
            bytes = java.util.Arrays.copyOf(bytes, MAX_IMAGE_BYTES);
        }
        return new ResizedImage(bytes, normalizeMimeType(mimeType));
    }

    private BufferedImage resizeImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int largestSide = Math.max(width, height);
        if (largestSide <= MAX_IMAGE_DIMENSION) {
            return forceRgb(source);
        }

        double scale = (double) MAX_IMAGE_DIMENSION / largestSide;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        return drawScaled(forceRgb(source), targetWidth, targetHeight);
    }

    private BufferedImage scaleDown(BufferedImage source, double scale) {
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return drawScaled(forceRgb(source), targetWidth, targetHeight);
    }

    private BufferedImage drawScaled(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private BufferedImage forceRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(Math.max(0.4f, Math.min(0.95f, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private record ImagePayload(String base64Data, String mimeType) {
    }

    private record ResizedImage(byte[] bytes, String mimeType) {
    }
}
