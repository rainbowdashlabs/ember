/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.knowledgebase.entity.UrlMetadata;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Looks up what a linked page or video calls itself, so a knowledge-base entry pointing at it can
 * name and describe itself without the member typing it out, and so the link becomes findable by
 * more than its URL.
 *
 * <p>Every lookup is best-effort: an unreachable page, a slow one, or one that says nothing about
 * itself yields empty metadata rather than failing the entry being created.
 *
 * <p>The address comes from whoever creates the entry and part of the answer is stored where they
 * can read it, so an unchecked lookup would let them reach whatever the server can reach and read
 * back what it found. Every address is therefore put to {@link RemoteUrlValidator} first, and
 * redirects are walked here rather than by the client, because a public address that redirects into
 * a private one would otherwise pass a check made only at the start.
 */
@Singleton
public class KbLinkMetadataService {
    private static final Logger log = LoggerFactory.getLogger(KbLinkMetadataService.class);
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESC_PATTERN = Pattern.compile(
            "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESC_PATTERN_ALT = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']description[\"']", Pattern.CASE_INSENSITIVE);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_SCANNED_CHARACTERS = 10_000;
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient httpClient;
    private final RemoteUrlValidator urlValidator;

    @Inject
    public KbLinkMetadataService(RemoteUrlValidator urlValidator) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                urlValidator);
    }

    /**
     * Builds the service on a caller-supplied client, so the lookups can be driven without
     * reaching the network.
     *
     * @param httpClient   the client every lookup is sent through
     * @param urlValidator decides which addresses may be reached at all
     */
    KbLinkMetadataService(HttpClient httpClient, RemoteUrlValidator urlValidator) {
        this.httpClient = httpClient;
        this.urlValidator = urlValidator;
    }

    private static String firstGroup(Pattern pattern, String body) {
        var matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String extractJsonString(String json, String key) {
        return firstGroup(Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\""), json);
    }

    /**
     * Reads the title and description a linked page declares about itself.
     *
     * @param url the page to look up
     * @return the metadata, with {@code null} fields for whatever the page did not declare
     */
    public UrlMetadata fetchUrlMetadata(String url) {
        try {
            String body = get(url);
            if (body == null || body.isBlank()) return new UrlMetadata(null, null);
            if (body.length() > MAX_SCANNED_CHARACTERS) body = body.substring(0, MAX_SCANNED_CHARACTERS);

            String description = firstGroup(META_DESC_PATTERN, body);
            if (description == null) description = firstGroup(META_DESC_PATTERN_ALT, body);
            return new UrlMetadata(firstGroup(TITLE_PATTERN, body), description);
        } catch (Exception e) {
            log.debug("Failed to fetch URL metadata for {}: {}", url, e.getMessage());
            return new UrlMetadata(null, null);
        }
    }

    /**
     * Reads the title and channel of a linked YouTube video as a single searchable line.
     *
     * @param youtubeUrl the video to look up
     * @return the title and channel, or {@code null} when the video could not be looked up
     */
    public String fetchYoutubeMetadata(String youtubeUrl) {
        try {
            String body = get("https://www.youtube.com/oembed?url="
                    + URLEncoder.encode(youtubeUrl, StandardCharsets.UTF_8)
                    + "&format=json");
            if (body == null) return null;
            String title = extractJsonString(body, "title");
            String author = extractJsonString(body, "author_name");
            return (title != null ? title : "") + " " + (author != null ? author : "");
        } catch (Exception e) {
            log.debug("Failed to fetch YouTube metadata for {}: {}", youtubeUrl, e.getMessage());
            return null;
        }
    }

    private String get(String url) throws Exception {
        String target = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!urlValidator.isAllowed(target)) {
                log.debug("Refusing to look up {}: not a public address", target);
                return null;
            }
            HttpResponse<String> response = send(target);
            if (response.statusCode() == 200) return response.body();

            String location = redirect(response);
            if (location == null) return null;
            target = URI.create(target).resolve(location).toString();
        }
        log.debug("Refusing to look up {}: too many redirects", url);
        return null;
    }

    private HttpResponse<String> send(String url) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; EmberBot/1.0)")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String redirect(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 300 || status > 399) return null;
        return response.headers().firstValue("location").orElse(null);
    }
}
