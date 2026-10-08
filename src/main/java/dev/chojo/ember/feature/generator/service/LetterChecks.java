/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.LetterPart;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Turns the rows the editor sends for a letter into a letter a station may keep.
 *
 * <p>A letter is written with the block editor pages use, narrowed to what prints: texts, pictures,
 * lines, gaps, signature lines and blocks stacked in a column ({@link ContentBlockService.Scope#LETTER}).
 * A row holds at most three columns, at any depth. A picture is the station logo or an image of the
 * station's media library, and the texts of the header and the footer are short.
 *
 * <p>A signature line stands in the body only, names its signer, has a short text under it and may say
 * what its signer confirms, at most {@link SignatureStatementChecks#MAX_STATEMENT} characters. Two
 * lines may name the same signer as alternatives, for members of different audiences; what is refused
 * here is only a pair that every member with some number of guardians would get together. Whether a
 * pair meets in the document of one particular member is checked when it is generated for them.
 */
@Singleton
public class LetterChecks {
    /** The longest text under a signature line, as short as a text of the header or the footer. */
    static final int MAX_SIGNATURE_TEXT = LetterPart.LETTERHEAD.maxText();

    /** The most blocks a letter holds in all. */
    static final int MAX_BLOCKS = 300;

    private final ContentBlockService blocks;
    private final MediaLibraryService mediaLibrary;

    @Inject
    public LetterChecks(ContentBlockService blocks, MediaLibraryService mediaLibrary) {
        this.blocks = blocks;
        this.mediaLibrary = mediaLibrary;
    }

    /**
     * Checks a letter.
     *
     * @param stationId the station that keeps the template
     * @param request   what the editor sent
     * @return the letter as it is to be written
     */
    public LetterContent letter(int stationId, DocumentTemplateRequest request) {
        var page = Objects.requireNonNullElse(request.page(), LetterPage.defaults())
                .tidied();
        if (!page.withinBounds()) throw DocumentRefusal.DOCUMENT_TEMPLATE_PAGE_OUT_OF_BOUNDS.raise();
        var letter = new LetterContent(
                rows(stationId, request.header(), LetterPart.LETTERHEAD),
                rows(stationId, request.footer(), LetterPart.LETTERHEAD),
                rows(stationId, request.body(), LetterPart.BODY),
                page);
        long count = Stream.of(letter.header(), letter.footer(), letter.body())
                .flatMap(LetterContent::blocks)
                .count();
        if (count > MAX_BLOCKS) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(MAX_BLOCKS));
        }
        requireSignersOnce(letter);
        return letter;
    }

    /**
     * The kinds of block an empty cell of a part offers: what a letter prints and the part takes, without
     * the empty cell itself and the column a split stacks.
     *
     * @param part a part of a letter
     * @return the kinds, in the order the block types are declared
     */
    public static List<CellContentType> offeredIn(LetterPart part) {
        return Arrays.stream(CellContentType.values())
                .filter(ContentBlockService.Scope.LETTER::takes)
                .filter(type -> type != CellContentType.EMPTY && type != CellContentType.NESTED_ROWS)
                .filter(type -> part.signatures() || type != CellContentType.SIGNATURE)
                .toList();
    }

    private List<ContentRow> rows(int stationId, @Nullable List<BlockRowRequest> sent, LetterPart part) {
        var data = BlockRowRequest.toRowData(Objects.requireNonNullElse(sent, List.of()));
        blocks.requireFits(stationId, data, ContentBlockService.Scope.LETTER);
        var rows = ContentBlockService.rowsOf(data);
        requireColumns(rows, part.maxColumns());
        if (!part.signatures()
                && LetterContent.blocks(rows).anyMatch(cell -> cell.contentType() == CellContentType.SIGNATURE)) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_SIGNATURE_OUTSIDE_BODY.raise();
        }
        LetterContent.blocks(rows).forEach(cell -> requireBlock(stationId, cell, part.maxText()));
        return rows;
    }

    /**
     * Refuses two signature lines for one signer that every member with some number of guardians would
     * get together, which no choice of audiences can keep apart.
     */
    private static void requireSignersOnce(LetterContent letter) {
        for (int guardians = 0; guardians <= SignatureRole.GUARDIANS_CHECKED; guardians++) {
            LetterLayout.requireSignersOnce(
                    letter, MemberView.anyMember(guardians), DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_TWICE);
        }
    }

    private static void requireColumns(List<ContentRow> rows, int maxColumns) {
        for (var row : rows) {
            if (row.cells().size() > maxColumns) throw DocumentRefusal.DOCUMENT_TEMPLATE_TOO_MANY_CELLS.raise();
            for (var cell : row.cells()) {
                if (cell.config() instanceof CellConfig.NestedRowsConfig nested) {
                    requireColumns(ContentRows.read(nested.rows()), maxColumns);
                }
            }
        }
    }

    private void requireBlock(int stationId, ContentCell cell, int maxText) {
        switch (cell.contentType()) {
            case MARKDOWN -> {
                if (cell.content().length() > maxText) {
                    throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(maxText));
                }
            }
            case IMAGE -> requirePicture(stationId, cell.content());
            case SIGNATURE -> requireSignature(cell);
            default -> {}
        }
    }

    private static void requireSignature(ContentCell cell) {
        if (!(cell.config() instanceof CellConfig.SignatureConfig signature) || signature.signer() == null) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_MISSING.raise();
        }
        if (cell.content().length() > MAX_SIGNATURE_TEXT) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(MAX_SIGNATURE_TEXT));
        }
        SignatureStatementChecks.checked(signature.statement());
    }

    private void requirePicture(int stationId, String content) {
        if (!pictureReachable(stationId, content)) throw DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE.raise();
    }

    /**
     * Whether a picture block shows something from a media library: the station logo, or an image in
     * that library.
     *
     * @param stationId the station whose media library the template takes its pictures from
     * @param content   what the picture block names
     * @return whether the picture is there
     */
    public boolean pictureReachable(int stationId, String content) {
        if (ContentCell.STATION_LOGO.equals(content)) return true;
        return !content.isBlank()
                && mediaLibrary
                        .findByHash(stationId, content)
                        .map(file -> file.mimeType().startsWith("image/"))
                        .orElse(false);
    }
}
