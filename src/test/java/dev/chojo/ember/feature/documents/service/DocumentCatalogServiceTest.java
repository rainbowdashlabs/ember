/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentTag;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.StoreQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentCatalogServiceTest {
    private DocumentRepository documents;
    private DocumentService documentService;
    private DocumentCatalogService catalog;
    private Document document;

    @BeforeEach
    void setup() {
        documents = mock(DocumentRepository.class);
        documentService = mock(DocumentService.class);
        catalog = new DocumentCatalogService(documents, documentService);
        document = mock(Document.class);
        when(document.id()).thenReturn(5);
        when(document.stationId()).thenReturn(3);
        when(document.title()).thenReturn("Ausweis");
        when(documents.membersOf(5)).thenReturn(List.of(11));
        when(documents.findTags(5)).thenReturn(List.of(new DocumentTag(1, 3, "Nachweis")));
    }

    @Test
    void aDocumentIsShownWithItsMembersAndWords() {
        var view = catalog.view(document);

        assertEquals("Ausweis", view.title());
        assertEquals(List.of(11), view.memberIds());
        assertEquals(List.of("Nachweis"), view.tags());
    }

    @Test
    void aMembersDocumentsAndThePageOfTheStoreAreRead() {
        when(documents.findByMember(3, 11, true)).thenReturn(List.of(document));
        when(documentService.searchConfigOf(3)).thenReturn("german");
        when(documents.findByStation(3, List.of(11), "pass", true, false, "german", 24, 48))
                .thenReturn(List.of(document));
        when(documents.countByStation(3, List.of(11), "pass", true, false, "german"))
                .thenReturn(49);
        when(documents.findById(5)).thenReturn(Optional.of(document));

        assertEquals(1, catalog.forMember(3, 11, true).size());
        var page = catalog.page(3, new StoreQuery(List.of(11), "pass", true, false, 24, 2));
        assertEquals(49, page.total());
        assertEquals(1, page.documents().size());
        assertEquals(document, catalog.find(5).orElseThrow());
    }

    @Test
    void wordsAndMembersAreWrittenAndNoWordsMeansNone() {
        when(documents.findTagsByStation(3)).thenReturn(List.of(new DocumentTag(1, 3, "Nachweis")));

        catalog.setTags(document, null);
        catalog.setTags(document, List.of("A"));
        catalog.setMembers(document, List.of(11, 12));

        verify(documents).setTags(5, 3, List.of());
        verify(documents).setTags(5, 3, List.of("A"));
        verify(documents).setMembers(5, List.of(11, 12));
        assertEquals(List.of("Nachweis"), catalog.tagNames(3));
    }
}
