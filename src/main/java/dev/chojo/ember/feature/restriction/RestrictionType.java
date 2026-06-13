/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationPermission;

/**
 * Defines the restriction table, foreign key column, entity table, entity ID column,
 * and management permission for each entity type that supports restrictions.
 * <p>
 * The management permission is checked in Java (not SQL) —
 * a member with this permission bypasses restrictions entirely.
 */
public enum RestrictionType {
    EVENT("event_restriction", "event_id", "station_event", "id", StationPermission.EVENT_MANAGER),
    QUIZ_TEST("quiz_test_restriction", "test_id", "quiz_test", "id", StationPermission.TEST_MANAGER),
    FORM("form_restriction", "form_id", "form", "id", StationPermission.POLL_MANAGER),
    NEWS("news_restriction", "news_id", "news", "id", StationPermission.NEWS_MANAGER),
    KB_FOLDER("kb_access_restriction", "folder_id", "kb_folder", "id", StationPermission.KNOWLEDGE_MANAGER),
    KB_FILE("kb_access_restriction", "file_id", "kb_file", "id", StationPermission.KNOWLEDGE_MANAGER);

    private final String table;
    private final String fkColumn;
    private final String entityTable;
    private final String entityIdColumn;
    private final StationPermission managerPermission;

    RestrictionType(
            String table,
            String fkColumn,
            String entityTable,
            String entityIdColumn,
            StationPermission managerPermission) {
        this.table = table;
        this.fkColumn = fkColumn;
        this.entityTable = entityTable;
        this.entityIdColumn = entityIdColumn;
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

    public StationPermission managerPermission() {
        return managerPermission;
    }
}
