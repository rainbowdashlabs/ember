/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing user tags and their member assignments.
 */
@Singleton
public class UserTagRepository {
    private static final String USER_TAG_COLUMNS = "id, station_id, name, color, visibility, position";
    private static final String STATION_MEMBER_COLUMNS = StationMember.COLUMNS;

    /**
     * Creates a new tag for a station.
     *
     * @param stationId the station identifier
     * @param name      the tag name
     * @return the created tag
     */
    public UserTag create(int stationId, String name) {
        return create(stationId, name, null, TagVisibility.PLAIN);
    }

    /**
     * Creates a new tag for a station, placed above every tag it already has.
     *
     * @param stationId  the station identifier
     * @param name       the tag name
     * @param color      the badge colour, or {@code null} for none
     * @param visibility who sees the tag
     * @return the created tag
     */
    public UserTag create(int stationId, String name, @Nullable String color, TagVisibility visibility) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO user_tag(station_id, name, color, visibility, position)
                VALUES(:station_id, :name, :color, :visibility, coalesce((SELECT max(position) + 1 FROM user_tag WHERE station_id = :station_id), 0))
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("visibility", visibility),
                UserTag.map(),
                USER_TAG_COLUMNS);
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
    public boolean update(int id, String name, @Nullable String color, TagVisibility visibility, int position) {
        return query("""
                UPDATE user_tag
                SET
                    name       = :name,
                    color      = :color,
                    visibility = :visibility,
                    position   = :position
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("visibility", visibility)
                        .bind("position", position))
                .update()
                .changed();
    }

    /**
     * Which of these tags are private.
     *
     * @param tagIds the tags
     * @return the ids among them whose tag is private
     */
    public Set<Integer> findPrivateIds(Collection<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return Set.of();
        return new HashSet<>(query("""
                SELECT id FROM user_tag WHERE id = ANY(:tag_ids) AND visibility = 'PRIVATE';""")
                .single(call().bind("tag_ids", List.copyOf(tagIds), PostgreSqlTypes.INTEGER))
                .map(row -> row.getInt("id"))
                .all());
    }

    /**
     * How many audiences, restrictions and access rules select people by this tag.
     *
     * <p>These are the places that store the tag as a reference of their own. A member question or a
     * page member list keeps it inside its configuration instead and is not counted: a private tag
     * there lets nobody through, so it shows nothing.
     *
     * @param tagId the tag
     * @return the number of rows naming the tag
     */
    public long countSelectingUses(int tagId) {
        return query("""
                SELECT (SELECT count(*) FROM event_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM event_view_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM event_template_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM event_template_view_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM quiz_test_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM form_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM news_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM document_template_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM document_template_station_use_restriction WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM kb_access_grant WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM checklist_member_filter WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM board_view_access WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM board_edit_access WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM federation_board_local_view_override WHERE tag_id = :tag_id)
                     + (SELECT count(*) FROM federation_board_local_edit_override WHERE tag_id = :tag_id) AS uses;""")
                .single(call().bind("tag_id", tagId))
                .map(row -> row.getLong("uses"))
                .first()
                .orElse(0L);
    }

    /**
     * Deletes a tag by its identifier.
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("user_tag", id);
    }

    /**
     * Finds all members assigned to a tag. Only members of the tag's own station count: an entry naming
     * somebody of another station, which an unchecked write could once leave behind, is never read.
     */
    public List<StationMember> findMembers(int tagId) {
        return query("""
                SELECT %s
                FROM station_member sm
                JOIN user_tag_entry ute ON sm.id = ute.member_id
                JOIN user_tag ut ON ut.id = ute.tag_id AND ut.station_id = sm.station_id
                WHERE ute.tag_id = :tag_id;""", SqlSupport.alias("sm", STATION_MEMBER_COLUMNS))
                .single(call().bind("tag_id", tagId))
                .map(StationMember.map())
                .all();
    }

    /**
     * The tag each of these members wears beside their name, which is the highest placed visible one
     * that carries a colour.
     *
     * <p>One query for a whole list, for the same reason the colours are fetched that way: a round trip
     * a row is most of what a page of two hundred costs.
     *
     * @param memberIds the members
     * @return member id to tag, holding only the members that wear one
     */
    public Map<Integer, UserTag> findDisplayTags(Collection<Integer> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return Map.of();
        Map<Integer, UserTag> tags = new HashMap<>();
        for (var row : query("""
                SELECT DISTINCT ON (ute.member_id) ute.member_id, %s
                FROM user_tag_entry ute
                JOIN user_tag ut ON ut.id = ute.tag_id
                JOIN station_member sm ON sm.id = ute.member_id AND sm.station_id = ut.station_id
                WHERE ute.member_id = ANY(:member_ids)
                  AND ut.visibility = 'BADGE'
                  AND ut.color IS NOT NULL AND ut.color <> ''
                ORDER BY ute.member_id, ut.position DESC;""", SqlSupport.alias("ut", USER_TAG_COLUMNS))
                .single(call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("member_id"), UserTag.map().map(row)))
                .all()) {
            tags.put(row.getKey(), row.getValue());
        }
        return tags;
    }

    /**
     * The tags each of these members carries that are not private, in one query for the whole list.
     * Private tags are left out because results grouped or filtered by them would show who carries
     * them.
     *
     * @param memberIds the members
     * @return member id to the ids of their tags, holding only members with at least one such tag
     */
    public Map<Integer, Set<Integer>> findOpenTagIdsOfMembers(Collection<Integer> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return Map.of();
        Map<Integer, Set<Integer>> tags = new HashMap<>();
        for (var entry : query("""
                SELECT ute.member_id, ute.tag_id
                FROM user_tag_entry ute
                JOIN user_tag ut ON ut.id = ute.tag_id
                JOIN station_member sm ON sm.id = ute.member_id AND sm.station_id = ut.station_id
                WHERE ute.member_id = ANY(:member_ids)
                  AND ut.visibility <> 'PRIVATE';""")
                .single(call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("member_id"), row.getInt("tag_id")))
                .all()) {
            tags.computeIfAbsent(entry.getKey(), _ -> new HashSet<>()).add(entry.getValue());
        }
        return tags;
    }

    /**
     * Finds all tags of the member's own station assigned to a specific member.
     */
    public List<UserTag> findTagsForMember(int memberId) {
        return query("""
                SELECT %s FROM user_tag ut
                JOIN user_tag_entry ute ON ut.id = ute.tag_id
                JOIN station_member sm ON sm.id = ute.member_id AND sm.station_id = ut.station_id
                WHERE ute.member_id = :member_id;""", SqlSupport.alias("ut", USER_TAG_COLUMNS))
                .single(call().bind("member_id", memberId))
                .map(UserTag.map())
                .all();
    }

    /**
     * Adds a member to a tag, ignoring duplicates and anybody who is not a member of the tag's station.
     */
    public void addMember(int tagId, int memberId) {
        query("""
                INSERT INTO user_tag_entry(tag_id, member_id)
                SELECT ut.id, sm.id
                FROM user_tag ut
                JOIN station_member sm ON sm.station_id = ut.station_id
                WHERE ut.id = :tag_id AND sm.id = :member_id
                ON CONFLICT DO NOTHING;""")
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
