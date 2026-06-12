/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * Strategy used when an account or member is deleted, applied per identity column.
 */
public enum Strategy {
    /**
     * Row deleted via FK {@code ON DELETE CASCADE}.
     */
    CASCADE,

    /**
     * Row deleted by explicit code in GdprDeletionService (no FK cascade).
     */
    DELETE_EXPLICIT,

    /**
     * Row retained, identity column replaced with a placeholder (e.g. UUID 00000000-…).
     */
    ANONYMIZE,

    /**
     * Row retained, identity column set to NULL. Requires the column to be nullable.
     */
    NULL,

    /**
     * Row retained as-is. Requires a {@code legalBasis} field.
     */
    RETAIN,

    /**
     * Row retained, but already disconnected from identity at write time.
     */
    RETAIN_UNLINKED,

    /**
     * Column appears in identityColumns but is not GDPR-relevant. Rare.
     */
    NOT_APPLICABLE
}
