/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentContainer;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.repository.ContentContainerRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * Saving and reading a container of blocks, for every feature that authors with them.
 *
 * <p>Pages, news entries and knowledge-base articles all come through here, which is what keeps
 * one editor rather than a look-alike per feature. What differs between them is only which blocks
 * they may use, and that is a single check on the way in.
 */
@Singleton
public class ContentBlockService {
    private static final Logger log = LoggerFactory.getLogger(ContentBlockService.class);

    private final ContentContainerRepository repository;

    @Inject
    public ContentBlockService(ContentContainerRepository repository) {
        this.repository = repository;
    }

    public Optional<ContentContainer> find(int containerId) {
        return repository.findById(containerId);
    }

    public ContentContainer create(int stationId) {
        var container = repository.create(stationId);
        log.info("Content container {} created in station {}", container.id(), stationId);
        return container;
    }

    /**
     * The container that already exists, or a fresh one. Used wherever a feature turns something
     * into blocks for the first time.
     */
    public ContentContainer ensure(int stationId, Integer containerId) {
        if (containerId != null) {
            var existing = repository.findById(containerId);
            if (existing.isPresent()) return existing.get();
        }
        return create(stationId);
    }

    public List<ContentRow> loadRows(int containerId) {
        return repository.loadRows(containerId);
    }

    /**
     * Replaces everything in the container with the supplied rows.
     *
     * <p>{@code scope} decides which blocks are accepted. Enforcing it here rather than only in the
     * chooser is what makes it a rule: the chooser is a convenience, and a request that names a
     * withheld block is refused whatever the chooser offered.
     */
    public void save(int containerId, List<RowData> rows, Scope scope) {
        for (var row : rows) {
            for (var cell : row.cells()) {
                requireAllowed(cell.contentType(), scope);
                requireNestedAllowed(cell.config(), scope);
            }
        }

        repository.deleteRows(containerId);
        for (var row : rows) {
            int rowId = repository.insertRow(containerId, row.sortOrder());
            for (var cell : row.cells()) {
                repository.insertCell(
                        rowId,
                        cell.sortOrder(),
                        cell.widthPercent(),
                        cell.contentType(),
                        cell.content(),
                        cell.config());
            }
        }
        log.info("Container {} saved ({} rows)", containerId, rows.size());
    }

    /**
     * Copies every row and cell of one container into another. Used when a page is duplicated.
     */
    public void copyInto(int sourceContainerId, int targetContainerId) {
        for (var row : repository.loadRows(sourceContainerId)) {
            int rowId = repository.insertRow(targetContainerId, row.sortOrder());
            for (var cell : row.cells()) {
                repository.insertCell(
                        rowId,
                        cell.sortOrder(),
                        cell.widthPercent(),
                        cell.contentType(),
                        cell.content(),
                        cell.config());
            }
        }
    }

    /**
     * Deletes the container and its blocks. The container is the owned side of the relation, so
     * whatever owned it has to say so: the reference points the wrong way for the database to
     * clean up on its own, and a container nobody deletes is a row that accumulates forever.
     */
    public void delete(Integer containerId) {
        if (containerId == null) return;
        repository.delete(containerId);
    }

    private void requireAllowed(CellContentType type, Scope scope) {
        if (scope == Scope.PAGE || type.availableInArticles()) return;
        throw new BadRequestResponse("This block is not available in an article: " + type);
    }

    /**
     * Nested rows carry their cells inside a cell config rather than as rows of their own, so the
     * allowlist has to recurse into them or a withheld block slips through one level down.
     */
    private void requireNestedAllowed(CellConfig config, Scope scope) {
        if (scope == Scope.PAGE) return;
        if (!(config instanceof CellConfig.NestedRowsConfig nested) || nested.rows() == null) return;
        for (JsonNode row : nested.rows()) {
            var cells = row.path("cells");
            if (!cells.isArray()) continue;
            for (JsonNode cell : cells) {
                var typeNode = cell.path("contentType");
                if (!typeNode.isString()) continue;
                try {
                    requireAllowed(CellContentType.valueOf(typeNode.asString()), scope);
                } catch (IllegalArgumentException ignored) {
                    // An unknown block name is not a withheld one; the config parser rejects it.
                }
            }
        }
    }

    /**
     * What is being authored, which is all the save path needs to know about the caller.
     */
    public enum Scope {
        /**
         * A public page, which may use every block.
         */
        PAGE,
        /**
         * A news entry or a knowledge-base article, which may not use the page-only blocks.
         */
        ARTICLE
    }

    public record RowData(int sortOrder, List<CellData> cells) {}

    public record CellData(
            int sortOrder, double widthPercent, CellContentType contentType, String content, CellConfig config) {}
}
