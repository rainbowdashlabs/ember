/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a copied file lands: under the new id of the row it belongs to, as it left when it is not named
 * by a row, and nowhere when its row did not arrive.
 */
class TransferFileKeysTest {

    private static IdRemapper documentsMovedFrom(int sourceId, int destinationId) {
        var idMap = new IdRemapper();
        idMap.put("member_document", sourceId, destinationId);
        return idMap;
    }

    @Test
    void aDocumentFileMovesToTheDocumentsNewId() {
        var idMap = documentsMovedFrom(7, 42);

        assertEquals(
                Optional.of("42/file/content"),
                TransferFileKeys.destinationKey(StorageCategory.MEMBER_DOCUMENTS, "7/file/content", idMap));
        assertEquals(
                Optional.of("42/thumb/orig.png"),
                TransferFileKeys.destinationKey(StorageCategory.MEMBER_DOCUMENTS, "7/thumb/orig.png", idMap));
    }

    @Test
    void aFileWhoseDocumentDidNotArriveStaysBehind() {
        assertEquals(
                Optional.empty(),
                TransferFileKeys.destinationKey(
                        StorageCategory.MEMBER_DOCUMENTS, "8/file/content", documentsMovedFrom(7, 42)));
        assertEquals(
                Optional.empty(),
                TransferFileKeys.destinationKey(
                        StorageCategory.MEMBER_DOCUMENTS, "99999999999/file", documentsMovedFrom(7, 42)));
    }

    @Test
    void aSealedFileKeepsItsKey() {
        String key = "sealed/" + "ab".repeat(32) + "/content";

        assertEquals(
                Optional.of(key),
                TransferFileKeys.destinationKey(StorageCategory.MEMBER_DOCUMENTS, key, documentsMovedFrom(7, 42)));
    }

    @Test
    void aCategoryNotNamedByRowsKeepsItsKeys() {
        assertEquals(
                Optional.of("7/orig.png"),
                TransferFileKeys.destinationKey(StorageCategory.MEDIA_FILES, "7/orig.png", documentsMovedFrom(7, 42)));
    }
}
