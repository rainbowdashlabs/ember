/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

/**
 * Defines the restriction table, foreign key column, entity table, entity ID column,
 * and management role for each entity type that supports restrictions.
 * <p>
 * The management role is the role that bypasses restrictions entirely —
 * a member with this role sees all entities of that type.
 */
public enum RestrictionType {
    EVENT("event_restriction", "event_id", "station_event", "id", "EVENT_MANAGER"),
    QUIZ_TEST("quiz_test_restriction", "test_id", "quiz_test", "id", "QUIZ_MANAGER"),
    FORM("form_restriction", "form_id", "form", "id", "POLL_MANAGER"),
    NEWS("news_restriction", "news_id", "news", "id", "NEWS_MANAGER"),
    KB_FOLDER("kb_access_restriction", "folder_id", "kb_folder", "id", "KNOWLEDGE_MANAGER"),
    KB_FILE("kb_access_restriction", "file_id", "kb_file", "id", "KNOWLEDGE_MANAGER");

    private final String table;
    private final String fkColumn;
    private final String entityTable;
    private final String entityIdColumn;
    private final String managerRole;

    RestrictionType(String table, String fkColumn, String entityTable, String entityIdColumn, String managerRole) {
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
    public String managerRole() {
        return managerRole;
    }
}
