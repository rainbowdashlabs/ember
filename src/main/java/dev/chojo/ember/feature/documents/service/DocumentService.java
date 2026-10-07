/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.image.MediaTypes;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.FilePicture;
import dev.chojo.ember.util.PdfText;
import dev.chojo.ember.util.sql.FullTextSearch;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The documents kept for a station's members: their bytes, the picture a tile shows, and what
 * becomes of them when a member leaves.
 *
 * <p>Every way into the store passes through here, through whichever {@link DocumentDoor} it comes:
 * a station that switched documents off has switched them off for every reader, the association
 * included, and for the file itself as much as for the list of files.
 */
@Singleton
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** The bytes as they were uploaded. */
    private static final Variant CONTENT = new Variant("content");

    private final DocumentRepository repository;
    private final StorageService storage;
    private final ImageVariants images;
    private final StationRepository stationRepository;
    private final DocumentIntake intake;

    @Inject
    public DocumentService(
            DocumentRepository repository,
            StorageService storage,
            ImageVariants images,
            StationRepository stationRepository,
            DocumentIntake intake) {
        this.repository = repository;
        this.storage = storage;
        this.images = images;
        this.stationRepository = stationRepository;
        this.intake = intake;
    }

    /**
     * Refuses where the station keeps no documents.
     *
     * <p>The store belongs to the station, so a station that switched it off has switched it off for
     * the association too. The personal data export is deliberately not held to this: a switched-off
     * page is not a reason to withhold somebody's own data from them.
     *
     * @param stationId the station
     * @param door      whose refusal to answer with
     */
    public void requireKept(int stationId, DocumentDoor door) {
        if (stationRepository.findDisabledModules(stationId).contains(StationModule.DOCUMENTS)) {
            throw door.switchedOff().raise();
        }
    }

    /**
     * Files an uploaded document: the station has to keep documents, the file has to pass the intake,
     * and a document given no title is called after its file.
     *
     * @param stationId the station it belongs to
     * @param filing    what was said about it
     * @param file      the uploaded file, or null where the request carried none
     * @param door      who files it, which decides the words of a refusal
     * @return the document as filed
     */
    public Document file(int stationId, Filing filing, @Nullable UploadedFile file, DocumentDoor door) {
        requireKept(stationId, door);
        var upload = intake.admit(stationId, StorageCategory.MEMBER_DOCUMENTS, file, door.intake());
        return store(
                stationId,
                filing.memberIds(),
                DocumentIntake.titleOf(filing.title(), upload),
                upload.fileName(),
                upload.declaredType(),
                upload.data(),
                filing.hidden(),
                filing.keepOnArchive(),
                filing.uploader(),
                filing.tags());
    }

    /**
     * Takes a document in, keeps its bytes and makes a picture of it where one can be made. The file
     * has passed the intake already.
     *
     * @param memberIds the members it concerns, at least one
     * @param mimeType  the type the intake settled on, or {@code null} where there is none, which is
     *                  kept as {@link MediaTypes#UNTYPED}
     */
    public Document store(
            int stationId,
            List<Integer> memberIds,
            String title,
            String fileName,
            @Nullable String mimeType,
            byte[] data,
            boolean hidden,
            boolean keepOnArchive,
            Uploader uploader,
            List<String> tags) {
        String type = Objects.requireNonNullElse(mimeType, MediaTypes.UNTYPED);
        var document = repository.create(
                stationId, title, fileName, type, data.length, hidden, keepOnArchive, uploader, memberIds);
        var scope = scope(stationId);
        storage.store(scope, StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()), CONTENT, data, type);
        if (storeThumbnail(scope, document.id(), type, data)) {
            repository.markThumbnail(document.id());
        }
        repository.setTags(document.id(), stationId, tags);
        index(document.id(), stationId, title, type, data);
        log.info("Stored document {} for station {} ({} bytes)", document.id(), stationId, data.length);
        return repository.findById(document.id()).orElse(document);
    }

    /**
     * The bytes of a document, as they were uploaded, for a reader at a station that keeps documents.
     *
     * @param door who reads, which decides the words of a refusal
     */
    public Optional<byte[]> open(Document document, DocumentDoor door) {
        requireKept(document.stationId(), door);
        return read(document);
    }

    /**
     * The bytes of a document, as they were uploaded, whatever the station switched off. For the
     * personal data export and the search index, never for a reader.
     */
    public Optional<byte[]> read(Document document) {
        return storage.readAllBytes(
                scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()), CONTENT);
    }

    /**
     * The picture of a document at the requested size, when one was made of it.
     *
     * @param door who reads, which decides the words of a refusal
     */
    public Optional<MediaContent> thumbnail(Document document, int size, DocumentDoor door) {
        requireKept(document.stationId(), door);
        if (!document.hasThumbnail()) return Optional.empty();
        return images.read(
                ImageProfile.CONTENT,
                scope(document.stationId()),
                StorageCategory.MEMBER_DOCUMENTS,
                thumbnailKey(document.id()),
                size);
    }

    /**
     * Removes documents a reader chose, at a station that keeps documents.
     *
     * @param stationId the station they belong to
     * @param documents the documents, already checked to be the reader's to remove
     * @param door      who removes them, which decides the words of a refusal
     */
    public void remove(int stationId, List<Document> documents, DocumentDoor door) {
        requireKept(stationId, door);
        documents.forEach(this::delete);
    }

    /**
     * Removes a document and everything kept for it.
     */
    public void delete(Document document) {
        storage.deletePrefix(scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()));
        storage.deletePrefix(
                scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, thumbnailKey(document.id()));
        repository.delete(document.id());
        log.info("Deleted document {} of station {}", document.id(), document.stationId());
    }

    /**
     * Records what the document says, so it can be searched for rather than scrolled to. A file
     * nothing can be read out of is still findable by its title.
     */
    private void index(int documentId, int stationId, String title, String mimeType, byte[] data) {
        String text = title;
        try {
            if ("application/pdf".equals(mimeType)) {
                text = title + " " + Objects.requireNonNullElse(PdfText.extract(data), "");
            } else if (mimeType.startsWith("text/")) {
                text = title + " " + new String(data, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Nothing could be read out of document {}", documentId, e);
        }
        repository.updateSearchIndex(documentId, text, searchConfigOf(stationId));
    }

    /**
     * Builds the search index of every document again, which a new major version of the database
     * needs when it stems words differently.
     *
     * <p>A document indexed before its source text was kept has its file read once more, which also
     * keeps the text from then on. After that every document is rebuilt from its kept text alone,
     * one statement per station, since that is the unit that decides the language.
     *
     * @return how many documents were indexed again
     */
    public int rebuildSearchIndex() {
        for (var document : repository.findWithoutSourceText()) {
            var data = read(document);
            if (data.isEmpty()) {
                log.warn("Document {} could not be read back for its search index", document.id());
                continue;
            }
            index(document.id(), document.stationId(), document.title(), document.mimeType(), data.get());
        }
        int rebuilt = 0;
        for (int stationId : repository.stationsWithSourceText()) {
            rebuilt += repository.rebuildSearchIndex(stationId, searchConfigOf(stationId));
        }
        return rebuilt;
    }

    /**
     * Draws the picture of every PDF document again, for pictures drawn before the page was drawn as
     * finely as it is now. A document that cannot be read is logged and skipped.
     *
     * @return how many documents were read and drawn again
     */
    public int redrawPages() {
        int redrawn = 0;
        for (var document : repository.findPdfs()) {
            var data = read(document);
            if (data.isEmpty()) {
                log.warn("Document {} could not be read back to draw its picture again", document.id());
                continue;
            }
            if (storeThumbnail(scope(document.stationId()), document.id(), document.mimeType(), data.get())) {
                repository.markThumbnail(document.id());
                redrawn++;
            }
        }
        return redrawn;
    }

    /** The language a station writes in, which is what its documents are stemmed by. */
    public String searchConfigOf(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(station -> FullTextSearch.forLocale(station.locale()))
                .orElse(FullTextSearch.DEFAULT_CONFIG);
    }

    /**
     * Whether a reader holding these permissions may see this document.
     *
     * <p>The document decides, and it decides between two permissions of the store rather than
     * borrowing one from the members. A document that names a member, or the name of one who was
     * deleted, is a member document and needs the permission for those. A document that names nobody
     * is the station's own paperwork and needs only the permission to read the store, which is what
     * lets the equipment officer at the test certificates without handing them the member list. A
     * hidden one is kept from everybody who may not read member documents, whatever it names.
     *
     * @param document               the document being read
     * @param mayReadMemberDocuments whether the reader may see the documents that name a member
     * @param mayReadStore           whether the reader may see the station's own paperwork
     * @return whether the reader may see it
     */
    public boolean mayRead(Document document, boolean mayReadMemberDocuments, boolean mayReadStore) {
        if (!repository.hasNoMembers(document.id())) return mayReadMemberDocuments;
        return mayReadStore && (!document.hidden() || mayReadMemberDocuments);
    }

    /**
     * Takes a member off their documents as they leave, by whatever way they leave.
     *
     * <p>What was not marked to be kept goes, and a document nobody is left on goes with it. What was
     * marked to be kept stays: with the member where they are archived, and under their name where they
     * are deleted, since a deleted member is not there to stay with. A kept document that named nobody
     * would become the station's own paperwork, readable far more widely than what was filed about one
     * person, so the name is what keeps it somebody's.
     *
     * @param memberId the member leaving
     * @param leaving  how they leave
     */
    public void memberLeaves(int memberId, Leaving leaving) {
        if (leaving == Leaving.DELETED) {
            int named = repository.keepDepartedName(memberId);
            if (named > 0) log.info("Kept the name of member {} on {} documents", memberId, named);
        }
        for (int orphaned : repository.unbindMember(memberId, leaving == Leaving.ARCHIVED)) {
            repository.findById(orphaned).ifPresent(this::delete);
        }
    }

    /**
     * Makes the picture a tile shows: the image itself, or the first page of a document that has
     * pages. Anything else has no picture, and the tile says what it is instead.
     *
     * @return whether a picture was made
     */
    private boolean storeThumbnail(StorageScope.Station scope, int documentId, String mimeType, byte[] data) {
        try {
            var picture = FilePicture.of(mimeType, data);
            if (picture.isEmpty()) return false;
            images.store(
                    ImageProfile.CONTENT,
                    scope,
                    StorageCategory.MEMBER_DOCUMENTS,
                    thumbnailKey(documentId),
                    picture.get(),
                    0);
            return true;
        } catch (Exception e) {
            log.warn("No picture could be made of document {}", documentId, e);
            return false;
        }
    }

    private StorageScope.Station scope(int stationId) {
        return new StorageScope.Station(stationId, stationRepository.requireUid(stationId));
    }

    private static String contentKey(int documentId) {
        return documentId + "/file";
    }

    /** The key a document's picture is kept under, beside the document itself. */
    static String thumbnailKey(int documentId) {
        return documentId + "/thumb";
    }

    /** How a member leaves the station. */
    public enum Leaving {
        /** Marked former, archived by the station or by its association. */
        ARCHIVED,
        /** Deleted outright, by the station or with their account. */
        DELETED
    }

    /**
     * What is said about a document as it is filed.
     *
     * @param memberIds     the members it concerns, or none for the station's own paperwork
     * @param title         what it is to be called, or null to call it after its file
     * @param hidden        whether it is kept from the members it names
     * @param keepOnArchive whether it outlasts their membership
     * @param uploader      who files it
     * @param tags          the words it is sorted by
     */
    public record Filing(
            List<Integer> memberIds,
            @Nullable String title,
            boolean hidden,
            boolean keepOnArchive,
            Uploader uploader,
            List<String> tags) {}
}
