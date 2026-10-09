/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.GuardianCondition;
import dev.chojo.ember.feature.content.route.BlockCellRequest;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the demo's letter templates are laid out from, as the template editor sends them: rows top to
 * bottom, each a few blocks left to right with their share of the width.
 *
 * <p>Every demo letter is printed on the same page and under the same letterhead: the station logo on the
 * left and the youth fire brigade of the district with the station's name on the right.
 */
final class DemoLetterBlocks {

    /** The margins and the size of the body text every demo letter is printed with. */
    static final LetterPage PAGE = new LetterPage(15, 10, 22, 20, 11, null, null, null);

    private static final String LETTERHEAD = """
            **Kreisjugendfeuerwehr Musterstadt**\\
            {{station.name}}""";

    private DemoLetterBlocks() {}

    /**
     * A block with what it shows and its settings, before it is given its width.
     *
     * @param type              the kind of block
     * @param content           its text, or what its picture shows
     * @param config            its settings
     * @param restriction       who the block is shown to, or null for everybody
     * @param guardianCondition which guardians the member must have for it to be printed, or null for any
     */
    record Block(
            CellContentType type,
            String content,
            CellConfig config,
            @Nullable RestrictionAudience restriction,
            @Nullable GuardianCondition guardianCondition) {

        private Block(CellContentType type, String content, CellConfig config) {
            this(type, content, config, null, null);
        }

        /**
         * @param audience who the block is shown to
         * @return the same block, shown only to them
         */
        Block shownTo(RestrictionAudience audience) {
            return new Block(type, content, config, audience, guardianCondition);
        }

        /**
         * @param condition which guardians the member must have
         * @return the same block, printed only for a member who has them
         */
        Block when(GuardianCondition condition) {
            return new Block(type, content, config, restriction, condition);
        }
    }

    /**
     * A block of a row with its share of the width.
     *
     * @param widthPercent how much of the row it takes
     * @param block        the block
     */
    record Cell(double widthPercent, Block block) {}

    static Cell cell(double widthPercent, Block block) {
        return new Cell(widthPercent, block);
    }

    static Block text(String markdown) {
        return new Block(CellContentType.MARKDOWN, markdown, CellContentType.MARKDOWN.emptyConfig());
    }

    static Block empty() {
        return new Block(CellContentType.EMPTY, "", CellContentType.EMPTY.emptyConfig());
    }

    static Block logo() {
        return new Block(CellContentType.IMAGE, ContentCell.STATION_LOGO, CellContentType.IMAGE.emptyConfig());
    }

    /**
     * A signature line.
     *
     * @param signer    who signs on it
     * @param below     what is printed under the line
     * @param statement what the signer confirms, or null for the default statement of the signer
     * @return the block
     */
    static Block signature(SignatureRole signer, String below, @Nullable String statement) {
        return new Block(CellContentType.SIGNATURE, below, new CellConfig.SignatureConfig(signer, statement));
    }

    /**
     * @param groupId the group
     * @return the audience of the members of that group
     */
    static RestrictionAudience group(int groupId) {
        return new RestrictionAudience(List.of(), List.of(groupId), List.of(), List.of(), RestrictionMode.AND);
    }

    /**
     * @param userType the kind of member
     * @return the audience of every member of that kind
     */
    static RestrictionAudience userType(StationUserType userType) {
        return new RestrictionAudience(List.of(userType), List.of(), List.of(), List.of(), RestrictionMode.AND);
    }

    /** @return the letterhead across the top of every page: the station logo and the name of the station */
    static List<BlockRowRequest> letterhead() {
        return rows(List.of(List.of(cell(20, logo()), cell(40, empty()), cell(40, text(LETTERHEAD)))));
    }

    /** The rows top to bottom, each its blocks left to right, numbered in that order. */
    static List<BlockRowRequest> rows(List<List<Cell>> rows) {
        var placed = new ArrayList<BlockRowRequest>();
        for (int index = 0; index < rows.size(); index++) {
            placed.add(new BlockRowRequest(index, cells(rows.get(index))));
        }
        return placed;
    }

    private static List<BlockCellRequest> cells(List<Cell> cells) {
        var placed = new ArrayList<BlockCellRequest>();
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            var block = cell.block();
            placed.add(new BlockCellRequest(
                    index,
                    cell.widthPercent(),
                    block.type().name(),
                    block.content(),
                    CellConfig.MAPPER.valueToTree(block.config()),
                    block.restriction(),
                    block.guardianCondition()));
        }
        return placed;
    }
}
