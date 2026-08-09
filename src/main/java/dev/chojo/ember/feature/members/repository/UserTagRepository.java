/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing user tags and their member assignments.
 */
@Singleton
public class UserTagRepository {
    private static final String USER_TAG_COLUMNS = "id, station_id, name, color, visible, position";
    private static final String STATION_MEMBER_COLUMNS =
            "id, station_id, uid, account_id, former, former_at, display_name, user_type, join_date";

    /**
     * Creates a new tag for a station.
     *
     * @param stationId the station identifier
     * @param name      the tag name
     * @return the created tag
     */
    public UserTag create(int stationId, String name) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO user_tag(station_id, name, position)
                VALUES(:station_id, :name, coalesce((SELECT max(position) + 1 FROM user_tag WHERE station_id = :station_id), 0))
                RETURNING %s;""", call().bind("station_id", stationId).bind("name", name), UserTag.map(), USER_TAG_COLUMNS);
    }

    /**
     * Finds a tag by its identifier.
     */
    public Optional<UserTag> findById(int id) {
        return SqlSupport.findById("user_tag", USER_TAG_COLUMNS, id, UserTag.map());
    }

    /**
     * Finds all tags for a station, ordered by name.
     */
    public List<UserTag> findByStation(int stationId) {
        return query("""
                SELECT %s FROM user_tag WHERE station_id = :station_id ORDER BY position DESC, name;""", USER_TAG_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(UserTag.map())
                .all();
    }

    /**
     * Updates a tag's name, color, visibility, and position.
     */
    public boolean update(int id, String name, String color, boolean visible, int position) {
        return query("""
                UPDATE user_tag
                SET
                    name     = :name,
                    color    = :color,
                    visible  = :visible,
                    position = :position
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("visible", visible)
                        .bind("position", position))
                .update()
                .changed();
    }

    /**
     * Deletes a tag by its identifier.
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("user_tag", id);
    }

    /**
     * Finds all members assigned to a tag.
     */
    public List<StationMember> findMembers(int tagId) {
        return query("""
                SELECT %s
                FROM station_member sm JOIN user_tag_entry ute ON sm.id = ute.member_id
                WHERE ute.tag_id = :tag_id;""", SqlSupport.alias("sm", STATION_MEMBER_COLUMNS))
                .single(call().bind("tag_id", tagId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds all tags assigned to a specific member.
     */
    public List<UserTag> findTagsForMember(int memberId) {
        return query("""
                SELECT %s FROM user_tag ut
                JOIN user_tag_entry ute ON ut.id = ute.tag_id
                WHERE ute.member_id = :member_id;""", SqlSupport.alias("ut", USER_TAG_COLUMNS))
                .single(call().bind("member_id", memberId))
                .map(UserTag.map())
                .all();
    }

    /**
     * Adds a member to a tag, ignoring duplicates.
     */
    public void addMember(int tagId, int memberId) {
        query("INSERT INTO user_tag_entry(tag_id, member_id) VALUES(:tag_id, :member_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("tag_id", tagId).bind("member_id", memberId))
                .insert();
    }

    /**
     * Removes a member from a tag.
     */
    public boolean removeMember(int tagId, int memberId) {
        return query("DELETE FROM user_tag_entry WHERE tag_id = :tag_id AND member_id = :member_id;")
                .single(call().bind("tag_id", tagId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    /**
     * Replaces all member assignments for a tag with the given member IDs.
     */
    public void setMembers(int tagId, List<Integer> memberIds) {
        query("DELETE FROM user_tag_entry WHERE tag_id = :tag_id;")
                .single(call().bind("tag_id", tagId))
                .delete();
        for (int memberId : memberIds) {
            addMember(tagId, memberId);
        }
    }
}
