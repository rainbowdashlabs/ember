/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import io.javalin.openapi.OpenApiName;

/**
 * One page of a form. Every form has at least one, and every question stands on one of them.
 *
 * @param id          unique page identifier
 * @param formId      the form the page belongs to
 * @param key         stable key of the page within its form, which the page that follows and the path a
 *                    response took name it by
 * @param position    display order position (0-based)
 * @param title       optional title shown above the page
 * @param description optional text shown under the title
 * @param after       where the reader goes once the page is done, unless an answer on it decides
 */
@OpenApiName("FormPage")
public record FormPage(
        int id, int formId, String key, int position, String title, String description, PageTarget after) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<FormPage> map() {
        return row -> new FormPage(
                row.getInt("id"),
                row.getInt("form_id"),
                row.getString("page_key"),
                row.getInt("position"),
                row.getString("title"),
                row.getString("description"),
                new PageTarget(row.getEnum("after_kind", PageTarget.TargetKind.class), row.getString("after_page")));
    }
}
