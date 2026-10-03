/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.ReachableFonts;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Which fonts an owner's documents can print with, and where their files are kept.
 *
 * <p>Reach adds up: a station reaches its own fonts, those of the association it belongs to and those
 * of the instance; an association reaches its own and the instance's; the instance only its own. Where
 * a family name is found at several of them, the nearest owner's family is taken whole (station over
 * association over instance). This is decided here and only here, so a template of an association is
 * served by handing in that association as the owner.
 *
 * <p>An association keeps its fonts in its home station, the way it keeps its other files there; the
 * instance keeps them in its own storage.
 */
@Singleton
public class FontLibrary {
    private final DocumentFontRepository fonts;
    private final ClusterService clusters;
    private final StationRepository stations;
    private final StorageService storage;

    @Inject
    public FontLibrary(
            DocumentFontRepository fonts, ClusterService clusters, StationRepository stations, StorageService storage) {
        this.fonts = fonts;
        this.clusters = clusters;
        this.stations = stations;
        this.storage = storage;
    }

    /**
     * @param owner the owner a template belongs to
     * @return every family it can print with, the nearest owner's where a name is found at several
     */
    public ReachableFonts reachable(Owner owner) {
        return ReachableFonts.of(
                switch (owner) {
                    case Owner.Station station ->
                        fonts.findReachable(station.stationId(), associationOf(station.stationId()));
                    case Owner.Association association -> fonts.findReachable(null, association.clusterId());
                    case Owner.Instance ignored -> fonts.findReachable(null, null);
                });
    }

    /**
     * Refuses a template that names a family its owner cannot reach, or, on an uploaded PDF, a family
     * whose outlines a field cannot embed.
     *
     * @param owner   the owner the template belongs to
     * @param content what the template is made of
     */
    public void requireReachable(Owner owner, TemplateContent content) {
        var families = content.fontFamilies().toList();
        if (families.isEmpty()) return;
        var reachable = reachable(owner);
        for (var family : families) {
            boolean usable = reachable
                    .find(family)
                    .map(found -> !(content instanceof PdfContent) || found.printsOnPdf())
                    .orElse(false);
            if (!usable) throw DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN.raise(RefusalDetail.text(family));
        }
    }

    /**
     * Which family a station's templates print in for a family name, which says which owner's file they
     * would read.
     *
     * @param stationId the station
     * @param family    the family name
     * @return the family the station reaches under that name, or empty where it reaches none
     */
    public Optional<FontFamily> familyAt(int stationId, String family) {
        return reachable(new Owner.Station(stationId)).find(family);
    }

    /**
     * Reads a font file.
     *
     * @param font the font
     * @return its bytes, or empty where the stored file is gone
     */
    public Optional<byte[]> read(DocumentFont font) {
        return storage.readAllBytes(scopeOf(font.owner()), categoryOf(font.owner()), key(font.id()));
    }

    /**
     * @param owner who keeps fonts
     * @return where the owner's font files are kept
     */
    public StorageScope scopeOf(Owner owner) {
        return switch (owner) {
            case Owner.Station station ->
                new StorageScope.Station(station.stationId(), stations.requireUid(station.stationId()));
            case Owner.Association association -> {
                int home = clusters.findById(association.clusterId())
                        .orElseThrow(DocumentRefusal.DOCUMENT_FONT_NOT_HERE::raise)
                        .homeStationId();
                yield new StorageScope.Association(home, stations.requireUid(home));
            }
            case Owner.Instance ignored -> new StorageScope.Instance();
        };
    }

    /**
     * @param owner who keeps fonts
     * @return what the owner's font files are kept as
     */
    public static StorageCategory categoryOf(Owner owner) {
        return switch (owner) {
            case Owner.Station ignored -> StorageCategory.FONTS;
            case Owner.Association ignored -> StorageCategory.ASSOCIATION_FONTS;
            case Owner.Instance ignored -> StorageCategory.INSTANCE_FONTS;
        };
    }

    /**
     * @param fontId a font
     * @return the storage key of its file
     */
    static String key(int fontId) {
        return String.valueOf(fontId);
    }

    private @Nullable Integer associationOf(int stationId) {
        return clusters.findByStation(stationId).map(cluster -> cluster.id()).orElse(null);
    }
}
