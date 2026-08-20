/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.repository.PageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Everything in a station that points at a file in its media library.
 *
 * <p>Three kinds of reference exist and all three are collected here:
 * <ul>
 *   <li><b>Cells</b> of the station's pages, which name a file in their content or their config.</li>
 *   <li><b>Text bodies</b> that a member may drop an image into. An inline image carries the
 *       content hash in its URL, so a body contributes its references by having 64-character hex
 *       tokens pulled out of it.</li>
 *   <li><b>Attachments</b>, which name a file outright rather than by having it read out of text.</li>
 * </ul>
 *
 * <p>{@link #TEXT_BODIES} is an explicit list, and that is the price of this design worth stating
 * plainly: <b>a feature that gains a markdown body and forgets to register it here will have its
 * images pruned.</b> The alternative, recording a reference at save time, needs every writer of
 * every body to be honest instead, and there are more writers than there are columns.
 */
@Singleton
public class MediaReferenceRegistry {
    private static final Logger log = LoggerFactory.getLogger(MediaReferenceRegistry.class);

    /**
     * A lowercase hex SHA-256, which is how a media file is addressed inside a URL.
     */
    private static final Pattern CONTENT_HASH = Pattern.compile("[0-9a-f]{64}");

    /**
     * Every text body in the application a member can insert a media image into. Each query
     * takes {@code :station_id} and returns one {@code body} column.
     *
     * <p>Add a row here whenever a feature gains a markdown body that the media browser reaches.
     */
    private static final List<String> TEXT_BODIES = List.of(
            "SELECT content_markdown AS body FROM news WHERE station_id = :station_id;",
            "SELECT content_html AS body FROM news WHERE station_id = :station_id;",
            """
            SELECT c.text_content AS body
            FROM kb_file_content c
            JOIN kb_file f ON f.id = c.file_id
            WHERE f.station_id = :station_id;""",
            """
            SELECT t.description AS body
            FROM board_ticket t
            JOIN board b ON b.id = t.board_id
            WHERE b.station_id = :station_id;""",
            "SELECT description AS body FROM station_event WHERE station_id = :station_id;",
            "SELECT description AS body FROM event_template WHERE station_id = :station_id;");

    /**
     * References that name their file outright rather than mentioning it in prose. They are read
     * as joins, so no pattern has to match for them to be found. Each query takes
     * {@code :station_id} and returns an {@code id} and a {@code content_hash} column.
     */
    private static final List<String> STATED_REFERENCES = List.of("""
            SELECT f.id, f.content_hash
            FROM news_attachment a
            JOIN station_file f ON f.id = a.file_id
            JOIN news n ON n.id = a.news_id
            WHERE n.station_id = :station_id;""", """
            SELECT f.id, f.content_hash
            FROM station_page p
            JOIN station_file f ON f.id = p.og_image_id
            WHERE p.station_id = :station_id;""");

    private final PageRepository pageRepository;

    @Inject
    public MediaReferenceRegistry(PageRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    /**
     * Every media reference in the station, as the set of tokens that identify a file: its
     * content hash, and for the cells that were written before hashes, its numeric id.
     */
    public Set<String> collect(int stationId) {
        Set<String> out = new HashSet<>();
        for (var cell : pageRepository.findAllCellsByStation(stationId)) {
            collectFromCell(cell.contentType(), cell.content(), cell.config(), out);
        }
        for (String sql : TEXT_BODIES) {
            collectFromText(sql, stationId, out);
        }
        collectStated(stationId, out);
        return out;
    }

    /**
     * Adds every reference a single cell makes: the content of an {@code IMAGE} cell, plus any
     * image field inside its config, including the ones buried in nested rows.
     */
    public void collectFromCell(CellContentType type, String content, CellConfig config, Set<String> out) {
        if (type == CellContentType.IMAGE && content != null && !content.isBlank()) {
            out.add(content.trim());
        }
        if (config == null) return;
        try {
            walkJsonForImageRefs(CellConfig.MAPPER.valueToTree(config), out);
        } catch (Exception e) {
            log.debug("Failed to walk cell config for file refs", e);
        }
    }

    private void collectFromText(String sql, int stationId, Set<String> out) {
        try {
            var bodies = query(sql)
                    .single(call().bind("station_id", stationId))
                    .map(row -> row.getString("body"))
                    .all();
            for (String body : bodies) {
                if (body == null || body.isBlank()) continue;
                var matcher = CONTENT_HASH.matcher(body);
                while (matcher.find()) {
                    out.add(matcher.group());
                }
            }
        } catch (Exception e) {
            // A body that cannot be read must never let pruning conclude "nothing references
            // this". Fail loudly and let the caller decide, rather than deleting on bad data.
            throw new IllegalStateException("Failed to collect media references from a registered body", e);
        }
    }

    /**
     * How many news entries hand this file out. An attachment is the one kind of reference that
     * is stated rather than inferred, and the database refuses to let a delete break it, so this
     * is what lets the delete say what it is about to break before it fails.
     */
    public int handedOutBy(int fileId) {
        return query("SELECT count(*) AS cnt FROM news_attachment WHERE file_id = :file_id;")
                .single(call().bind("file_id", fileId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    private void collectStated(int stationId, Set<String> out) {
        for (String sql : STATED_REFERENCES) {
            query(sql)
                    .single(call().bind("station_id", stationId))
                    .map(row -> {
                        out.add(String.valueOf(row.getInt("id")));
                        String hash = row.getString("content_hash");
                        if (hash != null && !hash.isBlank()) out.add(hash);
                        return null;
                    })
                    .all();
        }
    }

    private void walkJsonForImageRefs(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            var typeNode = node.get("contentType");
            if (typeNode != null && "IMAGE".equals(typeNode.asString())) {
                var contentNode = node.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    String v = contentNode.asString();
                    if (v != null && !v.isBlank()) out.add(v.trim());
                }
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String name = entry.getKey();
                var value = entry.getValue();
                if (("imageHash".equals(name) || "ogImageId".equals(name)) && value != null && !value.isNull()) {
                    out.add(value.asString());
                } else if ("imageHashes".equals(name) && value != null && value.isArray()) {
                    value.forEach(v -> out.add(v.asString()));
                } else {
                    walkJsonForImageRefs(value, out);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> walkJsonForImageRefs(child, out));
        }
    }
}
