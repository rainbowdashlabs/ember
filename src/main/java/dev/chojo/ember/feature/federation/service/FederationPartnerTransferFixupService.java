/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
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

    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final StationRepository stationRepository;

    @Inject
    public FederationPartnerTransferFixupService(
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository) {
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
    }

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
                        """).single(call().bind("station_id", stationId)).update();

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
     * Announces the moved station's new instance URL to every remote federation partner so the
     * partner can flip its own {@code federation_partner.remote_host} entry without waiting for
     * the next protocol ping. Runs immediately after {@link #rewriteAfterImport(int, String)}
     * has settled the local rows. Each call is a signed {@code POST /remote/announce} sent under
     * the moved station's identity; receivers verify the signature, look up the partnership by
     * the moved station's UID, and update their stored host. Failures are swallowed — the
     * version-ping fallback will eventually carry the change anyway.
     */
    public void announceNewHostToRemotePartners(int stationId, String newInstanceUrl) {
        String url = newInstanceUrl == null ? null : newInstanceUrl.trim();
        if (url == null || url.isEmpty()) {
            log.warn("skip new-host announce for station {}: destination instance URL not configured", stationId);
            return;
        }
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null || station.federationPrivateKey() == null) {
            log.warn(
                    "skip new-host announce for station {}: no federation private key on the imported station",
                    stationId);
            return;
        }
        var partners = federationRepository.findPartners(stationId);
        var payload = new AnnounceBody(url);
        int sent = 0;
        int skipped = 0;
        for (FederationPartner partner : partners) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE
                    || partner.remoteHost() == null
                    || partner.remoteHost().isBlank()) {
                skipped++;
                continue;
            }
            try {
                boolean ok = federationHttpClient.post(
                        partner.remoteHost(),
                        RemoteFederationRoutes.ANNOUNCE.at(),
                        payload,
                        partner.partnerStationId(),
                        stationId,
                        station.federationPrivateKey());
                if (ok) {
                    sent++;
                } else {
                    log.warn("new-host announce to partner {} at {} failed", partner.id(), partner.remoteHost());
                }
            } catch (Exception e) {
                log.warn(
                        "new-host announce to partner {} at {} threw: {}",
                        partner.id(),
                        partner.remoteHost(),
                        e.getMessage());
            }
        }
        log.info(
                "announced new host {} to {} remote partner(s) for station {} (skipped {} local/inactive)",
                url,
                sent,
                stationId,
                skipped);
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

    private record AnnounceBody(String newHost) {}
}
