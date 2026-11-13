/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationPermission;

/**
 * Defines the restriction table, foreign key column, entity table, entity ID column,
 * combination mode column and management permission for each entity type that supports
 * restrictions.
 * <p>
 * The management permission is checked in Java (not SQL) -
 * a member with this permission bypasses restrictions entirely.
 * <p>
 * An entity may carry more than one list, which is why the mode column is named here rather than
 * assumed: an event says separately who may see it and who may register for it, and each answer
 * combines its parts on its own terms.
 */
public enum RestrictionType {
    EVENT("event_restriction", "event_id", "station_event", "id", "restriction_mode", StationPermission.EVENT_MANAGER),
    EVENT_VIEW(
            "event_view_restriction",
            "event_id",
            "station_event",
            "id",
            "view_restriction_mode",
            StationPermission.EVENT_MANAGER),
    EVENT_TEMPLATE(
            "event_template_restriction",
            "template_id",
            "event_template",
            "id",
            "restriction_mode",
            StationPermission.EVENT_MANAGER),
    EVENT_TEMPLATE_VIEW(
            "event_template_view_restriction",
            "template_id",
            "event_template",
            "id",
            "view_restriction_mode",
            StationPermission.EVENT_MANAGER),
    EVENT_GROUP(
            "event_group_restriction",
            "event_group_id",
            "event_group",
            "id",
            "restriction_mode",
            StationPermission.EVENT_MANAGER),
    EVENT_GROUP_VIEW(
            "event_group_view_restriction",
            "event_group_id",
            "event_group",
            "id",
            "view_restriction_mode",
            StationPermission.EVENT_MANAGER),
    QUIZ_TEST(
            "quiz_test_restriction", "test_id", "quiz_test", "id", "restriction_mode", StationPermission.TEST_MANAGER),
    FORM("form_restriction", "form_id", "form", "id", "restriction_mode", StationPermission.POLL_MANAGER),
    NEWS("news_restriction", "news_id", "news", "id", "restriction_mode", StationPermission.NEWS_MANAGER),
    KB_FOLDER(
            "kb_access_grant", "folder_id", "kb_folder", "id", "restriction_mode", StationPermission.KNOWLEDGE_MANAGER),
    KB_FILE("kb_access_grant", "file_id", "kb_file", "id", "restriction_mode", StationPermission.KNOWLEDGE_MANAGER),
    DOCUMENT_TEMPLATE(
            "document_template_restriction",
            "template_id",
            "document_template",
            "id",
            "restriction_mode",
            StationPermission.DOCUMENT_TEMPLATE_EDIT),
    DOCUMENT_TEMPLATE_STATION_USE(
            "document_template_station_use_restriction",
            "use_id",
            "document_template_station_use",
            "id",
            "restriction_mode",
            StationPermission.DOCUMENT_TEMPLATE_EDIT),
    /**
     * Whom the witnesses of a station reach, one audience per station. Its manager permission is the one
     * that sets it; it never lets a person through, since the audience is about whom a witness picks.
     */
    WITNESS_REACH(
            "witness_reach_restriction",
            "station_id",
            "station",
            "id",
            "witness_reach_mode",
            StationPermission.MEMBER_MANAGER),
    ATTENDANCE_TEMPLATE_TIME_RULE(
            "attendance_template_time_rule_restriction",
            "rule_id",
            "attendance_template_time_rule",
            "id",
            "restriction_mode",
            StationPermission.ATTENDANCE_CONFIGURE,
            false),
    ATTENDANCE_SESSION_TIME_RULE(
            "attendance_session_time_rule_restriction",
            "rule_id",
            "attendance_session_time_rule",
            "id",
            "restriction_mode",
            StationPermission.ATTENDANCE_EDIT,
            false),
    /** The managing party of a procedure: who sees its public steps, ticks the steps marked for them and is told. */
    PROCEDURE(
            "procedure_restriction",
            "procedure_id",
            "procedure",
            "id",
            "restriction_mode",
            StationPermission.PROCEDURE_EDIT,
            true,
            true),
    /**
     * The managing party a procedure template hands to every procedure started from it. It guards
     * nothing itself: the procedure gets a copy, which is what decides.
     */
    PROCEDURE_TEMPLATE(
            "procedure_template_restriction",
            "template_id",
            "procedure_template",
            "id",
            "restriction_mode",
            StationPermission.PROCEDURE_MANAGER,
            false,
            true);

    private final String table;
    private final String fkColumn;
    private final String entityTable;
    private final String entityIdColumn;
    private final String modeColumn;
    private final StationPermission managerPermission;
    private final boolean guardsVisibility;
    private final boolean privateAudience;

    RestrictionType(
            String table,
            String fkColumn,
            String entityTable,
            String entityIdColumn,
            String modeColumn,
            StationPermission managerPermission) {
        this(table, fkColumn, entityTable, entityIdColumn, modeColumn, managerPermission, true);
    }

    RestrictionType(
            String table,
            String fkColumn,
            String entityTable,
            String entityIdColumn,
            String modeColumn,
            StationPermission managerPermission,
            boolean guardsVisibility) {
        this(table, fkColumn, entityTable, entityIdColumn, modeColumn, managerPermission, guardsVisibility, false);
    }

    RestrictionType(
            String table,
            String fkColumn,
            String entityTable,
            String entityIdColumn,
            String modeColumn,
            StationPermission managerPermission,
            boolean guardsVisibility,
            boolean privateAudience) {
        this.guardsVisibility = guardsVisibility;
        this.privateAudience = privateAudience;
        this.table = table;
        this.fkColumn = fkColumn;
        this.entityTable = entityTable;
        this.entityIdColumn = entityIdColumn;
        this.modeColumn = modeColumn;
        this.managerPermission = managerPermission;
    }

    public String table() {
        return table;
    }

    public String fkColumn() {
        return fkColumn;
    }

    public String entityTable() {
        return entityTable;
    }

    public String entityIdColumn() {
        return entityIdColumn;
    }

    public String modeColumn() {
        return modeColumn;
    }

    public StationPermission managerPermission() {
        return managerPermission;
    }

    /**
     * Whether the restriction decides who may see or use something.
     *
     * <p>The attendance time rules do not: they name who is credited with preparation and follow-up
     * time, and are only ever read and written through this registry. They never go through a
     * visibility check, whose manager bypass would credit every attendance editor, and they do not
     * keep a group from being deleted: a rule that loses a group it named stops crediting anybody.
     *
     * @return true for every restriction guarding visibility or use
     */
    public boolean guardsVisibility() {
        return guardsVisibility;
    }

    /**
     * Whether who the restriction takes in is itself kept from those who do not manage it. A procedure's
     * managing party is: a member picker narrowed to it would show a member who runs nothing who does.
     *
     * @return true where only those holding the manager permission may see who passes
     */
    public boolean privateAudience() {
        return privateAudience;
    }
}
