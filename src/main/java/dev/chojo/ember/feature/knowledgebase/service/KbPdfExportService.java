/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.PandocConverter;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a written knowledge-base file as a printable PDF, carrying the station header and logo
 * that the other exports use.
 *
 * <p>Markdown goes through pandoc, which turns it into the Typst markup the template evaluates, so
 * headings, lists, tables and code blocks survive. Plain text is handed over line by line instead,
 * because nothing in it should be read as markup in the first place.
 */
@Singleton
public class KbPdfExportService {
    private static final Logger log = LoggerFactory.getLogger(KbPdfExportService.class);
    private static final DateTimeFormatter PDF_DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final KbContentService contentService;
    private final StationRepository stationRepository;
    private final StationLogoService logoService;
    private final Api apiConfig;

    @Inject
    public KbPdfExportService(
            KbContentService contentService,
            StationRepository stationRepository,
            StationLogoService logoService,
            Api apiConfig) {
        this.contentService = contentService;
        this.stationRepository = stationRepository;
        this.logoService = logoService;
        this.apiConfig = apiConfig;
    }

    /**
     * Tells whether a file type has a written body this service can render.
     *
     * @param fileType the file type to check
     * @return whether the file can be exported as PDF
     */
    public static boolean isExportable(KbFileType fileType) {
        return fileType == KbFileType.MARKDOWN || fileType == KbFileType.TEXT;
    }

    /**
     * The parts of a file the renderer actually needs, so a file held by a partner can be rendered
     * without a local {@link KbFile} row behind it.
     */
    public record ExportSource(String fileName, String description, String content, boolean markdown) {}

    /**
     * Renders a file of this station as a PDF, under this station's name and logo.
     *
     * @param file        the markdown or text file to render
     * @param generatedBy the name of the person requesting the export
     * @return the rendered PDF
     */
    public byte[] render(KbFile file, String generatedBy) throws IOException, InterruptedException {
        var station = stationRepository.findById(file.stationId()).orElse(null);
        return render(
                new ExportSource(
                        file.name(),
                        file.description(),
                        contentService.getMarkdownContent(file.id()).orElse(""),
                        file.fileType() == KbFileType.MARKDOWN),
                station != null ? station.name() : "",
                station,
                file.stationId(),
                generatedBy);
    }

    /**
     * Renders a publicly visible file. A public reader has no name of their own, so the station
     * serving the file is named as the source in the footer.
     *
     * @param file    the markdown or text file to render
     * @param station the station publishing it
     * @return the rendered PDF
     */
    public byte[] renderPublic(KbFile file, Station station) throws IOException, InterruptedException {
        return render(
                new ExportSource(
                        file.name(),
                        file.description(),
                        contentService.getMarkdownContent(file.id()).orElse(""),
                        file.fileType() == KbFileType.MARKDOWN),
                station.name(),
                station,
                station.id(),
                station.name());
    }

    /**
     * Renders a file held by a federation partner. The partner's name heads the document because
     * the file is theirs, and no logo is drawn: a partner publishes its files, not its branding.
     *
     * @param source             the partner's file name, description and body
     * @param partnerStationName the name of the station serving the file
     * @param localStation       the reading station, which decides language and time zone
     * @param generatedBy        the name of the person requesting the export
     * @return the rendered PDF
     */
    public byte[] renderFederated(
            ExportSource source, String partnerStationName, Station localStation, String generatedBy)
            throws IOException, InterruptedException {
        return render(source, partnerStationName, localStation, null, generatedBy);
    }

    private byte[] render(
            ExportSource source, String stationName, Station station, Integer logoStationId, String generatedBy)
            throws IOException, InterruptedException {
        var data = new LinkedHashMap<String, Object>();
        data.put("stationName", stationName == null ? "" : stationName);
        data.put("generatedBy", generatedBy);
        data.put("generatedAt", PDF_DATE_TIME_FMT.format(Instant.now().atZone(resolveTimezone(station))));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("hasLogo", false);
        data.put("fileName", source.fileName());
        data.put("fileDescription", source.description() == null ? "" : source.description());

        Map<String, String> resources = Map.of();
        if (source.markdown()) {
            resources = Map.of("body.typ", PandocConverter.markdownToTypst(source.content()));
        } else {
            data.put("lines", List.of(source.content().replace("\r\n", "\n").split("\n", -1)));
        }

        var logo = logoStationId != null ? logoService.original(logoStationId).orElse(null) : null;
        byte[] pdf = TypstCompiler.compileTemplate(
                data,
                resolveLocalePrefix(station) + (source.markdown() ? "/kb-markdown-export.typ" : "/kb-text-export.typ"),
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null,
                resources);
        log.info("Rendered '{}' as PDF ({} bytes)", source.fileName(), pdf.length);
        return pdf;
    }

    private static String resolveLocalePrefix(Station station) {
        if (station != null && station.locale() != null && station.locale().startsWith("de")) return "de";
        return "en";
    }

    private static ZoneId resolveTimezone(Station station) {
        if (station != null && station.timezone() != null) {
            try {
                return ZoneId.of(station.timezone());
            } catch (Exception ignored) {
            }
        }
        return ZoneOffset.UTC;
    }
}
