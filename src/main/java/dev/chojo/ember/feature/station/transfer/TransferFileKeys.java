/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * Where a file copied by a station transfer lands on the destination.
 *
 * <p>Some categories name their files by their content or by a value their rows carry along, so a key
 * arrives as it left. A category listed here names its files by the id of a row instead, in the key's
 * first segment, and the table import gives that row a new id. Such a key moves to the id the row got on
 * the destination, and a key whose row did not arrive is left behind rather than copied under an id that
 * may belong to another row there. Keys of the same category whose first segment does not have the
 * category's shape, such as the sealed member document files named by their SHA-256, arrive as they left.
 *
 * <p>The pictures of document templates are drawn again whenever they are missing, so they could stay
 * behind. They are moved like the rest instead: the copy costs no more than any other file and spares
 * drawing every template again on its first view.
 */
final class TransferFileKeys {
    private static final Map<StorageCategory, RowKey> KEYED_BY_ROW_ID = Map.ofEntries(
            entry(StorageCategory.MEMBER_DOCUMENTS, RowKey.bare("member_document")),
            entry(StorageCategory.KB_FILES, RowKey.bare("kb_file")),
            entry(StorageCategory.DOCUMENT_TEMPLATES, RowKey.bare("document_template_pdf_original")),
            entry(StorageCategory.FONTS, new RowKey("document_font", Pattern.compile("(\\d+)(?:-web)?"))),
            entry(StorageCategory.MOVEMENT_DOCUMENTS, RowKey.bare("item_movement_document")),
            entry(StorageCategory.BOARD_ATTACHMENTS, RowKey.bare("board_ticket")),
            entry(StorageCategory.IMAGE_LOST_AND_FOUND, RowKey.bare("lost_and_found_item")),
            entry(StorageCategory.IMAGE_QUIZ_QUESTION, RowKey.bare("quiz_question")),
            entry(StorageCategory.IMAGE_DOCUMENT_TEMPLATE_PICTURE, RowKey.bare("document_template")),
            entry(StorageCategory.IMAGE_KB_ICON, RowKey.prefixed("kb_folder", KbIconService.KEY_PREFIX)),
            entry(StorageCategory.IMAGE_KB_FILE_PICTURE, RowKey.prefixed("kb_file", KbFilePictureService.KEY_PREFIX)));

    private TransferFileKeys() {}

    /**
     * Whether the files of a category can land under another key than the one they left with.
     *
     * @param category the category
     * @return true when the category names its files by the id of a row
     */
    static boolean renumbers(StorageCategory category) {
        return KEYED_BY_ROW_ID.containsKey(category);
    }

    /**
     * The destination key for a file listed by the source.
     *
     * @param category  the category the file belongs to
     * @param sourceKey the category-relative key on the source
     * @param idMap     the source-to-destination ids of the rows imported so far
     * @return the category-relative key on the destination, or empty when the row the file belongs to did
     * not arrive
     */
    static Optional<String> destinationKey(StorageCategory category, String sourceKey, IdRemapper idMap) {
        RowKey rowKey = KEYED_BY_ROW_ID.get(category);
        if (rowKey == null) return Optional.of(sourceKey);
        int slash = sourceKey.indexOf('/');
        String head = slash < 0 ? sourceKey : sourceKey.substring(0, slash);
        Matcher id = rowKey.head().matcher(head);
        if (!id.matches()) return Optional.of(sourceKey);
        String before = head.substring(0, id.start(1));
        String after = sourceKey.substring(id.end(1));
        return rowId(id.group(1))
                .flatMap(sourceId -> idMap.find(rowKey.table(), sourceId))
                .map(destinationId -> before + destinationId + after);
    }

    private static Optional<Integer> rowId(String digits) {
        try {
            return Optional.of(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * How a category names its files by a row.
     *
     * @param table the table whose renumbered ids name the files
     * @param head  the shape of a key's first segment, with the row id as its first group
     */
    private record RowKey(String table, Pattern head) {
        static RowKey bare(String table) {
            return prefixed(table, "");
        }

        static RowKey prefixed(String table, String prefix) {
            return new RowKey(table, Pattern.compile(Pattern.quote(prefix) + "(\\d+)"));
        }
    }
}
