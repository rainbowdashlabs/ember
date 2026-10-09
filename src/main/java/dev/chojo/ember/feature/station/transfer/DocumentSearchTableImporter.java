/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.documents.service.DocumentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Builds the search index of the member documents a transfer brought, from the text the source read
 * out of them. The index itself does not travel: it is built in the language of the station it is
 * at, and the generic engine cannot write it. A document whose text the source never kept arrives
 * without an index entry, which leaves it findable by its title and lets the next rebuild read its
 * file again.
 */
@Singleton
public class DocumentSearchTableImporter implements TableImporter {
    private final DocumentService documents;

    @Inject
    public DocumentSearchTableImporter(DocumentService documents) {
        this.documents = documents;
    }

    @Override
    public String table() {
        return "member_document_search";
    }

    @Override
    @SuppressWarnings("unchecked")
    public int importRows(StationImportContext context, Object payload) {
        int imported = 0;
        for (Map<String, Object> row : (List<Map<String, Object>>) payload) {
            if (!(row.get("document_id") instanceof Number sourceId)) continue;
            if (!(row.get("source_text") instanceof String text)) continue;
            var documentId = context.idMap().find("member_document", sourceId.intValue());
            if (documentId.isEmpty()) continue;
            documents.indexText(documentId.get(), context.stationId(), text);
            imported++;
        }
        return imported;
    }
}
