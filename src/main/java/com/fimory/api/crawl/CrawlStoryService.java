package com.fimory.api.crawl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@Service
public class CrawlStoryService {

    private enum StorySite {
        NETTRUYEN, TRUYENQQNO, TRUYENVN, BLOGTRUYEN, GOCTRUYENTRANHVUI, OTHER
    }

    private static final List<StorySite> STORY_SITE_PRIORITY = List.of(
            StorySite.NETTRUYEN,
            StorySite.TRUYENQQNO,
            StorySite.TRUYENVN,
            StorySite.BLOGTRUYEN,
            StorySite.GOCTRUYENTRANHVUI,
            StorySite.OTHER
    );

    private static final Pattern META_TAG_PATTERN = Pattern.compile("<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern H1_PATTERN = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img\\s+[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern A_TAG_PATTERN = Pattern.compile("<a\\s+[^>]*href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STRIP_TAGS_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CHAPTER_NUM_PATTERN = Pattern.compile("(?:chap(?:ter)?|chuong|chương|tap|tập|ep(?:isode)?)\\s*[-.:#]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private final HttpClient httpClient;
    private final Path uploadRoot;

    public CrawlStoryService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> crawlStory(String inputUrl, boolean downloadCover) {
        if (inputUrl == null || inputUrl.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        StorySite detected = detectStorySite(inputUrl);
        List<StorySite> candidates = new ArrayList<>();
        candidates.add(detected);
        for (StorySite site : STORY_SITE_PRIORITY) {
            if (!candidates.contains(site)) {
                candidates.add(site);
            }
        }

        List<String> errors = new ArrayList<>();
        for (StorySite site : candidates) {
            try {
                Map<String, Object> series = crawlStoryWithSite(inputUrl, site, downloadCover);
                String title = stringValue(series.get("title"));
                if (title != null && !title.isBlank()) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("ok", true);
                    response.put("series", series);
                    return response;
                }
                errors.add("[" + siteName(site) + "] missing title");
            } catch (Exception ex) {
                errors.add("[" + siteName(site) + "] " + ex.getMessage());
            }
        }
        throw new IllegalStateException("Không crawl được truyện: " + String.join(" | ", errors));
    }

    public Map<String, Object> crawlChapter(Map<String, Object> payload) {
        String chapterUrl = firstNonBlank(
                stringValue(payload.get("chapterUrl")),
                stringValue(payload.get("url"))
        );
        if (chapterUrl == null || chapterUrl.isBlank()) {
            throw new IllegalArgumentException("chapterUrl is required");
        }

        StorySite detected = detectStorySite(chapterUrl);
        List<StorySite> candidates = new ArrayList<>();
        candidates.add(detected);
        for (StorySite site : STORY_SITE_PRIORITY) {
            if (!candidates.contains(site)) {
                candidates.add(site);
            }
        }

        boolean saveToDisk = payload.get("saveToDisk") == null || Boolean.parseBoolean(String.valueOf(payload.get("saveToDisk")));
        List<String> errors = new ArrayList<>();
        for (StorySite site : candidates) {
            try {
                String html = fetchHtml(chapterUrl, 15000);
                List<String> images = parseChapterImages(site, html, chapterUrl);
                if (images.isEmpty()) {
                    errors.add("[" + siteName(site) + "] Không tìm thấy ảnh");
                    continue;
                }

                List<String> savedImages = List.of();
                String storageFolder = null;
                if (saveToDisk) {
                    SaveImagesResult saved = saveChapterImages(images, chapterUrl, payload);
                    savedImages = saved.savedImages();
                    storageFolder = saved.storageFolder();
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("site", siteName(site));
                result.put("chapterUrl", chapterUrl);
                result.put("imageCount", images.size());
                result.put("images", images);
                result.put("savedImages", savedImages);
                result.put("storageFolder", storageFolder);
                return result;
            } catch (Exception ex) {
                errors.add("[" + siteName(site) + "] " + ex.getMessage());
            }
        }
        throw new IllegalStateException("Không crawl được chapter: " + String.join(" | ", errors));
    }

    public Map<String, Object> cleanupTemporaryAssets(Map<String, Object> payload) {
        List<String> targets = new ArrayList<>();

        Object pathsRaw = payload.get("paths");
        if (pathsRaw instanceof List<?> list) {
            for (Object item : list) {
                String value = stringValue(item);
                if (!isBlank(value)) {
                    targets.add(value.trim());
                }
            }
        }

        String folder = stringValue(payload.get("storageFolder"));
        if (!isBlank(folder)) {
            targets.add(folder.trim());
        }
        String cover = stringValue(payload.get("coverLocal"));
        if (!isBlank(cover)) {
            targets.add(cover.trim());
        }

        int deleted = 0;
        int skipped = 0;
        for (String raw : targets) {
            if (deleteStorageTarget(raw)) {
                deleted++;
            } else {
                skipped++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("deleted", deleted);
        result.put("skipped", skipped);
        return result;
    }

    public String ensureCoverStoredLocally(String coverUrl, String refererUrl) {
        if (isBlank(coverUrl)) {
            return null;
        }
        if (coverUrl.startsWith("/storage/")) {
            return coverUrl;
        }
        if (coverUrl.startsWith("/uploads/")) {
            return "/storage/" + coverUrl.substring("/uploads/".length());
        }
        if (coverUrl.startsWith("storage/")) {
            return "/" + coverUrl;
        }
        if (coverUrl.startsWith("uploads/")) {
            return "/storage/" + coverUrl.substring("uploads/".length());
        }
        if (coverUrl.startsWith("http://") || coverUrl.startsWith("https://")) {
            return downloadCoverImage(coverUrl, refererUrl);
        }
        return null;
    }

    private Map<String, Object> crawlStoryWithSite(String inputUrl, StorySite site, boolean downloadCover) {
        String storyUrl = normalizeStoryUrl(inputUrl, site);
        String html = fetchHtml(storyUrl, 12000);

        String title = firstNonBlank(
                extractOgContent(html, "og:title"),
                stripHtml(extractFirstGroup(H1_PATTERN, html)),
                extractTagByClass(html, "title-detail"),
                extractTagByClass(html, "book-title"),
                extractTagByClass(html, "title")
        );
        if (title == null || title.isBlank()) {
            title = "";
        }
        title = normalizeExtractedText(title);

        String author = firstNonBlank(
                extractTextAfterLabel(html, "Tác giả"),
                extractTagByClass(html, "author"),
                extractTagByClass(html, "book-author")
        );
        author = normalizeExtractedText(author == null ? "" : author);

        String description = firstNonBlank(
                extractTagByClass(html, "detail-content"),
                extractTagByClass(html, "book-description"),
                extractTagByClass(html, "summary"),
                extractMetaDescription(html)
        );
        if (description == null) {
            description = "";
        }
        description = normalizeExtractedText(description);

        String cover = extractCoverUrl(html, storyUrl);
        List<Map<String, Object>> chapters = parseChapters(html, storyUrl, site);

        String coverLocal = null;
        if (downloadCover && cover != null && !cover.isBlank()) {
            String saved = downloadCoverImage(cover, storyUrl);
            if (saved != null) {
                coverLocal = saved;
            }
        }

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("title", title);
        series.put("author", author);
        series.put("description", description);
        series.put("cover_url", isBlank(cover) ? null : cover);
        series.put("cover_local", coverLocal);
        series.put("chapters", chapters);
        series.put("source_site", siteName(site));
        series.put("story_url", storyUrl);
        return series;
    }

    private List<Map<String, Object>> parseChapters(String html, String baseUrl, StorySite site) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        Matcher matcher = A_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String href = htmlEntityDecode(matcher.group(2));
            String text = collapseWhitespace(stripHtml(matcher.group(3)));
            if (href == null || href.isBlank()) {
                continue;
            }
            String absoluteUrl = resolveToAbsoluteUrl(href, baseUrl);
            if (absoluteUrl == null || absoluteUrl.isBlank()) {
                continue;
            }
            if (!looksLikeChapterLink(absoluteUrl, text)) {
                continue;
            }
            if (!seenUrls.add(absoluteUrl)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", text == null || text.isBlank() ? "Chapter" : text);
            item.put("url", absoluteUrl);
            chapters.add(item);
        }

        chapters.sort(chapterComparator());
        boolean reverse = site == StorySite.NETTRUYEN;
        if (reverse) {
            List<Map<String, Object>> reversed = new ArrayList<>();
            for (int i = chapters.size() - 1; i >= 0; i--) {
                reversed.add(chapters.get(i));
            }
            chapters = reversed;
        }

        List<Map<String, Object>> enriched = new ArrayList<>();
        Set<String> usedSlugs = new LinkedHashSet<>();
        for (int i = 0; i < chapters.size(); i++) {
            Map<String, Object> src = chapters.get(i);
            String title = stringValue(src.get("title"));
            String url = stringValue(src.get("url"));
            String baseSlug = slugify(firstNonBlank(title, "chapter-" + (i + 1)));
            String uniqueSlug = baseSlug;
            int slugAttempt = 2;
            while (usedSlugs.contains(uniqueSlug)) {
                uniqueSlug = baseSlug + "-" + slugAttempt;
                slugAttempt++;
            }
            usedSlugs.add(uniqueSlug);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ordinal", i + 1);
            item.put("title", title);
            item.put("url", url);
            item.put("slug", uniqueSlug);
            enriched.add(item);
        }
        return enriched;
    }

    private Comparator<Map<String, Object>> chapterComparator() {
        return (a, b) -> {
            String ta = stringValue(a.get("title"));
            String tb = stringValue(b.get("title"));
            String ua = stringValue(a.get("url"));
            String ub = stringValue(b.get("url"));

            int na = extractChapterNumber(firstNonBlank(ta, ua));
            int nb = extractChapterNumber(firstNonBlank(tb, ub));
            if (na != nb) {
                return Integer.compare(na, nb);
            }
            return firstNonBlank(ta, ua).compareToIgnoreCase(firstNonBlank(tb, ub));
        };
    }

    private int extractChapterNumber(String value) {
        if (value == null) return Integer.MAX_VALUE;
        Matcher m = CHAPTER_NUM_PATTERN.matcher(value);
        if (m.find()) {
            return parseIntSafe(m.group(1), Integer.MAX_VALUE);
        }
        Matcher m2 = NUMBER_PATTERN.matcher(value);
        if (m2.find()) {
            return parseIntSafe(m2.group(1), Integer.MAX_VALUE);
        }
        return Integer.MAX_VALUE;
    }

    private boolean looksLikeChapterLink(String href, String text) {
        String lowerHref = href.toLowerCase(Locale.ROOT);
        String lowerText = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lowerHref.contains("chap")
                || lowerHref.contains("chuong")
                || lowerHref.contains("chapter")
                || lowerHref.contains("/tap-")
                || lowerText.contains("chap")
                || lowerText.contains("chương")
                || lowerText.contains("chuong")
                || lowerText.contains("tập")
                || lowerText.contains("tap");
    }

    private List<String> parseChapterImages(StorySite site, String html, String chapterUrl) {
        List<String> images = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();

        Matcher matcher = IMG_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            Map<String, String> attrs = parseAttributes(tag);
            String src = firstNonBlank(
                    attrs.get("data-original"),
                    attrs.get("data-src"),
                    attrs.get("data-aload"),
                    attrs.get("data-lazy-src"),
                    attrs.get("src")
            );
            if (isBlank(src) || src.startsWith("data:")) {
                continue;
            }
            String absolute = resolveToAbsoluteUrl(src, chapterUrl);
            if (isBlank(absolute) || !looksLikeImageUrl(absolute)) {
                continue;
            }
            if (isLikelyDecorativeImage(attrs, absolute)) {
                continue;
            }
            if (dedupe.add(absolute)) {
                images.add(absolute);
            }
        }

        if (images.isEmpty() && site != StorySite.OTHER) {
            return parseChapterImages(StorySite.OTHER, html, chapterUrl);
        }
        return images;
    }

    private boolean looksLikeImageUrl(String url) {
        String lowered = url.toLowerCase(Locale.ROOT);
        return lowered.contains(".jpg")
                || lowered.contains(".jpeg")
                || lowered.contains(".png")
                || lowered.contains(".webp")
                || lowered.contains("image")
                || lowered.contains("img");
    }

    private boolean isLikelyDecorativeImage(Map<String, String> attrs, String imageUrl) {
        String loweredUrl = imageUrl == null ? "" : imageUrl.toLowerCase(Locale.ROOT);
        String cls = safeLower(attrs.get("class"));
        String alt = safeLower(attrs.get("alt"));
        String title = safeLower(attrs.get("title"));

        // Loáº¡i áº£nh Ä‘á»™ng/icon comment/emote thÆ°á»ng gáº·p.
        if (loweredUrl.contains(".gif")
                || loweredUrl.contains("icon")
                || loweredUrl.contains("avatar")
                || loweredUrl.contains("emot")
                || loweredUrl.contains("emoji")
                || loweredUrl.contains("sticker")
                || loweredUrl.contains("smile")
                || loweredUrl.contains("comment")
                || loweredUrl.contains("logo")
                || loweredUrl.contains("sprite")
                || loweredUrl.contains("button")
                || loweredUrl.contains("ads")
                || loweredUrl.contains("banner")) {
            return true;
        }

        if (cls.contains("icon")
                || cls.contains("avatar")
                || cls.contains("emot")
                || cls.contains("emoji")
                || cls.contains("sticker")
                || cls.contains("comment")
                || cls.contains("logo")
                || cls.contains("ads")
                || cls.contains("banner")) {
            return true;
        }

        if (alt.contains("icon")
                || alt.contains("avatar")
                || alt.contains("emoji")
                || alt.contains("sticker")
                || title.contains("icon")
                || title.contains("avatar")
                || title.contains("emoji")
                || title.contains("sticker")) {
            return true;
        }

        // Náº¿u áº£nh cÃ³ width/height khai bÃ¡o vÃ  quÃ¡ nhá» thÃ¬ bá» qua.
        int width = parseIntSafe(nullToEmpty(attrs.get("width")), -1);
        int height = parseIntSafe(nullToEmpty(attrs.get("height")), -1);
        if ((width > 0 && width < 180) || (height > 0 && height < 180)) {
            return true;
        }

        return false;
    }

    private SaveImagesResult saveChapterImages(List<String> images, String chapterUrl, Map<String, Object> payload) {
        try {
            Path chaptersRoot = uploadRoot.resolve("chapters").normalize();
            Files.createDirectories(chaptersRoot);

            String folderKey = normalizeChapterFolderName(payload);
            Path targetFolder = ensureUniqueFolder(chaptersRoot, folderKey);
            Files.createDirectories(targetFolder);

            List<String> saved = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                String imageUrl = images.get(i);
                String ext = guessImageExtension(imageUrl);
                String filename = String.format("%03d%s", i + 1, ext);
                Path dest = targetFolder.resolve(filename).normalize();
                if (!dest.startsWith(chaptersRoot)) {
                    continue;
                }
                if (downloadBinary(imageUrl, dest, chapterUrl)) {
                    saved.add(toStoragePath(dest));
                }
            }

            String folderStorage = saved.isEmpty() ? null : "/storage/chapters/" + targetFolder.getFileName();
            return new SaveImagesResult(saved, folderStorage);
        } catch (Exception ex) {
            return new SaveImagesResult(List.of(), null);
        }
    }

    private String downloadCoverImage(String coverUrl, String storyUrl) {
        try {
            Path coversRoot = uploadRoot.resolve("covers").normalize();
            Files.createDirectories(coversRoot);
            String ext = guessImageExtension(coverUrl);
            String filename = "cover_" + System.currentTimeMillis() + ext;
            Path dest = coversRoot.resolve(filename).normalize();
            if (!dest.startsWith(coversRoot)) {
                return null;
            }
            if (downloadBinary(coverUrl, dest, storyUrl)) {
                return "/storage/covers/" + filename;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean downloadBinary(String url, Path dest, String refererUrl) {
        List<String> referers = new ArrayList<>();
        referers.add(firstNonBlank(refererUrl, null));
        referers.add(extractOrigin(refererUrl));
        referers.add(extractOrigin(url));
        referers.add(null);

        LinkedHashSet<String> uniqueReferers = new LinkedHashSet<>();
        for (String candidate : referers) {
            uniqueReferers.add(candidate == null ? "" : candidate);
        }

        for (String rawReferer : uniqueReferers) {
            String currentReferer = rawReferer.isBlank() ? null : rawReferer;
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                        .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        .header("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
                        .header("Cache-Control", "no-cache");
                if (!isBlank(currentReferer)) {
                    builder.header("Referer", currentReferer);
                    String origin = extractOrigin(currentReferer);
                    if (!isBlank(origin)) {
                        builder.header("Origin", origin);
                    }
                }

                HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    continue;
                }
                byte[] body = response.body();
                if (body == null || body.length == 0) {
                    continue;
                }
                Files.write(dest, body);
                return true;
            } catch (Exception ignored) {
                // thá»­ phÆ°Æ¡ng Ã¡n referer tiáº¿p theo
            }
        }
        return false;
    }
    private String fetchHtml(String url, int timeoutMs) {
        try {
            List<String> referers = new ArrayList<>();
            String origin = extractOrigin(url);
            if (!isBlank(origin)) {
                referers.add(origin + "/");
            }
            referers.add("https://truyenqqno.com/");
            referers.add("https://nettruyen.com/");
            referers.add("");

            LinkedHashSet<String> uniqueReferers = new LinkedHashSet<>(referers);
            String lastError = null;

            for (String referer : uniqueReferers) {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
                        .header("Accept-Encoding", "gzip, deflate");

                if (!isBlank(referer)) {
                    builder.header("Referer", referer);
                    String refererOrigin = extractOrigin(referer);
                    if (!isBlank(refererOrigin)) {
                        builder.header("Origin", refererOrigin);
                    }
                }

                HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 400) {
                    lastError = "HTTP " + response.statusCode();
                    continue;
                }

                byte[] body = decodeBody(response.body(), response.headers().firstValue("content-encoding").orElse(""));
                String html = decodeHtml(body, response.headers().firstValue("content-type").orElse(""));
                if (html.length() < 100) {
                    lastError = "HTML quá ngắn";
                    continue;
                }
                return html;
            }

            throw new IllegalStateException(lastError == null ? "Không thể fetch HTML" : lastError);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể fetch HTML: " + ex.getMessage(), ex);
        }
    }
    private byte[] decodeBody(byte[] body, String encoding) throws IOException {
        String e = encoding == null ? "" : encoding.toLowerCase(Locale.ROOT);
        if (e.contains("gzip")) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return gis.readAllBytes();
            }
        }
        if (e.contains("deflate")) {
            try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(body))) {
                return iis.readAllBytes();
            }
        }
        return body;
    }

    private String decodeHtml(byte[] body, String contentTypeHeader) {
        Charset headerCharset = resolveCharsetFromContentType(contentTypeHeader);
        if (headerCharset != null) {
            return new String(body, headerCharset);
        }
        String previewLatin1 = new String(body, StandardCharsets.ISO_8859_1);
        Charset metaCharset = resolveCharsetFromMeta(previewLatin1);
        if (metaCharset != null) {
            return new String(body, metaCharset);
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private Charset resolveCharsetFromContentType(String contentTypeHeader) {
        if (isBlank(contentTypeHeader)) {
            return null;
        }
        Matcher m = Pattern.compile("charset\\s*=\\s*([a-zA-Z0-9_\\-]+)", Pattern.CASE_INSENSITIVE).matcher(contentTypeHeader);
        if (m.find()) {
            String charsetName = m.group(1).trim().replace("\"", "").replace("'", "");
            if (Charset.isSupported(charsetName)) {
                return Charset.forName(charsetName);
            }
        }
        return null;
    }

    private Charset resolveCharsetFromMeta(String htmlPreviewLatin1) {
        if (isBlank(htmlPreviewLatin1)) {
            return null;
        }
        Matcher m1 = Pattern.compile("<meta[^>]+charset\\s*=\\s*['\"]?([a-zA-Z0-9_\\-]+)['\"]?", Pattern.CASE_INSENSITIVE).matcher(htmlPreviewLatin1);
        if (m1.find()) {
            String charsetName = m1.group(1).trim();
            if (Charset.isSupported(charsetName)) {
                return Charset.forName(charsetName);
            }
        }
        Matcher m2 = Pattern.compile("content\\s*=\\s*['\"][^'\"]*charset=([a-zA-Z0-9_\\-]+)[^'\"]*['\"]", Pattern.CASE_INSENSITIVE).matcher(htmlPreviewLatin1);
        if (m2.find()) {
            String charsetName = m2.group(1).trim();
            if (Charset.isSupported(charsetName)) {
                return Charset.forName(charsetName);
            }
        }
        return null;
    }

    private StorySite detectStorySite(String url) {
        try {
            String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
            if (host.contains("nettruyen") || host.contains("nagalandcricket")
                    || (host.contains("truyen") && host.contains("tranh"))) {
                return StorySite.NETTRUYEN;
            }
            if (host.contains("truyenqqno") || host.contains("truyenqq") || host.contains("qqno")) {
                return StorySite.TRUYENQQNO;
            }
            if (host.contains("truyenvn") || host.contains("truyenfull") || host.contains("truyensub")) {
                return StorySite.TRUYENVN;
            }
            if (host.contains("blogtruyen")) {
                return StorySite.BLOGTRUYEN;
            }
            if (host.contains("goctruyentranhvui")) {
                return StorySite.GOCTRUYENTRANHVUI;
            }
            return StorySite.OTHER;
        } catch (Exception ex) {
            return StorySite.OTHER;
        }
    }

    private String normalizeStoryUrl(String url, StorySite site) {
        try {
            URL u = new URL(url);
            String path = u.getPath();
            if (site == StorySite.NETTRUYEN) {
                path = path.replaceAll("(?i)/chap-\\d+.*$", "")
                        .replaceAll("(?i)/chapter-\\d+.*$", "")
                        .replaceAll("(?i)/chuong-\\d+.*$", "");
            } else if (site == StorySite.TRUYENQQNO) {
                path = path.replaceAll("(?i)-chap-\\d+\\.html$", "")
                        .replaceAll("(?i)-chapter-\\d+\\.html$", "")
                        .replaceAll("(?i)/chap-\\d+\\.html$", "");
            } else {
                path = path.replaceAll("(?i)-chap-\\d+\\.html$", "")
                        .replaceAll("(?i)-chapter-\\d+\\.html$", "")
                        .replaceAll("(?i)/chap-\\d+.*$", "")
                        .replaceAll("(?i)/chapter-\\d+.*$", "")
                        .replaceAll("(?i)/chuong-\\d+.*$", "")
                        .replaceAll("(?i)/ch-\\d+.*$", "");
            }
            path = path.replaceAll("(?i)\\.html?$", "");
            String query = u.getQuery();
            return u.getProtocol() + "://" + u.getHost() + path + (isBlank(query) ? "" : ("?" + query));
        } catch (Exception ex) {
            return url;
        }
    }

    private String extractMetaDescription(String html) {
        return extractMetaContentByName(html, "description");
    }

    private String extractOgContent(String html, String property) {
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            Map<String, String> attrs = parseAttributes(tag);
            String p = attrs.get("property");
            if (property.equalsIgnoreCase(p)) {
                return htmlEntityDecode(attrs.get("content"));
            }
        }
        return null;
    }

    private String extractMetaContentByName(String html, String name) {
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            Map<String, String> attrs = parseAttributes(tag);
            String p = attrs.get("name");
            if (name.equalsIgnoreCase(p)) {
                return htmlEntityDecode(attrs.get("content"));
            }
        }
        return null;
    }

    private String extractTagByClass(String html, String className) {
        String regex = "<([a-z0-9]+)[^>]*class\\s*=\\s*([\"'])[^\"']*\\b" + Pattern.quote(className) + "\\b[^\"']*\\2[^>]*>(.*?)</\\1>";
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return collapseWhitespace(stripHtml(m.group(3)));
        }
        return null;
    }

    private String extractTextAfterLabel(String html, String label) {
        String regex = Pattern.quote(label) + "\\s*[:ï¼š]?\\s*</[^>]+>\\s*<[^>]+>(.*?)</";
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return collapseWhitespace(stripHtml(m.group(1)));
        }
        return null;
    }

    private String extractCoverUrl(String html, String baseUrl) {
        String og = extractOgContent(html, "og:image");
        if (!isBlank(og)) {
            return resolveToAbsoluteUrl(og, baseUrl);
        }

        Matcher matcher = IMG_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            Map<String, String> attrs = parseAttributes(tag);
            String cls = nullToEmpty(attrs.get("class"));
            String src = firstNonBlank(
                    attrs.get("data-original"),
                    attrs.get("data-src"),
                    attrs.get("src")
            );
            if (isBlank(src)) continue;
            String c = cls.toLowerCase(Locale.ROOT);
            String s = src.toLowerCase(Locale.ROOT);
            if (c.contains("cover") || c.contains("poster") || c.contains("book") || s.contains("cover") || s.contains("poster")) {
                return resolveToAbsoluteUrl(src, baseUrl);
            }
        }
        return null;
    }

    private Map<String, String> parseAttributes(String tag) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(tag);
        while (matcher.find()) {
            attrs.put(matcher.group(1).toLowerCase(Locale.ROOT), htmlEntityDecode(matcher.group(3)));
        }
        return attrs;
    }

    private String extractFirstGroup(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String stripHtml(String value) {
        if (value == null) return null;
        String cleaned = STRIP_TAGS_PATTERN.matcher(value).replaceAll(" ");
        return normalizeExtractedText(cleaned);
    }

    private String collapseWhitespace(String value) {
        if (value == null) return null;
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String htmlEntityDecode(String value) {
        if (value == null) return null;
        String v = value;
        v = v.replace("&amp;", "&");
        v = v.replace("&quot;", "\"");
        v = v.replace("&#39;", "'");
        v = v.replace("&apos;", "'");
        v = v.replace("&lt;", "<");
        v = v.replace("&gt;", ">");
        return v;
    }

    private String normalizeExtractedText(String value) {
        if (value == null) {
            return null;
        }
        String decoded = collapseWhitespace(htmlEntityDecode(value));
        return tryFixMojibake(decoded);
    }

    private String tryFixMojibake(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (!looksLikeMojibake(value)) {
            return value;
        }
        try {
            String candidate = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            if (candidate.isBlank()) {
                return value;
            }
            int originalScore = vietnameseScore(value);
            int candidateScore = vietnameseScore(candidate);
            return candidateScore >= originalScore ? candidate : value;
        } catch (Exception ignored) {
            return value;
        }
    }

    private boolean looksLikeMojibake(String value) {
        return value.contains("Ãƒ")
                || value.contains("Ã†")
                || value.contains("Ã¢â‚¬â€œ")
                || value.contains("Ã¢â‚¬")
                || value.contains("Ã°Å¸");
    }

    private int vietnameseScore(String value) {
        int score = 0;
        String lower = value.toLowerCase(Locale.ROOT);
        for (char ch : lower.toCharArray()) {
            if ("ÄƒÃ¢Ä‘ÃªÃ´Æ¡Æ°Ã¡Ã áº£Ã£áº¡áº¯áº±áº³áºµáº·áº¥áº§áº©áº«áº­Ã©Ã¨áº»áº½áº¹áº¿á»á»ƒá»…á»‡Ã­Ã¬á»‰Ä©á»‹Ã³Ã²á»Ãµá»á»‘á»“á»•á»—á»™á»›á»á»Ÿá»¡á»£ÃºÃ¹á»§Å©á»¥á»©á»«á»­á»¯á»±Ã½á»³á»·á»¹á»µ".indexOf(ch) >= 0) {
                score += 3;
            } else if (ch >= 'a' && ch <= 'z') {
                score += 1;
            }
        }
        return score;
    }

    private String resolveToAbsoluteUrl(String rawUrl, String baseUrl) {
        if (isBlank(rawUrl)) return "";
        try {
            return new URL(new URL(baseUrl), rawUrl).toString();
        } catch (Exception ex) {
            return rawUrl;
        }
    }

    private String extractOrigin(String rawUrl) {
        if (isBlank(rawUrl)) {
            return null;
        }
        try {
            URL u = new URL(rawUrl);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception ex) {
            return null;
        }
    }

    private String guessImageExtension(String imageUrl) {
        try {
            String decoded = URLDecoder.decode(imageUrl, StandardCharsets.UTF_8);
            int q = decoded.indexOf('?');
            if (q >= 0) decoded = decoded.substring(0, q);
            int idx = decoded.lastIndexOf('.');
            if (idx >= 0) {
                String ext = decoded.substring(idx).toLowerCase(Locale.ROOT);
                if (ext.length() <= 5 && (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".webp") || ext.equals(".gif"))) {
                    return ext;
                }
            }
        } catch (Exception ignored) {
        }
        return ".jpg";
    }

    private String normalizeChapterFolderName(Map<String, Object> payload) {
        String storySlug = firstNonBlank(stringValue(payload.get("storySlug")), stringValue(payload.get("storyTitle")));
        String chapterSlug = firstNonBlank(
                stringValue(payload.get("chapterId")),
                stringValue(payload.get("chapterSlug")),
                stringValue(payload.get("chapterTitle"))
        );
        String left = slugify(firstNonBlank(storySlug, "story"));
        String right = slugify(firstNonBlank(chapterSlug, "chapter"));
        String key = (left + "-" + right).replaceAll("-{2,}", "-");
        return key.isBlank() ? ("chapter-" + (System.currentTimeMillis() / 1000)) : key;
    }

    private Path ensureUniqueFolder(Path root, String key) {
        Path target = root.resolve(key).normalize();
        int attempt = 1;
        while (Files.exists(target) && attempt <= 3) {
            target = root.resolve(key + "-" + System.currentTimeMillis() + "-" + attempt).normalize();
            attempt++;
        }
        return target;
    }

    private boolean deleteStorageTarget(String rawPath) {
        if (isBlank(rawPath)) {
            return false;
        }

        String normalized = rawPath.trim().replace("\\", "/");
        int q = normalized.indexOf('?');
        if (q >= 0) {
            normalized = normalized.substring(0, q);
        }
        int hash = normalized.indexOf('#');
        if (hash >= 0) {
            normalized = normalized.substring(0, hash);
        }

        String relative = normalized;
        if (relative.startsWith("/storage/")) {
            relative = relative.substring("/storage/".length());
        } else if (relative.startsWith("storage/")) {
            relative = relative.substring("storage/".length());
        } else if (relative.startsWith("/uploads/")) {
            relative = relative.substring("/uploads/".length());
        } else if (relative.startsWith("uploads/")) {
            relative = relative.substring("uploads/".length());
        } else if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        if (isBlank(relative)) {
            return false;
        }

        Path target = uploadRoot.resolve(relative).normalize();
        if (!target.startsWith(uploadRoot)) {
            return false;
        }

        Path chaptersRoot = uploadRoot.resolve("chapters").normalize();
        Path coversRoot = uploadRoot.resolve("covers").normalize();

        try {
            if (Files.isDirectory(target)) {
                if (!target.startsWith(chaptersRoot)) {
                    return false;
                }
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
                }
                return true;
            }

            if (!target.startsWith(chaptersRoot) && !target.startsWith(coversRoot)) {
                return false;
            }
            return Files.deleteIfExists(target);
        } catch (Exception ex) {
            return false;
        }
    }

    private String toStoragePath(Path filePath) {
        String normalized = filePath.toAbsolutePath().normalize().toString().replace("\\", "/");
        String uploads = uploadRoot.toString().replace("\\", "/");
        int idx = normalized.indexOf(uploads);
        if (idx >= 0) {
            String relative = normalized.substring(idx + uploads.length());
            if (!relative.startsWith("/")) {
                relative = "/" + relative;
            }
            return ("/storage" + relative).replaceAll("/+", "/");
        }
        return normalized;
    }

    private String slugify(String value) {
        if (value == null) return "item";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    private String siteName(StorySite site) {
        return switch (site) {
            case NETTRUYEN -> "nettruyen";
            case TRUYENQQNO -> "truyenqqno";
            case TRUYENVN -> "truyenvn";
            case BLOGTRUYEN -> "blogtruyen";
            case GOCTRUYENTRANHVUI -> "goctruyentranhvui";
            case OTHER -> "other";
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeLower(String value) {
        return nullToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record SaveImagesResult(List<String> savedImages, String storageFolder) {
    }
}
