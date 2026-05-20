/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/** Repository for managing user tags and their member assignments. */
@Singleton
public class UserTagRepository {

    /**
     * Creates a new tag for a station.
     *
     * @param stationId the station identifier
     * @param name      the tag name
     * @return the created tag
     */
    public UserTag create(int stationId, String name) {
        return Query.query("INSERT INTO user_tag(station_id, name) VALUES(:station_id, :name) RETURNING *;")
                .single(Call.of().bind("station_id", stationId).bind("name", name))
                .map(UserTag.map())
                .first()
                .orElseThrow();
    }

    /** Finds a tag by its identifier. */
    public Optional<UserTag> findById(int id) {
        return Query.query("SELECT * FROM user_tag WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(UserTag.map())
                .first();
    }

    /** Finds all tags for a station, ordered by name. */
    public List<UserTag> findByStation(int stationId) {
        return Query.query("SELECT * FROM user_tag WHERE station_id = :station_id ORDER BY name;")
                .single(Call.of().bind("station_id", stationId))
                .map(UserTag.map())
                .all();
    }

    /** Updates a tag's name. */
    public boolean update(int id, String name) {
        return Query.query("UPDATE user_tag SET name = :name WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("name", name))
                .update()
                .changed();
    }

    /** Deletes a tag by its identifier. */
    public boolean delete(int id) {
        return Query.query("DELETE FROM user_tag WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Tag entries --

    /** Finds all members assigned to a tag. */
    public List<StationMember> findMembers(int tagId) {
        return Query.query("""
                            SELECT sm.id, sm.station_id, sm.account_id
                            FROM station_member sm JOIN user_tag_entry ute ON sm.id = ute.member_id
                            WHERE ute.tag_id = :tag_id;""")
                .single(Call.of().bind("tag_id", tagId))
                .map(StationMember.map())
                .all();
    }

    /** Finds all tags assigned to a specific member. */
    public List<UserTag> findTagsForMember(int memberId) {
        return Query.query("""
                            SELECT ut.* FROM user_tag ut
                            JOIN user_tag_entry ute ON ut.id = ute.tag_id
                            WHERE ute.member_id = :member_id;""")
                .single(Call.of().bind("member_id", memberId))
                .map(UserTag.map())
                .all();
    }

    /** Adds a member to a tag, ignoring duplicates. */
    public void addMember(int tagId, int memberId) {
        Query.query("INSERT INTO user_tag_entry(tag_id, member_id) VALUES(:tag_id, :member_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("tag_id", tagId).bind("member_id", memberId))
                .insert();
    }

    /** Removes a member from a tag. */
    public boolean removeMember(int tagId, int memberId) {
        return Query.query("DELETE FROM user_tag_entry WHERE tag_id = :tag_id AND member_id = :member_id;")
                .single(Call.of().bind("tag_id", tagId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    /** Replaces all member assignments for a tag with the given member IDs. */
    public void setMembers(int tagId, List<Integer> memberIds) {
        Query.query("DELETE FROM user_tag_entry WHERE tag_id = :tag_id;")
                .single(Call.of().bind("tag_id", tagId))
                .delete();
        for (int memberId : memberIds) {
            addMember(tagId, memberId);
        }
    }
}
