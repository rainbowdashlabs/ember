/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A document template an appointment asks participants to bring, or one it could ask for.
 *
 * @param templateId the template
 * @param name       what it is called
 * @param kind       what it is made of
 * @param version    its version now, which a copy generated from an older one is behind
 * @param archived   whether it was archived since, which takes it off the participants' list
 */
public record RequiredTemplate(int templateId, String name, DocumentTemplateKind kind, int version, boolean archived) {

    public static RowMapping<RequiredTemplate> map() {
        return row -> new RequiredTemplate(
                row.getInt("template_id"),
                row.getString("name"),
                row.getEnum("kind", DocumentTemplateKind.class),
                row.getInt("version"),
                row.getBoolean("archived"));
    }
}
