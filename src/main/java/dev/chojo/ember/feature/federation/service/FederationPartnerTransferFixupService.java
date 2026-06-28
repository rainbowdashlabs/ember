/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Rewrites {@code federation_partner.remote_host} after a cross-instance station transfer so
 * partnerships keep working without manual operator intervention.
 *
 * <p>Three cases the rewrite handles:
 * <ul>
 *   <li>The partner moved with this station (a multi-station transfer to the same destination)
 *       or already lived on the destination — the partnership becomes intra-instance, so
 *       {@code remote_host} and {@code webhook_url} are cleared.</li>
 *   <li>The partner stayed on the source instance — the partnership becomes cross-instance
 *       pointing back at the source, so {@code remote_host} / {@code webhook_url} are set to
 *       the source instance URL.</li>
 *   <li>The partner was already remote on the source (third instance) — the existing
 *       {@code remote_host} is left untouched and round-trips verbatim.</li>
 * </ul>
 *
 * <p>The mirror operation on the source side — flipping retained partner rows whose partner
 * UID equals the departed station — runs out of the {@code /complete} signal received from
 * the destination, in {@link #flipSourceSideRetainedPartners(UUID, String)}.
 */
@Singleton
public class FederationPartnerTransferFixupService {

    private static final Logger log = LoggerFactory.getLogger(FederationPartnerTransferFixupService.class);

    /**
     * Destination-side fixup. Runs once after the moved station's {@code federation_partner}
     * rows have been imported. {@code sourceInstanceUrl} is the base URL the destination used
     * to pull the bundle.
     */
    public void rewriteAfterImport(int stationId, String sourceInstanceUrl) {
        query("""
                        UPDATE federation_partner fp
                        SET partner_station_name = s.name
                        FROM station s
                        WHERE fp.station_id = :station_id
                          AND s.uid = fp.partner_station_id
                          AND (fp.partner_station_name IS NULL OR fp.partner_station_name = '');
                        """)
                .single(call().bind("station_id", stationId))
                .update();

        int cleared =
                query("""
                        UPDATE federation_partner
                        SET remote_host = NULL, webhook_url = NULL
                        WHERE station_id = :station_id
                          AND EXISTS (
                              SELECT 1 FROM station s WHERE s.uid = federation_partner.partner_station_id
                          );
                        """).single(call().bind("station_id", stationId)).update().rows();

        String url = sourceInstanceUrl == null ? null : sourceInstanceUrl.trim();
        int retargeted = 0;
        if (url != null && !url.isEmpty()) {
            retargeted = query("""
                            UPDATE federation_partner
                            SET remote_host = :url, webhook_url = :url
                            WHERE station_id = :station_id
                              AND remote_host IS NULL
                              AND NOT EXISTS (
                                  SELECT 1 FROM station s WHERE s.uid = federation_partner.partner_station_id
                              );
                            """)
                    .single(call().bind("station_id", stationId).bind("url", url))
                    .update()
                    .rows();
        }
        log.info(
                "destination-side partner fixup for station {}: cleared {} intra-instance partner(s), pointed {} cross-instance partner(s) back at {}",
                stationId,
                cleared,
                retargeted,
                url == null || url.isEmpty() ? "<unknown>" : url);
    }

    /**
     * Source-side mirror. Runs once when the destination signals {@code /complete}. Every
     * {@code federation_partner} row on this instance that points at the departed station's
     * UID and was previously intra-instance is flipped to point at the destination URL.
     * Already-remote rows are left untouched.
     */
    public void flipSourceSideRetainedPartners(UUID departedStationUid, String destinationInstanceUrl) {
        String url = destinationInstanceUrl == null ? null : destinationInstanceUrl.trim();
        if (url == null || url.isEmpty()) {
            log.warn(
                    "source-side partner flip skipped for departed station {}: no destination URL recorded",
                    departedStationUid);
            return;
        }
        int flipped = query("""
                        UPDATE federation_partner
                        SET remote_host = :url, webhook_url = :url
                        WHERE partner_station_id = :partner_uid
                          AND remote_host IS NULL;
                        """)
                .single(call().bind("partner_uid", departedStationUid, StandardValueConverter.UUID_STRING)
                        .bind("url", url))
                .update()
                .rows();
        log.info(
                "source-side partner flip for departed station {}: pointed {} retained partner row(s) at {}",
                departedStationUid,
                flipped,
                url);
    }
}
