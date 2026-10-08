/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where a file copied by a station transfer lands on the destination.
 *
 * <p>Most categories name their files by their content or by a value their rows carry along, so a key
 * arrives as it left. A category listed here names its files by the id of a row instead, in the key's
 * first segment, and the table import gives that row a new id. Such a key moves to the id the row got on
 * the destination, and a key whose row did not arrive is left behind rather than copied under an id that
 * may belong to another row there. Keys of the same category that do not start with an id, such as the
 * sealed member document files named by their SHA-256, arrive as they left.
 */
final class TransferFileKeys {
    private static final Map<StorageCategory, String> KEYED_BY_ROW_ID =
            Map.of(StorageCategory.MEMBER_DOCUMENTS, "member_document");
    private static final Pattern ROW_ID = Pattern.compile("\\d+");

    private TransferFileKeys() {}

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
        String table = KEYED_BY_ROW_ID.get(category);
        if (table == null) return Optional.of(sourceKey);
        int slash = sourceKey.indexOf('/');
        String head = slash < 0 ? sourceKey : sourceKey.substring(0, slash);
        if (!ROW_ID.matcher(head).matches()) return Optional.of(sourceKey);
        String rest = sourceKey.substring(head.length());
        return rowId(head).flatMap(id -> idMap.find(table, id)).map(id -> id + rest);
    }

    private static Optional<Integer> rowId(String digits) {
        try {
            return Optional.of(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
