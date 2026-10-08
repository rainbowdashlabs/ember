/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
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

    /** What every sealed file is. */
    public static final String SEALED_TYPE = "application/pdf";

    private final DocumentRepository repository;
    private final SealedVersionRepository versions;
    private final StorageService storage;
    private final ImageVariants images;
    private final StationRepository stationRepository;
    private final DocumentIntake intake;

    @Inject
    public DocumentService(
            DocumentRepository repository,
            SealedVersionRepository versions,
            StorageService storage,
            ImageVariants images,
            StationRepository stationRepository,
            DocumentIntake intake) {
        this.repository = repository;
        this.versions = versions;
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
        storage.store(
                scope(stationId), StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()), CONTENT, data, type);
        repository.setTags(document.id(), stationId, tags);
        describe(document, data);
        log.info("Stored document {} for station {} ({} bytes)", document.id(), stationId, data.length);
        return repository.findById(document.id()).orElse(document);
    }

    /**
     * Makes what is kept beside a document's file from the file it serves: the picture a tile shows and
     * what the search reads. Neither touches the file.
     *
     * @param document the document
     * @param data     the bytes it serves
     */
    void describe(Document document, byte[] data) {
        if (storeThumbnail(scope(document.stationId()), document.id(), document.mimeType(), data)) {
            repository.markThumbnail(document.id());
        }
        index(document.id(), document.stationId(), document.title(), document.mimeType(), data);
    }

    /**
     * Keeps a sealed file under its SHA-256. The key names the content, so a file once stored there is
     * never replaced by other bytes.
     *
     * @param stationId the station the document belongs to
     * @param sha256    the file's SHA-256, lower-case hexadecimal
     * @param data      the sealed file
     */
    void storeSealed(int stationId, String sha256, byte[] data) {
        storage.store(
                scope(stationId), StorageCategory.MEMBER_DOCUMENTS, sealedKey(sha256), CONTENT, data, SEALED_TYPE);
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
     * The bytes a document serves, whatever the station switched off: as they were uploaded, or for a
     * sealed document its current sealed version. For the personal data export and the search index,
     * never for a reader.
     */
    public Optional<byte[]> read(Document document) {
        if (document.sealed()) return versions.current(document.id()).flatMap(version -> read(document, version));
        return readUploaded(document);
    }

    /**
     * The file a document was filed with, for a reader at a station that keeps documents. For a document
     * sealed after it was filed, that is the file its signatures bind to, which stays beside its sealed
     * versions; {@link #open} serves the current version instead.
     *
     * @param door who reads, which decides the words of a refusal
     * @return the file as it was uploaded, or empty for a document that was filed sealed
     */
    public Optional<byte[]> openUploaded(Document document, DocumentDoor door) {
        requireKept(document.stationId(), door);
        return readUploaded(document);
    }

    /**
     * The file a document was filed with, whatever the station switched off, sealed since or not.
     *
     * @return the file as it was uploaded, or empty for a document that was filed sealed
     */
    public Optional<byte[]> readUploaded(Document document) {
        return storage.readAllBytes(
                scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()), CONTENT);
    }

    /**
     * The bytes of one sealed version of a document, whatever the station switched off. For the personal
     * data export, never for a reader.
     *
     * @param document the sealed document
     * @param version  one of its versions
     * @return the sealed file, or empty when it is not in the store
     */
    public Optional<byte[]> read(Document document, SealedVersion version) {
        return storage.readAllBytes(
                scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, sealedKey(version.sha256()), CONTENT);
    }

    /**
     * One sealed version of a document for a reader, the one it serves or an earlier one it superseded, at a
     * station that keeps documents.
     *
     * @param document the document, which has to be sealed
     * @param number   the version's number within the document
     * @param door     who reads, which decides the words of a refusal
     * @return the sealed file, or empty when it is not in the store
     */
    public Optional<byte[]> openVersion(Document document, int number, DocumentDoor door) {
        requireKept(document.stationId(), door);
        var version = sealedVersions(document).stream()
                .filter(candidate -> candidate.version() == number)
                .findFirst()
                .orElseThrow(DocumentRefusal.SEALED_VERSION_NOT_FOUND::raise);
        return read(document, version);
    }

    /**
     * The name a sealed version is saved under: the document's file name with the version before its
     * ending, so the versions of one document do not overwrite each other in a downloads folder.
     *
     * @param fileName the document's file name
     * @param number   the version's number
     * @return the name
     */
    public static String versionFileName(String fileName, int number) {
        int dot = fileName.lastIndexOf('.');
        String suffix = "-v" + number;
        if (dot <= 0) return fileName + suffix;
        return fileName.substring(0, dot) + suffix + fileName.substring(dot);
    }

    /**
     * The sealed versions of a document, newest first.
     *
     * @param document the document
     * @return its versions, none for a document that is not sealed
     */
    public List<SealedVersion> sealedVersions(Document document) {
        if (!document.sealed()) return List.of();
        return versions.versionsOf(document.id());
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
     * Removes documents a reader chose, at a station that keeps documents. A choice holding a sealed
     * document removes nothing.
     *
     * @param stationId the station they belong to
     * @param documents the documents, already checked to be the reader's to remove
     * @param door      who removes them, which decides the words of a refusal
     */
    public void remove(int stationId, List<Document> documents, DocumentDoor door) {
        requireKept(stationId, door);
        documents.forEach(DocumentService::requireUnsealed);
        documents.forEach(this::delete);
    }

    /**
     * Removes a document and everything kept for it. A sealed document is never removed this way; only
     * deleting its station takes it.
     */
    public void delete(Document document) {
        requireUnsealed(document);
        storage.deletePrefix(scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()));
        storage.deletePrefix(
                scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, thumbnailKey(document.id()));
        repository.delete(document.id());
        log.info("Deleted document {} of station {}", document.id(), document.stationId());
    }

    /**
     * Removes the files of a sealed document whose row is already gone: the sealed files no other version
     * names any more, the file it was filed with where it was sealed after filing, and the picture of its
     * tile.
     *
     * @param document the sealed document that was deleted
     * @param sha256s  the hashes of its versions
     */
    void deleteSealedFiles(Document document, List<String> sha256s) {
        var scope = scope(document.stationId());
        for (String sha256 : sha256s) {
            if (versions.firstWithHash(sha256).isEmpty()) {
                storage.deletePrefix(scope, StorageCategory.MEMBER_DOCUMENTS, sealedKey(sha256));
            }
        }
        storage.deletePrefix(scope, StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()));
        storage.deletePrefix(scope, StorageCategory.MEMBER_DOCUMENTS, thumbnailKey(document.id()));
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
        indexText(documentId, stationId, text);
    }

    /**
     * Records what a document says from text read out of it before, such as on the installation its
     * station moved from, without reading its file again. The index is built in the language of the
     * station it is at now.
     *
     * @param documentId the document
     * @param stationId  the station it belongs to
     * @param text       its title and whatever was read out of its file
     */
    public void indexText(int documentId, int stationId, String text) {
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
     * person, so the name is what keeps it somebody's. A sealed document is always marked to be kept, and
     * the database refuses letting go of its members any other way.
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

    /**
     * Refuses removing a sealed document.
     *
     * @param document the document
     */
    static void requireUnsealed(Document document) {
        if (document.sealed()) throw DocumentRefusal.DOCUMENT_SEALED_NOT_REMOVABLE.raise();
    }

    private static String contentKey(int documentId) {
        return documentId + "/file";
    }

    /**
     * The key a sealed file is kept under: its SHA-256 alone, not the document's id, so the key says
     * what the bytes are and stays right wherever the rows that name it are carried.
     */
    private static String sealedKey(String sha256) {
        return "sealed/" + sha256;
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
