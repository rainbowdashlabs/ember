/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.service.StationTransferFileService;
import dev.chojo.ember.feature.storage.service.StationTransferFileService.ListKeysResponse;
import dev.chojo.ember.feature.storage.service.TransferBackendDescriptorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationTransferAssetRoutesTest {
    private static final String BASE = PREFIX + "/public/transfer/token-1/files";

    private StationTransferFileService files;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        var export = mock(StationExportService.class);
        when(export.validateToken("token-1")).thenReturn(Optional.of(4));
        files = mock(StationTransferFileService.class);
        harness = RouteHarness.serving(new StationTransferAssetRoutes(
                export, mock(TransferBackendDescriptorService.class), files, mock(AvatarService.class)));
    }

    @Test
    void theKeysOfACategoryAreListedAPageAtATime() {
        when(files.page(4, StorageCategory.KB_FILES, "a", 2)).thenReturn(new ListKeysResponse(List.of("b"), null, 2));

        harness.run((server, client) -> {
            var page = json(client.get(BASE + "/kb_files?after=a&limit=2"));
            assertEquals("b", page.path("keys").get(0).asString());
            assertEquals(2, page.path("total").asInt());
            assertEquals(
                    403,
                    client.get(PREFIX + "/public/transfer/other/files/kb_files").code());
        });
    }

    @Test
    void aPageWithoutALimitLeavesItToTheService() {
        when(files.page(4, StorageCategory.KB_FILES, null, 0)).thenReturn(new ListKeysResponse(List.of(), null, 0));

        harness.run((server, client) ->
                assertEquals(200, client.get(BASE + "/kb_files").code()));

        verify(files).page(4, StorageCategory.KB_FILES, null, 0);
    }

    @Test
    void oneFileIsStreamedWithItsContentType() {
        var stream = new StoredStream(
                new ByteArrayInputStream(new byte[] {1, 2, 3}),
                3,
                new ObjectMetadata("application/pdf", "", Optional.empty(), Optional.empty()));
        when(files.open(4, StorageCategory.KB_FILES, "manual.pdf")).thenReturn(stream);
        when(files.open(4, StorageCategory.KB_FILES, "gone.pdf")).thenThrow(Refusal.TRANSFER_FILE_NOT_HERE.raise());

        harness.run((server, client) -> {
            var served = client.get(BASE + "/kb_files/manual.pdf");
            assertEquals(200, served.code());
            assertTrue(header(served, "Content-Type").startsWith("application/pdf"));
            assertEquals("3", header(served, "Content-Length"));
            assertEquals(Refusal.TRANSFER_FILE_NOT_HERE, refusalOf(client.get(BASE + "/kb_files/gone.pdf")));
        });
    }
}
