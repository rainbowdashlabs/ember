/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kind of legal document served from disk.
 * Wire format uses lowercase identifiers to remain compatible with existing API paths and storage layout.
 */
public enum LegalDocumentType {
    PRIVACY("privacy"),
    TOS("tos"),
    CONSENT("consent"),
    IMPRINT("imprint");

    private final String slug;

    LegalDocumentType(String slug) {
        this.slug = slug;
    }

    @JsonCreator
    public static LegalDocumentType fromSlug(String slug) {
        for (LegalDocumentType type : values()) {
            if (type.slug.equalsIgnoreCase(slug)) return type;
        }
        throw new IllegalArgumentException("Unknown legal document type: " + slug);
    }

    @JsonValue
    public String slug() {
        return slug;
    }
}
