/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.PageRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentContainer;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.content.entity.GuardianCondition;
import dev.chojo.ember.feature.content.repository.ContentContainerRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
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
    public ContentContainer create(@Nullable Integer stationId) {
        var container = repository.create(stationId);
        log.info("Content container {} created in station {}", container.id(), stationId);
        return container;
    }

    /**
     * The container that already exists, or a fresh one. Used wherever a feature turns something
     * into blocks for the first time.
     */
    public ContentContainer ensure(@Nullable Integer stationId, @Nullable Integer containerId) {
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
        requireFits(stationId, rows, scope);

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
    public void delete(@Nullable Integer containerId) {
        if (containerId == null) return;
        repository.delete(containerId);
        log.info("Deleted content container {} and its blocks", containerId);
    }

    /**
     * Refuses rows holding a block the scope does not take, or naming something its readers may not see.
     *
     * <p>The check {@link #save} runs before it writes anything, for a caller that keeps its rows
     * elsewhere: a letter keeps its header, footer and body beside the template rather than in a container.
     *
     * @param stationId the station the rows are written at, or null for the instance
     * @param rows      the rows
     * @param scope     what is being authored
     */
    public void requireFits(@Nullable Integer stationId, List<RowData> rows, Scope scope) {
        for (var row : rows) {
            for (var cell : row.cells()) {
                requireFits(stationId, cell.contentType(), cell.config(), scope);
            }
        }
    }

    private void requireFits(@Nullable Integer stationId, CellContentType type, CellConfig config, Scope scope) {
        if (!scope.takes(type)) throw scope.notTaken(type).raise();
        for (var reference : references) {
            reference.requireReachable(stationId, scope.audience(), config);
        }
        requireNestedFits(stationId, config, scope);
    }

    /**
     * Nested rows carry their cells inside a cell config rather than as rows of their own, so the
     * checks have to recurse into them or a withheld block slips through one level down. A cell
     * naming a block that does not exist is not a withheld one, and is left out by the reader.
     */
    private void requireNestedFits(@Nullable Integer stationId, CellConfig config, Scope scope) {
        if (!(config instanceof CellConfig.NestedRowsConfig nested)) return;
        for (var row : ContentRows.read(nested.rows())) {
            for (var cell : row.cells()) {
                requireFits(stationId, cell.contentType(), cell.config(), scope);
            }
        }
    }

    /**
     * Rows as they are kept outside a container, each row with its lines between columns and each cell
     * with what it says and who it is shown to.
     *
     * @param rows the rows as an author sent them
     * @return the same rows as the records pages are read into, without ids
     */
    public static List<ContentRow> rowsOf(List<RowData> rows) {
        var out = new ArrayList<ContentRow>();
        for (var row : rows) {
            var cells = row.cells().stream()
                    .map(cell -> new ContentCell(
                            0,
                            0,
                            cell.sortOrder(),
                            cell.widthPercent(),
                            cell.contentType(),
                            cell.content(),
                            cell.config(),
                            cell.restriction(),
                            cell.guardianCondition()))
                    .toList();
            out.add(new ContentRow(0, 0, row.sortOrder(), cells, row.columnLines()));
        }
        return List.copyOf(out);
    }

    /**
     * What is being authored, which is all the save path needs to know about the caller.
     */
    public enum Scope {
        /**
         * A public page, which may use every block but those only a letter prints, and whose blocks may
         * only name what is public.
         */
        PAGE(BlockAudience.PUBLIC, PageRefusal.CONTENT_BLOCK_ONLY_ON_PAGES),
        /**
         * A news entry or a knowledge-base article, which may not use the page-only blocks, and whose
         * blocks may name what every member of the station may see.
         */
        ARTICLE(BlockAudience.MEMBERS, PageRefusal.CONTENT_BLOCK_ONLY_ON_PAGES),
        /**
         * A letter template, printed for one member at a time: text, pictures, lines, gaps, signature
         * lines, boxes a signer fills in and blocks stacked in a column, nothing that only works on a
         * screen.
         */
        LETTER(BlockAudience.MEMBERS, DocumentRefusal.DOCUMENT_TEMPLATE_BLOCK_NOT_TAKEN);

        private static final Set<CellContentType> LETTER_BLOCKS = EnumSet.of(
                CellContentType.EMPTY,
                CellContentType.MARKDOWN,
                CellContentType.IMAGE,
                CellContentType.DIVIDER,
                CellContentType.SPACER,
                CellContentType.SIGNATURE,
                CellContentType.FILL_IN,
                CellContentType.NESTED_ROWS);

        private final BlockAudience audience;
        private final Refusal notTaken;

        Scope(BlockAudience audience, Refusal notTaken) {
            this.audience = audience;
            this.notTaken = notTaken;
        }

        /**
         * Who reads what is authored here.
         */
        public BlockAudience audience() {
            return audience;
        }

        /**
         * @param type a kind of block
         * @return whether what is authored here may hold it
         */
        public boolean takes(CellContentType type) {
            return switch (this) {
                case PAGE -> !type.lettersOnly();
                case ARTICLE -> type.availableInArticles();
                case LETTER -> LETTER_BLOCKS.contains(type);
            };
        }

        /**
         * @param type a kind of block this scope does not take
         * @return the refusal for it
         */
        public Refusal notTaken(CellContentType type) {
            if (this != LETTER && type.lettersOnly()) return PageRefusal.CONTENT_BLOCK_ONLY_IN_LETTERS;
            return notTaken;
        }
    }

    /**
     * One row as an author sent it.
     *
     * @param columnLines whether a line is drawn between its columns; only a letter keeps it
     */
    public record RowData(int sortOrder, List<CellData> cells, boolean columnLines) {

        /** A row without lines between its columns. */
        public RowData(int sortOrder, List<CellData> cells) {
            this(sortOrder, cells, false);
        }
    }

    /**
     * One block as an author sent it.
     *
     * @param restriction       who the block is shown to, or null for everybody; only a letter keeps it
     * @param guardianCondition which guardians the member must have for the block to be printed, or null;
     *                          only a letter keeps it
     */
    public record CellData(
            int sortOrder,
            double widthPercent,
            CellContentType contentType,
            String content,
            CellConfig config,
            @Nullable RestrictionAudience restriction,
            @Nullable GuardianCondition guardianCondition) {

        /** A block shown to everybody. */
        public CellData(
                int sortOrder, double widthPercent, CellContentType contentType, String content, CellConfig config) {
            this(sortOrder, widthPercent, contentType, content, config, null, null);
        }
    }
}
