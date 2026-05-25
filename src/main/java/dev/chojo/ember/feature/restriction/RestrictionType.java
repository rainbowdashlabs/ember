/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.Roles;

/**
 * Defines the restriction table, foreign key column, entity table, entity ID column,
 * and management role for each entity type that supports restrictions.
 * <p>
 * The management role is the role that bypasses restrictions entirely —
 * a member with this role sees all entities of that type.
 */
public enum RestrictionType {
    EVENT("event_restriction", "event_id", "station_event", "id", Roles.EVENT_MANAGER),
    QUIZ_TEST("quiz_test_restriction", "test_id", "quiz_test", "id", Roles.QUIZ_MANAGER),
    FORM("form_restriction", "form_id", "form", "id", Roles.POLL_MANAGER),
    NEWS("news_restriction", "news_id", "news", "id", Roles.NEWS_MANAGER),
    // KB restrictions are checked via direct queries in KnowledgeBaseRepository, not via check_restriction()
    KB_FOLDER("kb_access_restriction", "folder_id", "kb_folder", "id", Roles.KNOWLEDGE_MANAGER),
    KB_FILE("kb_access_restriction", "file_id", "kb_file", "id", Roles.KNOWLEDGE_MANAGER);

    private final String table;
    private final String fkColumn;
    private final String entityTable;
    private final String entityIdColumn;
    private final Roles managerRole;

    RestrictionType(String table, String fkColumn, String entityTable, String entityIdColumn, Roles managerRole) {
        this.table = table;
        this.fkColumn = fkColumn;
        this.entityTable = entityTable;
        this.entityIdColumn = entityIdColumn;
        this.managerRole = managerRole;
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

    /**
     * The role name that bypasses restrictions for this entity type.
     */
    public Roles managerRole() {
        return managerRole;
    }
}
