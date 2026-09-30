/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.content.entity.BlockAudience;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final Set<BlockReferences> references;

    @Inject
    public ContentBlockService(ContentContainerRepository repository, Set<BlockReferences> references) {
        this.repository = repository;
        this.references = references;
    }

    public Optional<ContentContainer> find(int containerId) {
        return repository.findById(containerId);
    }

    /**
     * A fresh container for the station, or for the instance itself when {@code stationId} is
     * {@code null}: content read in every station belongs to none of them.
     */
    public ContentContainer create(Integer stationId) {
        var container = repository.create(stationId);
        log.info("Content container {} created in station {}", container.id(), stationId);
        return container;
    }

    /**
     * The container that already exists, or a fresh one. Used wherever a feature turns something
     * into blocks for the first time.
     */
    public ContentContainer ensure(Integer stationId, Integer containerId) {
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
     * <p>{@code scope} decides which blocks are accepted and what a block naming a news entry or an
     * appointment may name: only what every reader of that content may see. Enforcing it here rather
     * than only in the picker is what makes it a rule: the picker is a convenience, and a request
     * that names a withheld block or item is refused whatever the picker offered.
     */
    public void save(int containerId, List<RowData> rows, Scope scope) {
        Integer stationId = repository
                .findById(containerId)
                .map(ContentContainer::stationId)
                .orElse(null);
        for (var row : rows) {
            for (var cell : row.cells()) {
                requireFits(stationId, cell.contentType(), cell.config(), scope);
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
        var rows = repository.loadRows(sourceContainerId);
        for (var row : rows) {
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
        log.info(
                "Copied {} row(s) from container {} into container {}",
                rows.size(),
                sourceContainerId,
                targetContainerId);
    }

    /**
     * Deletes the container and its blocks. The container is the owned side of the relation, so
     * whatever owned it has to say so: the reference points the wrong way for the database to
     * clean up on its own, and a container nobody deletes is a row that accumulates forever.
     */
    public void delete(Integer containerId) {
        if (containerId == null) return;
        repository.delete(containerId);
        log.info("Deleted content container {} and its blocks", containerId);
    }

    private void requireFits(Integer stationId, CellContentType type, CellConfig config, Scope scope) {
        requireAllowed(type, scope);
        for (var reference : references) {
            reference.requireReachable(stationId, scope.audience(), config);
        }
        requireNestedFits(stationId, config, scope);
    }

    private void requireAllowed(CellContentType type, Scope scope) {
        if (scope == Scope.PAGE || type.availableInArticles()) return;
        throw new BadRequestResponse("This block is not available in an article: " + type);
    }

    /**
     * Nested rows carry their cells inside a cell config rather than as rows of their own, so the
     * checks have to recurse into them or a withheld block slips through one level down. A cell
     * naming a block that does not exist is not a withheld one, and is left to the config parser.
     */
    private void requireNestedFits(Integer stationId, CellConfig config, Scope scope) {
        if (!(config instanceof CellConfig.NestedRowsConfig nested) || nested.rows() == null) return;
        for (JsonNode row : nested.rows()) {
            var cells = row.path("cells");
            if (!cells.isArray()) continue;
            for (JsonNode cell : cells) {
                var type = knownType(cell.path("contentType"));
                if (type != null) requireFits(stationId, type, CellConfig.parse(type, cell.path("config")), scope);
            }
        }
    }

    private static CellContentType knownType(JsonNode name) {
        if (!name.isString()) return null;
        return Arrays.stream(CellContentType.values())
                .filter(type -> type.name().equals(name.asString()))
                .findFirst()
                .orElse(null);
    }

    /**
     * What is being authored, which is all the save path needs to know about the caller.
     */
    public enum Scope {
        /**
         * A public page, which may use every block, and whose blocks may only name what is public.
         */
        PAGE(BlockAudience.PUBLIC),
        /**
         * A news entry or a knowledge-base article, which may not use the page-only blocks, and whose
         * blocks may name what every member of the station may see.
         */
        ARTICLE(BlockAudience.MEMBERS);

        private final BlockAudience audience;

        Scope(BlockAudience audience) {
            this.audience = audience;
        }

        /**
         * Who reads what is authored here.
         */
        public BlockAudience audience() {
            return audience;
        }
    }

    public record RowData(int sortOrder, List<CellData> cells) {}

    public record CellData(
            int sortOrder, double widthPercent, CellContentType contentType, String content, CellConfig config) {}
}
