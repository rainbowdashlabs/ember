/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Typed payload for {@code inventory_container_history.details}, one variant per
 * {@link ContainerEventKind}. The kind is carried by the sibling {@code event_kind}
 * column, so the JSON itself stays free of a discriminator and keeps the exact
 * shape written before this record existed.
 */
public sealed interface ContainerHistoryDetails
        permits ContainerHistoryDetails.Created,
                ContainerHistoryDetails.Renamed,
                ContainerHistoryDetails.Moved,
                ContainerHistoryDetails.Deleted {

    /**
     * Shared Jackson mapper for the history detail variants.
     */
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /**
     * Logger for unparsable legacy payloads.
     */
    Logger log = getLogger(ContainerHistoryDetails.class);

    /**
     * Parses the {@code details} JSONB for the given event kind.
     *
     * @param eventKind the kind of the history row, selecting the variant
     * @param json      raw JSONB string, may be null or blank
     * @return the parsed details, or {@code null} when absent or unreadable
     */
    static ContainerHistoryDetails parse(ContainerEventKind eventKind, String json) {
        if (eventKind == null || json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, eventKind.detailsClass());
        } catch (Exception e) {
            log.warn("Failed to parse container history details for kind {}: {}", eventKind, json, e);
            return null;
        }
    }

    /**
     * Returns the {@link ContainerEventKind} this payload belongs to.
     */
    ContainerEventKind eventKind();

    /**
     * Serialises this payload to JSONB form.
     */
    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize container history details", e);
        }
    }

    /**
     * Payload of a {@link ContainerEventKind#CREATED} row.
     *
     * @param name     the name the container was created with
     * @param parentId the parent it was created under, omitted when it was created as a root
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Created(String name, Integer parentId) implements ContainerHistoryDetails {
        @Override
        public ContainerEventKind eventKind() {
            return ContainerEventKind.CREATED;
        }
    }

    /**
     * Payload of a {@link ContainerEventKind#RENAMED} row.
     *
     * @param from the previous name
     * @param to   the new name
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Renamed(String from, String to) implements ContainerHistoryDetails {
        @Override
        public ContainerEventKind eventKind() {
            return ContainerEventKind.RENAMED;
        }
    }

    /**
     * Payload of a {@link ContainerEventKind#MOVED} row. Both ends are nullable and
     * are written out explicitly, because {@code null} means "was/is a root".
     *
     * @param from the previous parent id, or {@code null} when it was a root
     * @param to   the new parent id, or {@code null} when it became a root
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Moved(Integer from, Integer to) implements ContainerHistoryDetails {
        @Override
        public ContainerEventKind eventKind() {
            return ContainerEventKind.MOVED;
        }
    }

    /**
     * Payload of a {@link ContainerEventKind#DELETED} row. The row itself keeps
     * {@code container_id = NULL}, so the id is repeated here.
     *
     * @param id   the id the deleted container had
     * @param name the name the deleted container had
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Deleted(int id, String name) implements ContainerHistoryDetails {
        @Override
        public ContainerEventKind eventKind() {
            return ContainerEventKind.DELETED;
        }
    }
}
