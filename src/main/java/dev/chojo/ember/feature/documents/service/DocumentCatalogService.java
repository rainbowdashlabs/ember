/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentFilter;
import dev.chojo.ember.feature.documents.entity.DocumentTag;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SignatureSummary;
import dev.chojo.ember.feature.signing.service.SignatureSummaries;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The document store as readers browse it: the documents of a member, a page of the station's
 * store, the words documents are sorted by, and whom a document is bound to.
 *
 * <p>Who may see what is {@link DocumentAccessService}'s to decide; this reads and writes what the
 * caller already may, at a station that keeps documents.
 */
@Singleton
public class DocumentCatalogService {
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final SignatureSummaries signatures;

    @Inject
    public DocumentCatalogService(
            DocumentRepository documents, DocumentService documentService, SignatureSummaries signatures) {
        this.documents = documents;
        this.documentService = documentService;
        this.signatures = signatures;
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
     * @param door          who reads, which decides the words of a refusal
     */
    public List<MemberDocumentResponse> forMember(
            int stationId, int memberId, boolean includeHidden, DocumentDoor door) {
        documentService.requireKept(stationId, door);
        return views(documents.findByMember(stationId, memberId, includeHidden));
    }

    /**
     * A page of the station's store, searched in the station's own language.
     */
    public DocumentPage page(int stationId, StoreQuery query) {
        documentService.requireKept(stationId, DocumentDoor.STATION);
        String config = documentService.searchConfigOf(stationId);
        var page = views(
                documents.findByStation(stationId, query.filter(), config, query.size(), query.page() * query.size()));
        return new DocumentPage(page, documents.countByStation(stationId, query.filter(), config));
    }

    /**
     * Every document the filter matches that can be removed, by id, so a reader can choose all of them
     * at once. Sealed documents are never among them.
     */
    public List<Integer> ids(int stationId, DocumentFilter filter) {
        documentService.requireKept(stationId, DocumentDoor.STATION);
        return documents.idsByStation(stationId, filter, documentService.searchConfigOf(stationId));
    }

    /**
     * The words the station sorts its documents by.
     */
    public List<String> tagNames(int stationId) {
        documentService.requireKept(stationId, DocumentDoor.STATION);
        return documents.findTagsByStation(stationId).stream()
                .map(DocumentTag::name)
                .toList();
    }

    /**
     * Sets the words a document is sorted by, writing the ones the station does not know yet.
     */
    public MemberDocumentResponse setTags(Document document, @Nullable List<String> tags) {
        documentService.requireKept(document.stationId(), DocumentDoor.STATION);
        documents.setTags(document.id(), document.stationId(), tags != null ? tags : List.of());
        return view(document);
    }

    /**
     * Binds a document to exactly these members. A sealed document stays with the members it was sealed
     * for.
     */
    public MemberDocumentResponse setMembers(Document document, List<Integer> memberIds) {
        documentService.requireKept(document.stationId(), DocumentDoor.STATION);
        if (document.sealed()) throw DocumentRefusal.DOCUMENT_SEALED_MEMBERS_FIXED.raise();
        documents.setMembers(document.id(), memberIds);
        return view(document);
    }

    /**
     * A document as a reader sees it, with the members it is bound to, the names of those who were
     * deleted while it was kept for them, who put it in, the words it carries, for a sealed one its
     * sealed versions, and how the signatures asked for on it stand.
     */
    public MemberDocumentResponse view(Document document) {
        return view(document, signatures.ofDocuments(List.of(document.id())).get(document.id()));
    }

    /** Several documents as {@link #view(Document)} shows each, how their signatures stand read at once. */
    private List<MemberDocumentResponse> views(List<Document> listed) {
        var signed = signatures.ofDocuments(listed.stream().map(Document::id).toList());
        return listed.stream()
                .map(document -> view(document, signed.get(document.id())))
                .toList();
    }

    private MemberDocumentResponse view(Document document, @Nullable SignatureSummary signature) {
        return new MemberDocumentResponse(
                document.id(),
                document.title(),
                document.fileName(),
                document.mimeType(),
                document.sizeBytes(),
                document.hidden(),
                document.keepOnArchive(),
                document.hasThumbnail(),
                document.uploadedBy(),
                documents.uploaderNameOf(document.id()).orElse(null),
                document.createdAt(),
                documents.membersOf(document.id()),
                documents.departedOf(document.id()),
                documents.findTags(document.id()).stream()
                        .map(DocumentTag::name)
                        .toList(),
                document.sealed(),
                documentService.sealedVersions(document).stream()
                        .map(SealedVersionResponse::of)
                        .toList(),
                signature);
    }

    /**
     * What a page of the store is narrowed to.
     *
     * @param filter what the reader narrowed it to
     * @param size   how many a page holds
     * @param page   which page, counted from zero
     */
    public record StoreQuery(DocumentFilter filter, int size, int page) {}

    /**
     * One document as a reader sees it.
     *
     * @param uploadedBy    the member who put it in, where it was put in at the station
     * @param uploaderName  the name of whoever put it in, a member of the station or a manager of the
     *                      association, or null where nobody did or they are gone
     * @param memberIds     the members it is bound to, so a reader can tell whose it is
     * @param departedNames the names of members who were deleted while it was kept for them
     * @param sealed        whether it is sealed, which locks it: never deleted, its members fixed
     * @param sealedVersions its sealed versions, newest first; none for a document that is not sealed
     * @param signature     how the signatures asked for on it stand, or null where nobody was asked to sign it
     */
    public record MemberDocumentResponse(
            int id,
            String title,
            String fileName,
            String mimeType,
            long sizeBytes,
            boolean hidden,
            boolean keepOnArchive,
            boolean hasThumbnail,
            @Nullable Integer uploadedBy,
            @Nullable String uploaderName,
            Instant createdAt,
            List<Integer> memberIds,
            List<String> departedNames,
            List<String> tags,
            boolean sealed,
            List<SealedVersionResponse> sealedVersions,
            @Nullable SignatureSummary signature) {}

    /**
     * One sealed version of a document as a reader sees it.
     *
     * @param version       its number within the document, counting from 1
     * @param sha256        SHA-256 of the sealed file, lower-case hexadecimal
     * @param sizeBytes     how large the sealed file is
     * @param sealLevel     the level its seal reached
     * @param timestampedBy the timestamp service whose timestamp it carries, or null
     * @param sealedAt      when it was filed
     * @param supersededAt  when a later version took its place, or null for the version the document serves
     */
    public record SealedVersionResponse(
            int version,
            String sha256,
            long sizeBytes,
            SealLevel sealLevel,
            @Nullable String timestampedBy,
            Instant sealedAt,
            @Nullable Instant supersededAt) {

        static SealedVersionResponse of(SealedVersion version) {
            return new SealedVersionResponse(
                    version.version(),
                    version.sha256(),
                    version.sizeBytes(),
                    version.sealLevel(),
                    version.timestampedBy(),
                    version.sealedAt(),
                    version.supersededAt());
        }
    }

    /**
     * A page of the station's documents.
     *
     * @param total how many the filters match in all, so the pages can be counted
     */
    public record DocumentPage(List<MemberDocumentResponse> documents, int total) {}
}
