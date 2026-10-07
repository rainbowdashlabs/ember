/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What a PDF template lays over its PDF: the fields drawn on its pages and what the PDF's own form
 * fields are filled with. It outlasts a new upload of the PDF, which is how the fields of an old
 * version can be checked against the pages of a new one.
 *
 * @param fields   the fields drawn on the pages
 * @param bindings what the form fields of the PDF are filled with
 */
public record PdfLayout(List<PdfField> fields, List<FormBinding> bindings) {

    public PdfLayout {
        fields = fields == null ? List.of() : List.copyOf(fields);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }

    /** A layout with nothing on it. */
    public static PdfLayout empty() {
        return new PdfLayout(List.of(), List.of());
    }

    /** @return every text of the fields and the form fields that can name placeholders */
    public Stream<String> texts() {
        return Stream.concat(
                fields.stream().map(PdfField::text).filter(Objects::nonNull),
                bindings.stream().map(FormBinding::text));
    }

    /** @return the family of uploaded fonts every field names that does not print in the default font */
    public Stream<String> fontFamilies() {
        return fields.stream().map(PdfField::fontFamily).filter(Objects::nonNull);
    }
}
