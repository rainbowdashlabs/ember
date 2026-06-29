/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import org.slf4j.Logger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * A station member with all associated data (roles, groups, tags, profile values) aggregated
 * from a single SQL query. Used by the rich member list endpoint to avoid N+1 queries.
 *
 * @param id                   the station member identifier
 * @param stationId            the station this member belongs to
 * @param uid                  the stable UUID for federation identity
 * @param accountId            the linked account identifier, or null for decoupled former members
 * @param name                 the display name (from account first/last name or display_name fallback)
 * @param email                the account email address
 * @param accountSetupPending  {@code true} when the linked account has never been logged into; flagged in the
 *                             member list so managers can chase the recipient or resend the setup mail
 * @param former               whether this member has been marked as a former member
 * @param userType             the station user type (e.g. MEMBER, TEAM, MANAGER)
 * @param roles                the list of role names assigned to this member
 * @param groups               the list of groups this member belongs to
 * @param tags                 the list of tags assigned to this member
 * @param profileValues        a map of field ID to field value for profile fields
 */
public record RichMember(
        int id,
        int stationId,
        UUID uid,
        Integer accountId,
        String name,
        String email,
        boolean accountSetupPending,
        boolean former,
        StationUserType userType,
        LocalDate joinDate,
        List<String> roles,
        List<GroupEntry> groups,
        List<TagEntry> tags,
        Map<String, Object> profileValues,
        MemberIdentity identity) {

    private static final Logger log = getLogger(RichMember.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<GroupEntry>> GROUP_LIST = new TypeReference<>() {};
    private static final TypeReference<List<TagEntry>> TAG_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> STRING_MAP = new TypeReference<>() {};

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<RichMember> map() {
        return row -> new RichMember(
                row.getInt("id"),
                row.getInt("station_id"),
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getObject("account_id", Integer.class),
                row.getString("name"),
                row.getString("email"),
                row.getBoolean("account_setup_pending"),
                row.getBoolean("former"),
                row.getEnum("user_type", StationUserType.class),
                row.getDate("join_date") != null ? row.getDate("join_date").toLocalDate() : null,
                parseJson(row.getString("roles"), STRING_LIST, List.of()),
                parseJson(row.getString("groups"), GROUP_LIST, List.of()),
                parseJson(row.getString("tags"), TAG_LIST, List.of()),
                parseJson(row.getString("profile_values"), STRING_MAP, Map.of()),
                null);
    }

    private static <T> T parseJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to parse JSON column: {}", json, e);
            return fallback;
        }
    }

    /**
     * A group entry with id and name.
     */
    public RichMember withIdentity(MemberIdentity identity) {
        return new RichMember(
                id,
                stationId,
                uid,
                accountId,
                name,
                email,
                accountSetupPending,
                former,
                userType,
                joinDate,
                roles,
                groups,
                tags,
                profileValues,
                identity);
    }

    public record GroupEntry(int id, String name) {}

    /**
     * A tag entry with id and name.
     */
    public record TagEntry(int id, String name) {}
}
