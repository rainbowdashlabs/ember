/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.news.entity.NewsAttachment;
import dev.chojo.ember.feature.news.repository.NewsAttachmentRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What a news entry hands over, and how it travels.
 *
 * <p>Attachments hang off the entry rather than its body, so they are unaffected by how the body
 * was written. They are added by the two serialisers that need them to leave the station, at the
 * moment those serialise: a feed reader expects an enclosure, and a federation partner expects
 * markdown. Neither is ever written back into the entry.
 */
@Singleton
public class NewsAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(NewsAttachmentService.class);

    private final NewsAttachmentRepository repository;
    private final MediaLibraryService media;
    private final StationRepository stationRepository;
    private final Api apiConfig;

    @Inject
    public NewsAttachmentService(
            NewsAttachmentRepository repository,
            MediaLibraryService media,
            StationRepository stationRepository,
            Api apiConfig) {
        this.repository = repository;
        this.media = media;
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
    }

    public List<NewsAttachment> list(int newsId) {
        return repository.findByNews(newsId);
    }

    public Map<Integer, List<NewsAttachment>> listFor(List<Integer> newsIds) {
        return repository.findByNewsIds(newsIds);
    }

    public Optional<NewsAttachment> find(int attachmentId) {
        return repository.findById(attachmentId);
    }

    /**
     * Attaches a media file to the entry. The file has to belong to the same station, because an
     * attachment is a reference into that station's library and nothing else.
     */
    public NewsAttachment attach(int newsId, int stationId, int fileId, String label) {
        var file = media.findFile(fileId).orElseThrow(() -> new BadRequestResponse("Unknown file"));
        if (file.stationId() != stationId) throw new BadRequestResponse("File belongs to another station");
        var attachment = repository.attach(newsId, fileId, label);
        log.info("File {} attached to news {} in station {}", fileId, newsId, stationId);
        return attachment;
    }

    public boolean relabel(int attachmentId, String label) {
        return repository.updateLabel(attachmentId, label == null || label.isBlank() ? null : label);
    }

    public void reorder(int newsId, List<Integer> attachmentIds) {
        repository.reorder(newsId, attachmentIds);
        log.debug("News {} reordered to {} attachment(s)", newsId, attachmentIds.size());
    }

    public boolean detach(int attachmentId) {
        return repository.detach(attachmentId);
    }

    /**
     * The absolute address of an attachment, which is what a feed reader and a partner station
     * both need: the file is addressed by hash on the origin station's public media route, so the
     * link resolves for a reader who has never heard of this instance's sessions.
     */
    public String absoluteUrl(int stationId, NewsAttachment attachment) {
        UUID stationUid = stationRepository.resolveUid(stationId);
        return apiConfig.baseUrl() + "/api/v1/public/media/" + stationUid + "/" + attachment.contentHash();
    }

    /**
     * The same list as {@link #withAttachmentLinks}, appended to the rendered HTML instead. Both
     * are needed because the federation payload carries both, and a partner renders whichever of
     * the two its surface reads: appending to only one would make the attachments invisible on
     * the other side.
     */
    public String withAttachmentLinksHtml(String html, int newsId, int stationId) {
        var attachments = repository.findByNews(newsId);
        if (attachments.isEmpty()) return html;
        var out = new StringBuilder(html == null ? "" : html);
        out.append("\n<hr />\n<ul>");
        for (var attachment : attachments) {
            out.append("\n<li><a href=\"")
                    .append(escapeHtml(absoluteUrl(stationId, attachment)))
                    .append("\">")
                    .append(escapeHtml(attachment.displayName()))
                    .append("</a></li>");
        }
        return out.append("\n</ul>\n").toString();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Returns the entry's markdown with its attachments appended as a link list, for the
     * serialisers that have no field of their own to put them in.
     *
     * <p>This is what keeps the federation wire format frozen. A partner receives working links
     * back to the origin station without a single field being added to the payload, so no partner
     * on an older build is paused by a surface mismatch.
     *
     * <p>The list carries no heading: the payload is read by partners in whatever language the
     * origin writes in, and a word chosen here would be wrong for most of them.
     */
    public String withAttachmentLinks(String markdown, int newsId, int stationId) {
        var attachments = repository.findByNews(newsId);
        if (attachments.isEmpty()) return markdown;
        var out = new StringBuilder(markdown == null ? "" : markdown);
        out.append("\n\n---\n");
        for (var attachment : attachments) {
            out.append("\n- [")
                    .append(attachment.displayName())
                    .append("](")
                    .append(absoluteUrl(stationId, attachment))
                    .append(")");
        }
        return out.append("\n").toString();
    }
}
