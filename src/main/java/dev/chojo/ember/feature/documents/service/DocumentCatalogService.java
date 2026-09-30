/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentTag;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import io.javalin.openapi.OpenApiName;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The document store as readers browse it: the documents of a member, a page of the station's
 * store, the words documents are sorted by, and whom a document is bound to.
 *
 * <p>Who may see what is {@link DocumentAccessService}'s to decide; this reads and writes what the
 * caller already may.
 */
@Singleton
public class DocumentCatalogService {
    private final DocumentRepository documents;
    private final DocumentService documentService;

    @Inject
    public DocumentCatalogService(DocumentRepository documents, DocumentService documentService) {
        this.documents = documents;
        this.documentService = documentService;
    }

    /**
     * @return the document, or empty when there is none by that id
     */
    public Optional<Document> find(int id) {
        return documents.findById(id);
    }

    /**
     * The documents bound to a member.
     *
     * @param includeHidden whether the ones kept from the member themselves are listed too
     */
    public List<DocumentResponse> forMember(int stationId, int memberId, boolean includeHidden) {
        return documents.findByMember(stationId, memberId, includeHidden).stream()
                .map(this::view)
                .toList();
    }

    /**
     * A page of the station's store, searched in the station's own language.
     */
    public DocumentPage page(int stationId, StoreQuery query) {
        String config = documentService.searchConfigOf(stationId);
        var page = documents
                .findByStation(
                        stationId,
                        query.memberIds(),
                        query.search(),
                        query.includeHidden(),
                        query.unboundOnly(),
                        config,
                        query.size(),
                        query.page() * query.size())
                .stream()
                .map(this::view)
                .toList();
        int total = documents.countByStation(
                stationId, query.memberIds(), query.search(), query.includeHidden(), query.unboundOnly(), config);
        return new DocumentPage(page, total);
    }

    /**
     * The words the station sorts its documents by.
     */
    public List<String> tagNames(int stationId) {
        return documents.findTagsByStation(stationId).stream()
                .map(DocumentTag::name)
                .toList();
    }

    /**
     * Sets the words a document is sorted by, writing the ones the station does not know yet.
     */
    public DocumentResponse setTags(Document document, List<String> tags) {
        documents.setTags(document.id(), document.stationId(), tags != null ? tags : List.of());
        return view(document);
    }

    /**
     * Binds a document to exactly these members.
     */
    public DocumentResponse setMembers(Document document, List<Integer> memberIds) {
        documents.setMembers(document.id(), memberIds);
        return view(document);
    }

    /**
     * A document as a reader sees it, with the members it is bound to and the words it carries.
     */
    public DocumentResponse view(Document document) {
        return new DocumentResponse(
                document.id(),
                document.title(),
                document.fileName(),
                document.mimeType(),
                document.sizeBytes(),
                document.hidden(),
                document.keepOnArchive(),
                document.hasThumbnail(),
                document.uploadedBy(),
                document.createdAt(),
                documents.membersOf(document.id()),
                documents.findTags(document.id()).stream()
                        .map(DocumentTag::name)
                        .toList());
    }

    /**
     * What a page of the store is narrowed to.
     *
     * @param memberIds     only what is bound to one of them, or empty for everybody
     * @param search        words in the title or in the documents themselves, or null
     * @param includeHidden whether the documents kept from their own members are listed too
     * @param unboundOnly   only the documents that name nobody
     * @param size          how many a page holds
     * @param page          which page, counted from zero
     */
    public record StoreQuery(
            List<Integer> memberIds, String search, boolean includeHidden, boolean unboundOnly, int size, int page) {}

    /**
     * One document as a reader sees it.
     *
     * @param memberIds the members it is bound to, so a reader can tell whose it is
     */
    @OpenApiName("MemberDocumentResponse")
    public record DocumentResponse(
            int id,
            String title,
            String fileName,
            String mimeType,
            long sizeBytes,
            boolean hidden,
            boolean keepOnArchive,
            boolean hasThumbnail,
            Integer uploadedBy,
            Instant createdAt,
            List<Integer> memberIds,
            List<String> tags) {}

    /**
     * A page of the station's documents.
     *
     * @param total how many the filters match in all, so the pages can be counted
     */
    @OpenApiName("MemberDocumentPage")
    public record DocumentPage(List<DocumentResponse> documents, int total) {}
}
