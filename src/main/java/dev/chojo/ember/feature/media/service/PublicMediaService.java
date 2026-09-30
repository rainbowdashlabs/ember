/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The media files served to everyone, by content hash, for the public site: a station's, addressed by
 * the station's identity or its readable name, and the instance's own.
 *
 * <p>A width asked for that is not a positive whole number is ignored rather than refused, so a link
 * written by hand still shows the picture in its original size.
 */
@Singleton
public class PublicMediaService {
    private final MediaLibraryService media;
    private final StationRepository stationRepository;

    @Inject
    public PublicMediaService(MediaLibraryService media, StationRepository stationRepository) {
        this.media = media;
        this.stationRepository = stationRepository;
    }

    /**
     * The station a public address names.
     *
     * @param address the station's identity or readable name
     * @return the station's id
     */
    public int resolveStation(String address) {
        return stationRepository
                .resolveAddressedId(address)
                .orElseThrow(Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_FILE::raise);
    }

    /**
     * A file of a station's library, looked up in that library alone.
     *
     * @param stationId the station
     * @param hash      the file's content hash
     * @param width     the width asked for, or null
     * @param accept    the formats the browser takes
     * @return the file
     */
    public MediaContent read(int stationId, String hash, String width, String accept) {
        return readFrom(stationId, hash, width, accept);
    }

    /**
     * A file of the instance's own library, which belongs to no station.
     *
     * @param hash   the file's content hash
     * @param width  the width asked for, or null
     * @param accept the formats the browser takes
     * @return the file
     */
    public MediaContent readInstance(String hash, String width, String accept) {
        return readFrom(null, hash, width, accept);
    }

    private MediaContent readFrom(Integer stationId, String hash, String width, String accept) {
        return media.readVariant(stationId, hash, parseWidth(width), accept)
                .orElseThrow(Refusal.PUBLIC_FILE_NOT_HERE::raise);
    }

    private static Integer parseWidth(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
