/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationCapability;
import dev.chojo.ember.feature.federation.entity.FederationChangeLog;
import dev.chojo.ember.feature.federation.entity.FederationMetadataCache;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederationService;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

@Singleton
public class FederationRepository {

    // -- Partners --

    public List<FederationPartner> findPartners(int stationId) {
        return query("SELECT * FROM federation_partner WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(call().bind("station_id", stationId))
                .map(FederationPartner.map())
                .all();
    }

    /**
     * Public summary of every active federation partner for a station: name, slug (when set) and
     * great-circle distance in km when both coordinates are known.
     */
    public List<PublicPartnerSummary> findActivePartnerSummaries(int stationId) {
        return query("""
                        SELECT s.uid AS partner_uid,
                               s.name AS partner_name,
                               s.public_slug AS partner_slug,
                               CASE
                                   WHEN home.latitude IS NOT NULL AND home.longitude IS NOT NULL
                                    AND s.latitude IS NOT NULL AND s.longitude IS NOT NULL
                                   THEN ember_schema.haversine_km(
                                            home.latitude, home.longitude,
                                            s.latitude, s.longitude)
                                   ELSE NULL
                               END AS distance_km
                        FROM federation_partner fp
                        JOIN station s ON s.uid = fp.partner_station_id
                        JOIN station home ON home.id = fp.station_id
                        WHERE fp.station_id = :station_id AND fp.status = 'ACTIVE'
                        ORDER BY distance_km NULLS LAST, partner_name;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new PublicPartnerSummary(
                        row.get("partner_uid", StandardValueConverter.UUID_STRING),
                        row.getString("partner_name"),
                        row.getString("partner_slug"),
                        row.getObject("distance_km", Double.class)))
                .all();
    }

    public record PublicPartnerSummary(UUID uid, String name, String slug, Double distanceKm) {}

    public Optional<FederationPartner> findPartnerById(int id) {
        return query("SELECT * FROM federation_partner WHERE id = :id;")
                .single(call().bind("id", id))
                .map(FederationPartner.map())
                .first();
    }

    public Optional<FederationPartner> findByInviteCode(String code) {
        return query("SELECT * FROM federation_partner WHERE invite_code = :code;")
                .single(call().bind("code", code))
                .map(FederationPartner.map())
                .first();
    }

    public void createInviteToken(int stationId, String token) {
        query("INSERT INTO federation_invite_token(station_id, token) VALUES(:station_id, :token);")
                .single(call().bind("station_id", stationId).bind("token", token))
                .insert();
    }

    public boolean deleteInviteToken(int stationId, String token) {
        return query("DELETE FROM federation_invite_token WHERE station_id = :station_id AND token = :token;")
                .single(call().bind("station_id", stationId).bind("token", token))
                .delete()
                .changed();
    }

    public List<FederationPartner> findPendingRequestsForStation(UUID targetStationUid) {
        return query(
                        "SELECT * FROM federation_partner WHERE partner_station_id = :target_id::uuid AND status = 'PENDING';")
                .single(call().bind("target_id", targetStationUid, UUID_STRING))
                .map(FederationPartner.map())
                .all();
    }

    public FederationPartner createPartner(
            int stationId, UUID partnerStationUid, String inviteCode, String publicKey, String remoteHost) {
        return query("""
                        INSERT INTO federation_partner(station_id, partner_station_id, invite_code, public_key, status, remote_host, federation_version)
                        VALUES (:station_id, :partner_station_id::uuid, :invite_code, :public_key, 'PENDING', :remote_host, :federation_version)
                        RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("partner_station_id", partnerStationUid, UUID_STRING)
                        .bind("invite_code", inviteCode)
                        .bind("public_key", publicKey)
                        .bind("remote_host", remoteHost)
                        .bind("federation_version", FederationService.FEDERATION_VERSION))
                .map(FederationPartner.map())
                .first()
                .orElseThrow();
    }

    public boolean updateRemoteHost(int id, String remoteHost) {
        return query("UPDATE federation_partner SET remote_host = :remote_host, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("remote_host", remoteHost))
                .update()
                .changed();
    }

    public boolean activatePartner(int id, String partnerPublicKey) {
        return query(
                        "UPDATE federation_partner SET status = 'ACTIVE', partner_public_key = :key, invite_code = NULL, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("key", partnerPublicKey))
                .update()
                .changed();
    }

    public void updateFederationVersion(int id, String version) {
        query("UPDATE federation_partner SET federation_version = :version, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("version", version))
                .update();
    }

    /**
     * Returns all active remote partners (across all stations) for version broadcasting.
     */
    public List<FederationPartner> findAllActiveRemotePartners() {
        return query("SELECT * FROM federation_partner WHERE status = 'ACTIVE' AND remote_host IS NOT NULL;")
                .single(call())
                .map(FederationPartner.map())
                .all();
    }

    /**
     * Sets the federation version for all partners that still have the placeholder '0' version.
     * This handles partners created before the version was set at creation time.
     */
    public int backfillPartnerVersions(String currentVersion) {
        return query("""
                        UPDATE federation_partner
                        SET federation_version = :version, updated_at = now()
                        WHERE federation_version = '0';""")
                .single(call().bind("version", currentVersion))
                .update()
                .rows();
    }

    public boolean updatePartnerStatus(int id, FederationPartner.FederationStatus status) {
        return query("UPDATE federation_partner SET status = :status, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("status", status))
                .update()
                .changed();
    }

    public boolean deletePartner(int id) {
        return query("DELETE FROM federation_partner WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Updates the remote_host on all partner records where the given station is the partner.
     */
    public void updateRemoteHostForPartnerStation(UUID partnerStationUid, String remoteHost) {
        query(
                        "UPDATE federation_partner SET remote_host = :remote_host, updated_at = now() WHERE partner_station_id = :partner_station_id::uuid;")
                .single(call().bind("partner_station_id", partnerStationUid, UUID_STRING)
                        .bind("remote_host", remoteHost))
                .update();
    }

    // -- Capabilities --

    public List<FederationCapability> findCapabilities(int partnerId) {
        return query("SELECT * FROM federation_capability WHERE partner_id = :partner_id;")
                .single(call().bind("partner_id", partnerId))
                .map(FederationCapability.map())
                .all();
    }

    public void upsertCapability(int partnerId, CapabilityType capability, Direction direction, boolean enabled) {
        query("""
                        INSERT INTO federation_capability(partner_id, capability, direction, enabled)
                        VALUES (:partner_id, :capability, :direction, :enabled)
                        ON CONFLICT (partner_id, capability, direction) DO UPDATE SET enabled = :enabled;""")
                .single(call().bind("partner_id", partnerId)
                        .bind("capability", capability)
                        .bind("direction", direction)
                        .bind("enabled", enabled))
                .insert();
    }

    // -- KB Shares --

    public List<FederationShare> findKbShares(int stationId) {
        return query("SELECT * FROM federation_kb_share WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(FederationShare.mapKb())
                .all();
    }

    public FederationShare createKbShare(int stationId, Integer fileId, Integer folderId, ShareScope shareScope) {
        return query("""
                        INSERT INTO federation_kb_share(station_id, file_id, folder_id, share_scope)
                        VALUES (:station_id, :file_id, :folder_id, :share_scope) RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("file_id", fileId)
                        .bind("folder_id", folderId)
                        .bind("share_scope", shareScope))
                .map(FederationShare.mapKb())
                .first()
                .orElseThrow();
    }

    public boolean deleteKbShare(int id) {
        return query("DELETE FROM federation_kb_share WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Quiz Shares --

    public List<FederationShare> findQuizShares(int stationId) {
        return query("SELECT * FROM federation_quiz_share WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(FederationShare.mapQuiz())
                .all();
    }

    public FederationShare createQuizShare(int stationId, int catalogId, ShareScope shareScope) {
        return query("""
                        INSERT INTO federation_quiz_share(station_id, catalog_id, share_scope)
                        VALUES (:station_id, :catalog_id, :share_scope) RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("catalog_id", catalogId)
                        .bind("share_scope", shareScope))
                .map(FederationShare.mapQuiz())
                .first()
                .orElseThrow();
    }

    public boolean deleteQuizShare(int id) {
        return query("DELETE FROM federation_quiz_share WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Protocol Shares --

    public List<FederationShare> findProtocolShares(int stationId) {
        return query("SELECT * FROM federation_protocol_share WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(FederationShare.mapProtocol())
                .all();
    }

    public FederationShare createProtocolShare(int stationId, int protocolId, ShareScope shareScope) {
        return query("""
                        INSERT INTO federation_protocol_share(station_id, protocol_id, share_scope)
                        VALUES (:station_id, :protocol_id, :share_scope) RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("protocol_id", protocolId)
                        .bind("share_scope", shareScope))
                .map(FederationShare.mapProtocol())
                .first()
                .orElseThrow();
    }

    public boolean deleteProtocolShare(int id) {
        return query("DELETE FROM federation_protocol_share WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Metadata Cache --

    public List<FederationMetadataCache> findCachedMetadata(int partnerId, ContentType contentType) {
        return query(
                        "SELECT * FROM federation_metadata_cache WHERE partner_id = :partner_id AND content_type = :content_type ORDER BY title;")
                .single(call().bind("partner_id", partnerId).bind("content_type", contentType))
                .map(FederationMetadataCache.map())
                .all();
    }

    public void upsertMetadataCache(
            int partnerId, ContentType contentType, int remoteId, String title, String description) {
        query("""
                        INSERT INTO federation_metadata_cache(partner_id, content_type, remote_id, title, description, cached_at)
                        VALUES (:partner_id, :content_type, :remote_id, :title, :description, now())
                        ON CONFLICT (partner_id, content_type, remote_id)
                        DO UPDATE SET title = :title, description = :description, cached_at = now();""")
                .single(call().bind("partner_id", partnerId)
                        .bind("content_type", contentType)
                        .bind("remote_id", remoteId)
                        .bind("title", title)
                        .bind("description", description))
                .insert();
    }

    public void clearMetadataCache(int partnerId) {
        query("DELETE FROM federation_metadata_cache WHERE partner_id = :partner_id;")
                .single(call().bind("partner_id", partnerId))
                .delete();
    }

    // -- Partner by remote station ID (for signature verification) --

    public Optional<FederationPartner> findPartnerByRemoteStationUid(UUID remoteStationUid) {
        return query(
                        "SELECT * FROM federation_partner WHERE partner_station_id = :partner_station_id::uuid AND status = 'ACTIVE' LIMIT 1;")
                .single(call().bind("partner_station_id", remoteStationUid, UUID_STRING))
                .map(FederationPartner.map())
                .first();
    }

    public Optional<FederationPartner> findPartnerByStationAndRemoteUid(int stationId, UUID remoteStationUid) {
        return query(
                        "SELECT * FROM federation_partner WHERE station_id = :station_id AND partner_station_id = :partner_station_id::uuid LIMIT 1;")
                .single(call().bind("station_id", stationId).bind("partner_station_id", remoteStationUid, UUID_STRING))
                .map(FederationPartner.map())
                .first();
    }

    // -- Webhook URL --

    public String getWebhookUrl(int partnerId) {
        return query("SELECT webhook_url FROM federation_partner WHERE id = :id;")
                .single(call().bind("id", partnerId))
                .map(row -> row.getString("webhook_url"))
                .first()
                .orElse(null);
    }

    public void setWebhookUrl(int partnerId, String webhookUrl) {
        query("UPDATE federation_partner SET webhook_url = :webhook_url, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", partnerId).bind("webhook_url", webhookUrl))
                .update();
    }

    // -- Last Sync --

    public void updateLastSyncAt(int partnerId) {
        query("UPDATE federation_partner SET last_sync_at = now(), updated_at = now() WHERE id = :id;")
                .single(call().bind("id", partnerId))
                .update();
    }

    // -- Change Log --

    public void logChange(int stationId, ContentType contentType, int contentId, ChangeType changeType) {
        query("""
                        INSERT INTO federation_change_log(station_id, content_type, content_id, change_type)
                        VALUES (:station_id, :content_type, :content_id, :change_type);""")
                .single(call().bind("station_id", stationId)
                        .bind("content_type", contentType)
                        .bind("content_id", contentId)
                        .bind("change_type", changeType))
                .insert();
    }

    public List<FederationChangeLog> findChangesSince(int stationId, Instant since) {
        return query(
                        "SELECT * FROM federation_change_log WHERE station_id = :station_id AND changed_at > :since ORDER BY changed_at;")
                .single(call().bind("station_id", stationId).bind("since", since, INSTANT_TIMESTAMP))
                .map(FederationChangeLog.map())
                .all();
    }

    public int countPendingRequests(UUID stationUid) {
        return query(
                        "SELECT count(*) AS cnt FROM federation_partner WHERE partner_station_id = :uid::uuid AND status = 'PENDING';")
                .single(call().bind("uid", stationUid, UUID_STRING))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }
}
