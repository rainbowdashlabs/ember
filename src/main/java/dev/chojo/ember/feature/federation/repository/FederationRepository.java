/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
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
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class FederationRepository {

    // -- Partners --

    public List<FederationPartner> findPartners(int stationId) {
        return Query.query("SELECT * FROM federation_partner WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(FederationPartner.map())
                .all();
    }

    public Optional<FederationPartner> findPartnerById(int id) {
        return Query.query("SELECT * FROM federation_partner WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(FederationPartner.map())
                .first();
    }

    public Optional<FederationPartner> findByInviteCode(String code) {
        return Query.query("SELECT * FROM federation_partner WHERE invite_code = :code;")
                .single(Call.of().bind("code", code))
                .map(FederationPartner.map())
                .first();
    }

    public void createInviteToken(int stationId, String token) {
        Query.query("INSERT INTO federation_invite_token(station_id, token) VALUES(:station_id, :token);")
                .single(Call.of().bind("station_id", stationId).bind("token", token))
                .insert();
    }

    public boolean deleteInviteToken(int stationId, String token) {
        return Query.query("DELETE FROM federation_invite_token WHERE station_id = :station_id AND token = :token;")
                .single(Call.of().bind("station_id", stationId).bind("token", token))
                .delete()
                .changed();
    }

    public List<FederationPartner> findPendingRequestsForStation(int targetStationId) {
        return Query.query(
                        "SELECT * FROM federation_partner WHERE partner_station_id = :target_id AND status = 'PENDING';")
                .single(Call.of().bind("target_id", targetStationId))
                .map(FederationPartner.map())
                .all();
    }

    public FederationPartner createPartner(
            int stationId, int partnerStationId, String inviteCode, String publicKey, String remoteHost) {
        return Query.query("""
                        INSERT INTO federation_partner(station_id, partner_station_id, invite_code, public_key, status, remote_host)
                        VALUES (:station_id, :partner_station_id, :invite_code, :public_key, 'PENDING', :remote_host)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("partner_station_id", partnerStationId)
                        .bind("invite_code", inviteCode)
                        .bind("public_key", publicKey)
                        .bind("remote_host", remoteHost))
                .map(FederationPartner.map())
                .first()
                .orElseThrow();
    }

    public boolean updateRemoteHost(int id, String remoteHost) {
        return Query.query(
                        "UPDATE federation_partner SET remote_host = :remote_host, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("remote_host", remoteHost))
                .update()
                .changed();
    }

    public boolean activatePartner(int id, String partnerPublicKey) {
        return Query.query(
                        "UPDATE federation_partner SET status = 'ACTIVE', partner_public_key = :key, invite_code = NULL, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("key", partnerPublicKey))
                .update()
                .changed();
    }

    public void updateFederationVersion(int id, int version) {
        Query.query("UPDATE federation_partner SET federation_version = :version, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("version", version))
                .update();
    }

    public boolean updatePartnerStatus(int id, String status) {
        return Query.query("UPDATE federation_partner SET status = :status, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("status", status))
                .update()
                .changed();
    }

    public boolean deletePartner(int id) {
        return Query.query("DELETE FROM federation_partner WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Updates the remote_host on all partner records where the given station is the partner.
     */
    public void updateRemoteHostForPartnerStation(int partnerStationId, String remoteHost) {
        Query.query(
                        "UPDATE federation_partner SET remote_host = :remote_host, updated_at = now() WHERE partner_station_id = :partner_station_id;")
                .single(Call.of().bind("partner_station_id", partnerStationId).bind("remote_host", remoteHost))
                .update();
    }

    // -- Capabilities --

    public List<FederationCapability> findCapabilities(int partnerId) {
        return Query.query("SELECT * FROM federation_capability WHERE partner_id = :partner_id;")
                .single(Call.of().bind("partner_id", partnerId))
                .map(FederationCapability.map())
                .all();
    }

    public void upsertCapability(int partnerId, CapabilityType capability, Direction direction, boolean enabled) {
        Query.query("""
                        INSERT INTO federation_capability(partner_id, capability, direction, enabled)
                        VALUES (:partner_id, :capability, :direction, :enabled)
                        ON CONFLICT (partner_id, capability, direction) DO UPDATE SET enabled = :enabled;""")
                .single(Call.of()
                        .bind("partner_id", partnerId)
                        .bind("capability", capability)
                        .bind("direction", direction)
                        .bind("enabled", enabled))
                .insert();
    }

    // -- KB Shares --

    public List<FederationShare> findKbShares(int stationId) {
        return Query.query("SELECT * FROM federation_kb_share WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(FederationShare.mapKb())
                .all();
    }

    public FederationShare createKbShare(int stationId, Integer fileId, Integer folderId, ShareScope shareScope) {
        return Query.query("""
                        INSERT INTO federation_kb_share(station_id, file_id, folder_id, share_scope)
                        VALUES (:station_id, :file_id, :folder_id, :share_scope) RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("file_id", fileId)
                        .bind("folder_id", folderId)
                        .bind("share_scope", shareScope))
                .map(FederationShare.mapKb())
                .first()
                .orElseThrow();
    }

    public boolean deleteKbShare(int id) {
        return Query.query("DELETE FROM federation_kb_share WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Quiz Shares --

    public List<FederationShare> findQuizShares(int stationId) {
        return Query.query("SELECT * FROM federation_quiz_share WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(FederationShare.mapQuiz())
                .all();
    }

    public FederationShare createQuizShare(int stationId, int catalogId, ShareScope shareScope) {
        return Query.query("""
                        INSERT INTO federation_quiz_share(station_id, catalog_id, share_scope)
                        VALUES (:station_id, :catalog_id, :share_scope) RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("catalog_id", catalogId)
                        .bind("share_scope", shareScope))
                .map(FederationShare.mapQuiz())
                .first()
                .orElseThrow();
    }

    public boolean deleteQuizShare(int id) {
        return Query.query("DELETE FROM federation_quiz_share WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Protocol Shares --

    public List<FederationShare> findProtocolShares(int stationId) {
        return Query.query("SELECT * FROM federation_protocol_share WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(FederationShare.mapProtocol())
                .all();
    }

    public FederationShare createProtocolShare(int stationId, int protocolId, ShareScope shareScope) {
        return Query.query("""
                        INSERT INTO federation_protocol_share(station_id, protocol_id, share_scope)
                        VALUES (:station_id, :protocol_id, :share_scope) RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("protocol_id", protocolId)
                        .bind("share_scope", shareScope))
                .map(FederationShare.mapProtocol())
                .first()
                .orElseThrow();
    }

    public boolean deleteProtocolShare(int id) {
        return Query.query("DELETE FROM federation_protocol_share WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Metadata Cache --

    public List<FederationMetadataCache> findCachedMetadata(int partnerId, ContentType contentType) {
        return Query.query(
                        "SELECT * FROM federation_metadata_cache WHERE partner_id = :partner_id AND content_type = :content_type ORDER BY title;")
                .single(Call.of().bind("partner_id", partnerId).bind("content_type", contentType))
                .map(FederationMetadataCache.map())
                .all();
    }

    public void upsertMetadataCache(
            int partnerId, ContentType contentType, int remoteId, String title, String description) {
        Query.query("""
                        INSERT INTO federation_metadata_cache(partner_id, content_type, remote_id, title, description, cached_at)
                        VALUES (:partner_id, :content_type, :remote_id, :title, :description, now())
                        ON CONFLICT (partner_id, content_type, remote_id)
                        DO UPDATE SET title = :title, description = :description, cached_at = now();""")
                .single(Call.of()
                        .bind("partner_id", partnerId)
                        .bind("content_type", contentType)
                        .bind("remote_id", remoteId)
                        .bind("title", title)
                        .bind("description", description))
                .insert();
    }

    public void clearMetadataCache(int partnerId) {
        Query.query("DELETE FROM federation_metadata_cache WHERE partner_id = :partner_id;")
                .single(Call.of().bind("partner_id", partnerId))
                .delete();
    }

    // -- Partner by remote station ID (for signature verification) --

    public Optional<FederationPartner> findPartnerByRemoteStationId(int remoteStationId) {
        return Query.query(
                        "SELECT * FROM federation_partner WHERE partner_station_id = :partner_station_id AND status = 'ACTIVE' LIMIT 1;")
                .single(Call.of().bind("partner_station_id", remoteStationId))
                .map(FederationPartner.map())
                .first();
    }

    // -- Webhook URL --

    public String getWebhookUrl(int partnerId) {
        return Query.query("SELECT webhook_url FROM federation_partner WHERE id = :id;")
                .single(Call.of().bind("id", partnerId))
                .map(row -> row.getString("webhook_url"))
                .first()
                .orElse(null);
    }

    public void setWebhookUrl(int partnerId, String webhookUrl) {
        Query.query("UPDATE federation_partner SET webhook_url = :webhook_url, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", partnerId).bind("webhook_url", webhookUrl))
                .update();
    }

    // -- Last Sync --

    public void updateLastSyncAt(int partnerId) {
        Query.query("UPDATE federation_partner SET last_sync_at = now(), updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", partnerId))
                .update();
    }

    // -- Change Log --

    public void logChange(int stationId, ContentType contentType, int contentId, ChangeType changeType) {
        Query.query("""
                        INSERT INTO federation_change_log(station_id, content_type, content_id, change_type)
                        VALUES (:station_id, :content_type, :content_id, :change_type);""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("content_type", contentType)
                        .bind("content_id", contentId)
                        .bind("change_type", changeType))
                .insert();
    }

    public List<FederationChangeLog> findChangesSince(int stationId, Instant since) {
        return Query.query(
                        "SELECT * FROM federation_change_log WHERE station_id = :station_id AND changed_at > :since ORDER BY changed_at;")
                .single(Call.of().bind("station_id", stationId).bind("since", since, INSTANT_TIMESTAMP))
                .map(FederationChangeLog.map())
                .all();
    }
}
