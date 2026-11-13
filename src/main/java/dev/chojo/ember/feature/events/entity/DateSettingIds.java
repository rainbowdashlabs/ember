/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * The attendance template and category an appointment or an event group names itself, as stored.
 *
 * @param templateId the attendance template, or null for none
 * @param categoryId the category, or null for none
 */
public record DateSettingIds(
        @Nullable Integer templateId, @Nullable Integer categoryId) {

    /** Reads the ids from a row holding {@code template_id} and {@code category_id}. */
    public static RowMapping<DateSettingIds> map() {
        return row -> new DateSettingIds(
                row.getObject("template_id", Integer.class), row.getObject("category_id", Integer.class));
    }
}
