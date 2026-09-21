/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gathers the pictures of an article so its PDF can show them.
 *
 * <p>An article points at its pictures by URL, and those URLs answer only to a signed-in browser,
 * so the PDF compiler cannot fetch them. This reads every picture the station itself holds, puts it
 * next to the document under a local name like {@code img-1.webp}, and points the markdown there,
 * whether the picture is written as markdown or, as the editor does for one given a width, as an
 * {@code <img>} tag.
 * Two kinds of picture are the station's own: one pasted into a written article, and one from the
 * media library that a block of a rich article shows.
 *
 * <p>Whatever it cannot place keeps its URL, which the conversion then turns into the picture's alt
 * text: a picture of another station, one on the open web, one that has since been deleted. A
 * missing picture never stops the export.
 */
@Singleton
public class KbPdfPictures {
    private static final Logger log = LoggerFactory.getLogger(KbPdfPictures.class);

    /** Wide enough to print sharply across the text width, small enough to keep the PDF light. */
    private static final int PRINT_WIDTH = 1024;

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("(!\\[[^\\]]*]\\(\\s*<?)([^)\\s>]+)");
    private static final Pattern HTML_IMAGE = Pattern.compile("(<img\\b[^>]*?\\ssrc=\")([^\"]+)");
    private static final Pattern ARTICLE_IMAGE = Pattern.compile("^/(?:api/v1/)?(?:public/)?kb/images/([^/?#]+)");
    private static final Pattern PUBLIC_ARTICLE_IMAGE = Pattern.compile("^/api/v1/public/kb/([^/]+)/images/([^/?#]+)");
    private static final Pattern MEDIA_FILE = Pattern.compile("^/(?:api/v1/)?(?:public/)?media/([^/]+)/([^/?#]+)");
    private static final Map<String, String> PRINTABLE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final KbImageService articleImages;
    private final MediaLibraryService mediaLibrary;
    private final StationRepository stationRepository;

    @Inject
    public KbPdfPictures(
            KbImageService articleImages, MediaLibraryService mediaLibrary, StationRepository stationRepository) {
        this.articleImages = articleImages;
        this.mediaLibrary = mediaLibrary;
        this.stationRepository = stationRepository;
    }

    /**
     * Markdown whose pictures point at files placed next to it, and those files by name.
     *
     * @param markdown the markdown with its picture sources rewritten
     * @param pictures the picture bytes, keyed by the local name the markdown now uses
     */
    public record Placed(String markdown, Map<String, byte[]> pictures) {}

    /**
     * Places every picture of the station that the markdown shows.
     *
     * @param stationId the station whose pictures may be placed
     * @param markdown  the article body
     * @return the rewritten body with the pictures it now points at
     */
    public Placed place(int stationId, String markdown) {
        var stationUid = stationRepository.resolveUid(stationId).toString();
        var namesByUrl = new LinkedHashMap<String, String>();
        var pictures = new LinkedHashMap<String, byte[]>();
        Function<String, String> localName = url -> namesByUrl.computeIfAbsent(url, u -> read(stationId, stationUid, u)
                .map(picture -> {
                    String local = "img-" + (pictures.size() + 1) + "." + picture.extension();
                    pictures.put(local, picture.data());
                    return local;
                })
                .orElse(u));
        String placed = rewriteSources(rewriteSources(markdown, MARKDOWN_IMAGE, localName), HTML_IMAGE, localName);
        return new Placed(placed, pictures);
    }

    /**
     * Replaces the source each match of {@code pattern} names. Group 1 is what precedes the source
     * and stays as it is, group 2 the source itself.
     */
    private static String rewriteSources(String text, Pattern pattern, Function<String, String> localName) {
        Matcher matcher = pattern.matcher(text);
        var out = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group(1) + localName.apply(matcher.group(2));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private record Picture(byte[] data, String extension) {}

    private Optional<Picture> read(int stationId, String stationUid, String url) {
        try {
            return readStationPicture(stationId, stationUid, url);
        } catch (RuntimeException e) {
            log.warn("Could not read picture {} of station {} for a PDF export", url, stationId, e);
            return Optional.empty();
        }
    }

    private Optional<Picture> readStationPicture(int stationId, String stationUid, String url) {
        var article = ARTICLE_IMAGE.matcher(url);
        if (article.find()) return articlePicture(stationId, article.group(1));

        var publicArticle = PUBLIC_ARTICLE_IMAGE.matcher(url);
        if (publicArticle.find() && sameStation(stationUid, publicArticle.group(1))) {
            return articlePicture(stationId, publicArticle.group(2));
        }

        var media = MEDIA_FILE.matcher(url);
        if (media.find() && sameStation(stationUid, media.group(1))) {
            return mediaLibrary
                    .readVariant(stationId, media.group(2), PRINT_WIDTH, "image/webp")
                    .flatMap(file -> printable(file.data(), file.contentType()));
        }
        return Optional.empty();
    }

    private Optional<Picture> articlePicture(int stationId, String imageId) {
        return articleImages
                .read(stationId, imageId, PRINT_WIDTH)
                .flatMap(image -> printable(image.data(), image.contentType()));
    }

    private static Optional<Picture> printable(byte[] data, String contentType) {
        return Optional.ofNullable(contentType)
                .map(PRINTABLE_TYPES::get)
                .map(extension -> new Picture(data, extension));
    }

    private static boolean sameStation(String stationUid, String urlUid) {
        try {
            return UUID.fromString(urlUid).toString().equals(stationUid);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
