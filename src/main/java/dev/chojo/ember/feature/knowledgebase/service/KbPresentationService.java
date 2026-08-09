/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.util.PdfText;
import dev.chojo.ember.util.PresentationConverter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Turns uploaded slide decks into something a browser can show. An upload is stored as-is and
 * converted to PDF in the background; the file carries a conversion status so a reader knows
 * whether the rendered version is ready, still coming, or never arrived.
 *
 * <p>The text of a successful conversion feeds the search index, which is the only way the words
 * inside a slide deck become findable.
 */
@Singleton
public class KbPresentationService {
    private static final Logger log = LoggerFactory.getLogger(KbPresentationService.class);

    private final KnowledgeBaseRepository repository;
    private final KbFileStorageService fileStorage;
    private final KbContentService contentService;

    @Inject
    public KbPresentationService(
            KnowledgeBaseRepository repository, KbFileStorageService fileStorage, KbContentService contentService) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.contentService = contentService;
    }

    /**
     * Marks a presentation as awaiting conversion and starts converting it in the background.
     *
     * @param stationId the station owning the file
     * @param fileId    the presentation file
     * @param data      the uploaded slide deck
     * @param filename  the original file name, which decides the source format
     */
    public void startConversion(int stationId, int fileId, byte[] data, String filename) {
        repository.updateConversionStatus(fileId, ConversionStatus.PENDING);
        CompletableFuture.runAsync(() -> convert(stationId, fileId, data, filename));
    }

    /**
     * Stores the converted PDF of a presentation, indexes its text and marks the conversion as
     * succeeded. A storage failure marks the conversion as failed instead.
     *
     * @param stationId the station owning the file
     * @param fileId    the presentation file
     * @param pdfBytes  the converted PDF
     */
    public void storePresentationResult(int stationId, int fileId, byte[] pdfBytes) {
        try {
            fileStorage.storePresentationPdf(stationId, fileId, pdfBytes);
            contentService.storeExtractedText(fileId, PdfText.extract(pdfBytes));
            repository.updateConversionStatus(fileId, ConversionStatus.SUCCESS);
            log.info("Presentation conversion succeeded for file {}", fileId);
        } catch (Exception e) {
            log.error("Failed to store presentation result for file {}", fileId, e);
            repository.updateConversionStatus(fileId, ConversionStatus.FAILED);
        }
    }

    /**
     * Reads the converted PDF of a presentation.
     *
     * @param fileId the presentation file
     * @return the PDF, or empty when the conversion has not produced one
     */
    public Optional<byte[]> getPresentationPdf(int fileId) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return Optional.empty();
        return fileStorage.readPresentationPdf(file.stationId(), fileId).map(KbFileStorageService.FileData::data);
    }

    /**
     * Replaces the slide deck behind an existing presentation file and converts it again.
     *
     * @param fileId   the presentation file
     * @param data     the new slide deck
     * @param mimeType the MIME type of the new upload
     * @param filename the new file name, which decides the source format
     */
    public void reuploadPresentation(int fileId, byte[] data, String mimeType, String filename) {
        var file = repository.findFileById(fileId).orElseThrow();
        fileStorage.store(file.stationId(), fileId, data, mimeType);
        startConversion(file.stationId(), fileId, data, filename);
        log.info("KB presentation file {} re-uploaded in station {}", fileId, file.stationId());
    }

    /**
     * Runs the conversion itself, marking the file as failed when the converter cannot produce a
     * PDF. Package-private so the failure path can be exercised without waiting on the background
     * task that normally drives it.
     *
     * @param stationId the station owning the file
     * @param fileId    the presentation file
     * @param data      the slide deck to convert
     * @param filename  the original file name, which decides the source format
     */
    void convert(int stationId, int fileId, byte[] data, String filename) {
        try {
            storePresentationResult(stationId, fileId, PresentationConverter.toPdf(data, filename));
        } catch (Exception e) {
            log.error("Presentation conversion failed for file {}", fileId, e);
            repository.updateConversionStatus(fileId, ConversionStatus.FAILED);
        }
    }
}
