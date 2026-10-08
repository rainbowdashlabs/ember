/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

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
        assertEquals(
                Optional.of("file-7-1712345678/original.png"),
                TransferFileKeys.destinationKey(
                        StorageCategory.IMAGE_KB_IMAGE, "file-7-1712345678/original.png", movedFrom("kb_file", 7, 42)));
        assertEquals(
                Optional.of("logo/original.png"),
                TransferFileKeys.destinationKey(
                        StorageCategory.IMAGE_STATION_LOGO, "logo/original.png", movedFrom("station", 7, 42)));
    }

    @ParameterizedTest
    @MethodSource("filesNamedByRows")
    void aFileNamedByARowMovesToTheRowsNewId(
            StorageCategory category, String table, String sourceKey, String destinationKey) {
        assertEquals(
                Optional.of(destinationKey),
                TransferFileKeys.destinationKey(category, sourceKey, movedFrom(table, 7, 42)));
    }

    @ParameterizedTest
    @MethodSource("filesNamedByRows")
    void aFileWhoseRowDidNotArriveStaysBehind(StorageCategory category, String table, String sourceKey) {
        assertEquals(Optional.empty(), TransferFileKeys.destinationKey(category, sourceKey, movedFrom(table, 8, 42)));
    }

    @Test
    void aRowOfAnotherTableDoesNotMoveAFile() {
        assertEquals(
                Optional.empty(),
                TransferFileKeys.destinationKey(
                        StorageCategory.IMAGE_KB_ICON, "folder-7/original.png", movedFrom("kb_file", 7, 42)));
    }

    @Test
    void aKeyOfAnotherShapeKeepsItsKey() {
        assertEquals(
                Optional.of("file-7/original.png"),
                TransferFileKeys.destinationKey(
                        StorageCategory.IMAGE_KB_ICON, "file-7/original.png", movedFrom("kb_folder", 7, 42)));
        assertEquals(
                Optional.of("7-print"),
                TransferFileKeys.destinationKey(StorageCategory.FONTS, "7-print", movedFrom("document_font", 7, 42)));
    }

    private static Stream<Arguments> filesNamedByRows() {
        return Stream.of(
                Arguments.of(StorageCategory.KB_FILES, "kb_file", "7/content", "42/content"),
                Arguments.of(StorageCategory.KB_FILES, "kb_file", "7/content-gz", "42/content-gz"),
                Arguments.of(
                        StorageCategory.DOCUMENT_TEMPLATES,
                        "document_template_pdf_original",
                        "7/original",
                        "42/original"),
                Arguments.of(StorageCategory.FONTS, "document_font", "7", "42"),
                Arguments.of(StorageCategory.FONTS, "document_font", "7-web", "42-web"),
                Arguments.of(StorageCategory.MOVEMENT_DOCUMENTS, "item_movement_document", "7/file", "42/file"),
                Arguments.of(
                        StorageCategory.BOARD_ATTACHMENTS, "board_ticket", "7/1a2b_report.pdf", "42/1a2b_report.pdf"),
                Arguments.of(
                        StorageCategory.IMAGE_LOST_AND_FOUND,
                        "lost_and_found_item",
                        "7/original.png",
                        "42/original.png"),
                Arguments.of(StorageCategory.IMAGE_QUIZ_QUESTION, "quiz_question", "7/original.png", "42/original.png"),
                Arguments.of(
                        StorageCategory.IMAGE_DOCUMENT_TEMPLATE_PICTURE,
                        "document_template",
                        "7/v3-150/original.png",
                        "42/v3-150/original.png"),
                Arguments.of(
                        StorageCategory.IMAGE_KB_ICON, "kb_folder", "folder-7/original.png", "folder-42/original.png"),
                Arguments.of(
                        StorageCategory.IMAGE_KB_FILE_PICTURE,
                        "kb_file",
                        "file-7/original.png",
                        "file-42/original.png"));
    }

    private static IdRemapper movedFrom(String table, int sourceId, int destinationId) {
        var idMap = new IdRemapper();
        idMap.put(table, sourceId, destinationId);
        return idMap;
    }
}
