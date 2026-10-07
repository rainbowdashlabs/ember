/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * What a form field the uploaded PDF brings is filled with.
 *
 * <p>A text field takes the text with its placeholders filled in; a check box is ticked where that
 * text says yes, the same way a check field drawn on the page decides.
 *
 * @param fieldName the fully qualified name of the form field
 * @param text      the text with placeholders
 */
public record FormBinding(String fieldName, String text) {

    public static RowMapping<FormBinding> map() {
        return row -> new FormBinding(row.getString("field_name"), row.getString("text"));
    }
}
