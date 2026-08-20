/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.members.entity.MemberDocument;
import dev.chojo.ember.feature.members.repository.MemberDocumentRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.PdfText;
import dev.chojo.ember.util.sql.FullTextSearch;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * The documents kept for a station's members: their bytes, the picture a tile shows, and what
 * becomes of them when a member leaves.
 */
@Singleton
public class MemberDocumentService {
    private static final Logger log = LoggerFactory.getLogger(MemberDocumentService.class);

    /** The bytes as they were uploaded. */
    private static final Variant CONTENT = new Variant("content");

    /** How wide the picture of a page is rendered before it is scaled down for the tiles. */
    private static final int THUMBNAIL_DPI = 72;

    private final MemberDocumentRepository repository;
    private final StorageService storage;
    private final ImageVariantService images;
    private final StationRepository stationRepository;

    @Inject
    public MemberDocumentService(
            MemberDocumentRepository repository,
            StorageService storage,
            ImageVariantService images,
            StationRepository stationRepository) {
        this.repository = repository;
        this.storage = storage;
        this.images = images;
        this.stationRepository = stationRepository;
    }

    /**
     * Takes a document in, keeps its bytes and makes a picture of it where one can be made.
     *
     * @param memberIds the members it concerns, at least one
     */
    public MemberDocument store(
            int stationId,
            List<Integer> memberIds,
            String title,
            String fileName,
            String mimeType,
            byte[] data,
            boolean hidden,
            boolean keepOnArchive,
            Integer uploadedBy,
            List<String> tags) {
        var document = repository.create(
                stationId, title, fileName, mimeType, data.length, hidden, keepOnArchive, uploadedBy, memberIds);
        var scope = scope(stationId);
        storage.store(scope, StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()), CONTENT, data, mimeType);
        if (storeThumbnail(scope, document.id(), mimeType, data)) {
            repository.markThumbnail(document.id());
        }
        repository.setTags(document.id(), stationId, tags);
        index(document.id(), stationId, title, mimeType, data);
        log.info("Stored document {} for station {} ({} bytes)", document.id(), stationId, data.length);
        return repository.findById(document.id()).orElse(document);
    }

    /**
     * The bytes of a document, as they were uploaded.
     */
    public Optional<byte[]> read(MemberDocument document) {
        return storage.readAllBytes(
                scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, contentKey(document.id()), CONTENT);
    }

    /**
     * The picture of a document at the requested size, when one was made of it.
     */
    public Optional<ImageVariantService.ImageData> thumbnail(MemberDocument document, int size) {
        if (!document.hasThumbnail()) return Optional.empty();
        return images.read(
                scope(document.stationId()), StorageCategory.MEMBER_DOCUMENTS, thumbnailKey(document.id()), size);
    }

    /**
     * Removes a document and everything kept for it.
     */
    public void delete(MemberDocument document) {
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
                text = title + " " + PdfText.extract(data);
            } else if (mimeType != null && mimeType.startsWith("text/")) {
                text = title + " " + new String(data, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Nothing could be read out of document {}", documentId, e);
        }
        repository.updateSearchIndex(documentId, text, searchConfigOf(stationId));
    }

    /** The language a station writes in, which is what its documents are stemmed by. */
    public String searchConfigOf(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(station -> FullTextSearch.forLocale(station.locale()))
                .orElse(FullTextSearch.DEFAULT_CONFIG);
    }

    /**
     * Takes a member off their documents when they are marked former. What was marked as kept
     * stays with them for the record; the rest goes, and a document nobody is bound to any more
     * goes with it.
     */
    public void releaseMember(int memberId) {
        for (int orphaned : repository.unbindMember(memberId, true)) {
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
            byte[] picture = pictureOf(mimeType, data);
            if (picture == null) return false;
            images.store(scope, StorageCategory.MEMBER_DOCUMENTS, thumbnailKey(documentId), picture, "image/png");
            return true;
        } catch (Exception e) {
            log.warn("No picture could be made of document {}", documentId, e);
            return false;
        }
    }

    private byte[] pictureOf(String mimeType, byte[] data) throws IOException {
        if (mimeType != null && mimeType.startsWith("image/")) return data;
        if ("application/pdf".equals(mimeType)) return firstPageOf(data);
        return null;
    }

    /** The first page of a PDF as a picture, which is what makes a readable tile of it. */
    private byte[] firstPageOf(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() == 0) return null;
            BufferedImage page = new PDFRenderer(document).renderImageWithDPI(0, THUMBNAIL_DPI);
            var out = new ByteArrayOutputStream();
            ImageIO.write(page, "png", out);
            return out.toByteArray();
        }
    }

    private StorageScope.Station scope(int stationId) {
        return new StorageScope.Station(stationId, stationRepository.resolveUid(stationId));
    }

    private static String contentKey(int documentId) {
        return documentId + "/file";
    }

    private static String thumbnailKey(int documentId) {
        return documentId + "/thumb";
    }
}
