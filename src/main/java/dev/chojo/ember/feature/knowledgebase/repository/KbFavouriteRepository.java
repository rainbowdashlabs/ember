/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import dev.chojo.ember.feature.knowledgebase.entity.KbFavourite;
import dev.chojo.ember.feature.knowledgebase.entity.KbFavouriteTarget;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * The members' favourites in the wiki, of all four kinds.
 *
 * <p>Every read resolves this station's entries against their own tables, so a renamed file shows
 * its new name and one in the trash is not shown at all, while a partner's entry is passed through
 * as it was kept.
 */
@Singleton
public class KbFavouriteRepository {
    private static final String SELECT = """
            SELECT fav.id,
                   fav.member_id,
                   fav.target,
                   COALESCE(fav.file_id, fav.folder_id, fav.partner_entry_id) AS entry_id,
                   fav.partner_station_uid::text AS partner_station_uid,
                   COALESCE(f.name, fo.name, fav.title) AS title,
                   COALESCE(f.file_type, fav.file_type) AS file_type,
                   fav.station_name,
                   fav.created_at
              FROM kb_favourite fav
                   LEFT JOIN kb_file f ON f.id = fav.file_id
                   LEFT JOIN kb_folder fo ON fo.id = fav.folder_id
             WHERE (fav.file_id IS NULL OR f.deleted_at IS NULL)
               AND (fav.folder_id IS NULL OR fo.deleted_at IS NULL)""";

    /** A member's favourites whose entries are not in the trash, newest first. */
    public List<KbFavourite> findByMember(int memberId) {
        return query("""
                %s
                   AND fav.member_id = :member_id
                 ORDER BY fav.created_at DESC;""", SELECT)
                .single(call().bind("member_id", memberId))
                .map(KbFavourite.map())
                .all();
    }

    /** One of a member's favourites, found by what it points at on this station. */
    public Optional<KbFavourite> findLocal(int memberId, KbFavouriteTarget target, int entryId) {
        return query("""
                %s
                   AND fav.member_id = :member_id
                   AND fav.target = :target
                   AND COALESCE(fav.file_id, fav.folder_id) = :entry_id;""", SELECT)
                .single(call().bind("member_id", memberId)
                        .bind("target", target)
                        .bind("entry_id", entryId))
                .map(KbFavourite.map())
                .first();
    }

    /** One of a member's favourites, found by the partner entry it points at. */
    public Optional<KbFavourite> findPartner(
            int memberId, KbFavouriteTarget target, UUID partnerStationUid, int entryId) {
        return query("""
                %s
                   AND fav.member_id = :member_id
                   AND fav.target = :target
                   AND fav.partner_station_uid = :partner_uid::uuid
                   AND fav.partner_entry_id = :entry_id;""", SELECT)
                .single(call().bind("member_id", memberId)
                        .bind("target", target)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("entry_id", entryId))
                .map(KbFavourite.map())
                .first();
    }

    /** Marks a file or folder of this station. Marking it twice leaves one favourite. */
    public void addLocal(int memberId, KbFavouriteTarget target, int entryId) {
        Integer fileId = target == KbFavouriteTarget.FILE ? entryId : null;
        Integer folderId = target == KbFavouriteTarget.FOLDER ? entryId : null;
        query("""
                INSERT INTO kb_favourite(member_id, target, file_id, folder_id)
                VALUES(:member_id, :target, :file_id, :folder_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("member_id", memberId)
                        .bind("target", target)
                        .bind("file_id", fileId)
                        .bind("folder_id", folderId))
                .insert();
    }

    /**
     * Marks a partner's file or folder, keeping what the partner said about it. Marking it again
     * brings the kept values up to date rather than adding a second favourite.
     */
    public void addPartner(
            int memberId,
            KbFavouriteTarget target,
            UUID partnerStationUid,
            int entryId,
            String title,
            String fileType,
            String stationName) {
        query("""
                INSERT INTO kb_favourite(member_id, target, partner_station_uid, partner_entry_id, title, file_type, station_name)
                VALUES(:member_id, :target, :partner_uid::uuid, :entry_id, :title, :file_type, :station_name)
                ON CONFLICT (member_id, target, partner_station_uid, partner_entry_id)
                    WHERE target IN ('PARTNER_FILE', 'PARTNER_FOLDER')
                DO UPDATE SET title = excluded.title,
                              file_type = excluded.file_type,
                              station_name = excluded.station_name;""")
                .single(call().bind("member_id", memberId)
                        .bind("target", target)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("entry_id", entryId)
                        .bind("title", title)
                        .bind("file_type", fileType)
                        .bind("station_name", stationName))
                .insert();
    }

    /**
     * Brings every member's kept values for one partner entry up to date, after the partner has
     * answered for it.
     */
    public void refreshPartner(
            KbFavouriteTarget target,
            UUID partnerStationUid,
            int entryId,
            String title,
            String fileType,
            String stationName) {
        query("""
                UPDATE kb_favourite
                   SET title = :title,
                       file_type = :file_type,
                       station_name = :station_name
                 WHERE target = :target
                   AND partner_station_uid = :partner_uid::uuid
                   AND partner_entry_id = :entry_id;""")
                .single(call().bind("target", target)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("entry_id", entryId)
                        .bind("title", title)
                        .bind("file_type", fileType)
                        .bind("station_name", stationName))
                .update();
    }

    /** Removes one of a member's favourites. */
    public boolean delete(int id, int memberId) {
        return query("DELETE FROM kb_favourite WHERE id = :id AND member_id = :member_id;")
                .single(call().bind("id", id).bind("member_id", memberId))
                .delete()
                .changed();
    }
}
