/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.FormField;
import dev.chojo.ember.feature.generator.entity.FormFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.PdfOriginal;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Checks what the editor lays over the PDF of a PDF template against the pages and form fields of that
 * PDF.
 *
 * <p>Every field lies on a page the PDF has, inside the part of it that is shown. A text or check
 * field has a text, a text field a size it can be read at, and a signature field a signer, so that no
 * member's document asks one person to sign in two fields. Only a text field keeps a font; whether the
 * station reaches it is checked with the template. A form field is filled only where the PDF has one
 * of that name that can be filled. A binding
 * with an empty text is no binding: the form field keeps what it shows.
 *
 * <p>A template without a PDF can have no fields yet, since there are no pages to hold them.
 */
final class PdfLayoutChecks {
    /** The most fields and filled form fields a template holds together. */
    static final int MAX_FIELDS = 200;

    /** The smallest text size a field may ask for. */
    static final double MIN_FONT_SIZE = 4;

    /** The largest text size a field may ask for. */
    static final double MAX_FONT_SIZE = 72;

    /** The text size a field without one starts at. */
    static final double DEFAULT_FONT_SIZE = 10;

    private PdfLayoutChecks() {}

    /**
     * @param original the PDF the template fills now, or null where none was uploaded
     * @param fields   the fields as the editor sent them
     * @param bindings the form fields' values as the editor sent them
     * @param maxText  the longest text a field may hold
     * @return the content as it is to be written
     */
    static PdfContent check(
            @Nullable PdfOriginal original,
            @Nullable List<PdfField> fields,
            @Nullable List<FormBinding> bindings,
            int maxText) {
        var sentFields = Objects.requireNonNullElse(fields, List.<PdfField>of());
        var sentBindings = Objects.requireNonNullElse(bindings, List.<FormBinding>of());
        if (sentFields.size() + sentBindings.size() > MAX_FIELDS) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TOO_MANY_FIELDS.raise();
        }
        if (original == null) {
            if (!sentFields.isEmpty() || !sentBindings.isEmpty()) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING.raise();
            }
            return new PdfContent(null, PdfLayout.empty());
        }
        var checked = sentFields.stream()
                .map(field -> field(original, field, maxText))
                .toList();
        requireEachSignerOnce(checked);
        return new PdfContent(original, new PdfLayout(checked, bindings(original, sentBindings, maxText)));
    }

    private static PdfField field(PdfOriginal original, @Nullable PdfField field, int maxText) {
        if (field == null || field.kind() == null || field.rect() == null) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_INCOMPLETE.raise();
        }
        var rect = requireOnPage(original, field.rect());
        if (field.kind() == PdfFieldKind.SIGNATURE) return signature(field, rect, maxText);
        String text = Objects.requireNonNullElse(field.text(), "").strip();
        if (text.isEmpty()) throw DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_INCOMPLETE.raise();
        requireFits(text, maxText);
        double size = checkedSize(field);
        var align = Objects.requireNonNullElse(field.align(), TextAlign.LEFT);
        if (field.kind() != PdfFieldKind.TEXT) return new PdfField(field.kind(), rect, text, size, align, false, null);
        return new PdfField(
                PdfFieldKind.TEXT,
                rect,
                text,
                size,
                align,
                field.wrap(),
                null,
                familyOf(field.fontFamily()),
                Objects.requireNonNullElse(field.fontStyle(), FontStyle.REGULAR));
    }

    /**
     * A signature field as it is kept: its signer, whether it draws its line, and its text, which may be
     * empty and is kept even while it does not print, so switching the printing on brings it back.
     */
    private static PdfField signature(PdfField field, FieldRect rect, int maxText) {
        var role = field.role();
        if (role == null) throw DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_INCOMPLETE.raise();
        String text = Objects.requireNonNullElse(field.text(), "").strip();
        requireFits(text, maxText);
        return new PdfField(
                PdfFieldKind.SIGNATURE,
                rect,
                text.isEmpty() ? null : text,
                checkedSize(field),
                Objects.requireNonNullElse(field.align(), TextAlign.LEFT),
                false,
                role,
                null,
                FontStyle.REGULAR,
                field.withoutLine(),
                field.printText());
    }

    private static void requireFits(String text, int maxText) {
        if (text.length() > maxText) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(maxText));
        }
    }

    private static double checkedSize(PdfField field) {
        double size = field.fontSize() > 0 ? field.fontSize() : DEFAULT_FONT_SIZE;
        if (size < MIN_FONT_SIZE || size > MAX_FONT_SIZE) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_SIZE_OUT_OF_BOUNDS.raise();
        }
        return size;
    }

    /** The family a text field names, or null for the default font where it names none. */
    private static @Nullable String familyOf(@Nullable String family) {
        return family == null || family.isBlank() ? null : family.strip();
    }

    private static FieldRect requireOnPage(PdfOriginal original, FieldRect rect) {
        boolean onPage = rect.width() > 0
                && rect.height() > 0
                && original.inspection()
                        .page(rect.page())
                        .map(page -> page.holds(rect))
                        .orElse(false);
        if (!onPage) throw DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_OFF_PAGE.raise();
        return rect;
    }

    private static void requireEachSignerOnce(List<PdfField> fields) {
        var roles = fields.stream().map(PdfField::role).filter(Objects::nonNull).toList();
        if (!SignatureRole.distinctForEveryMember(roles)) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_TWICE.raise();
        }
    }

    private static List<FormBinding> bindings(PdfOriginal original, List<FormBinding> sent, int maxText) {
        var byName = new LinkedHashMap<String, FormBinding>();
        for (var binding : sent) {
            if (binding == null) continue;
            String name = binding.fieldName();
            String text = Objects.requireNonNullElse(binding.text(), "").strip();
            if (name == null || text.isEmpty()) continue;
            if (text.length() > maxText) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(maxText));
            }
            boolean fillable = original.inspection()
                    .formField(name)
                    .map(FormField::kind)
                    .map(FormFieldKind::fillable)
                    .orElse(false);
            if (!fillable) throw DocumentRefusal.DOCUMENT_TEMPLATE_FORM_FIELD_UNKNOWN.raise(RefusalDetail.text(name));
            byName.put(name, new FormBinding(name, text));
        }
        return List.copyOf(byName.values());
    }
}
