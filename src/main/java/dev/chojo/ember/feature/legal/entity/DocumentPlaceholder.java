/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import java.util.List;

/**
 * A {@code {{ name }}} token found in the legal documents, together with the value an
 * administrator has given it and every place it appears.
 *
 * @param name   the placeholder name as written between the braces
 * @param value  the configured replacement, empty while none is set
 * @param usages every section the placeholder appears in
 */
public record DocumentPlaceholder(String name, String value, List<Usage> usages) {

    /**
     * One section a placeholder appears in.
     *
     * @param type    the document type slug ({@code privacy}, {@code tos}, …)
     * @param locale  the locale the section belongs to
     * @param section the section name without ordering prefix or extension
     */
    public record Usage(String type, String locale, String section) {}
}
