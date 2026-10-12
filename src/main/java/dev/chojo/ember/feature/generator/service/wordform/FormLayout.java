/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.service.GapWords;
import dev.chojo.ember.feature.generator.service.wordform.FormSpot.FormSpotKind;
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lays the fields of a PDF template over a converted Word form, one for each place of the form.
 *
 * <p>A text place whose label {@link GapWords} recognises prints that value of the member. Every other
 * place becomes a question to the person generating: a text, a day where the form asks for a date or the
 * label reads like one ("Datum", "… am"), a tick for a check box. A preset of the form becomes the
 * suggestion of its question, a ticked box one that says yes. Where the form asks for the first name
 * apart, "Name" is the surname.
 *
 * <p>LibreOffice makes a text field only as wide as the blank it held, so a text field is widened up to
 * whatever follows it on its line, the next field or the next printed letter, or to the right margin where
 * nothing does. A field never runs over words of the form, such as "übernommen worden" after a blank.
 */
final class FormLayout {
    /** The size text is printed in, the largest a field shrinks a long value from. */
    static final double FONT_SIZE = 10;

    /** The room left between a widened field and the next one on its line. */
    private static final double GAP = 4;

    /** The right margin taken where the form names none, about two centimetres. */
    private static final double DEFAULT_MARGIN = 56;

    /** What a suggestion that ticks a box says. */
    private static final String YES = "Ja";

    private final Map<String, String> fields;
    private final Set<String> keys = new HashSet<>();
    private final List<PdfField> laid = new ArrayList<>();
    private final List<TemplateQuestion> questions = new ArrayList<>();
    private final List<String> recognised = new ArrayList<>();
    private final List<String> unplaced = new ArrayList<>();

    private FormLayout(Map<String, String> fields) {
        this.fields = fields;
    }

    /**
     * The fields and questions of a form.
     *
     * @param fields     the fields in the order the form holds its places
     * @param questions  the questions, in the same order
     * @param recognised the labels of the places that print a value of the member
     * @param unplaced   the labels of the places LibreOffice made no form field for, which got no field
     */
    record Laid(
            List<PdfField> fields, List<TemplateQuestion> questions, List<String> recognised, List<String> unplaced) {}

    /**
     * @param spots       the places of the form, in order
     * @param taken       where the form fields of the places are on the converted PDF
     * @param rightMargin the right margin of the page in points, or 0 where the form names none
     * @param fields      the keys of the owner's profile questions by their names in lower case
     * @return the fields and questions
     */
    static Laid lay(List<FormSpot> spots, FormWidgets.Taken taken, double rightMargin, Map<String, String> fields) {
        var layout = new FormLayout(fields);
        boolean firstNameApart = spots.stream()
                .anyMatch(spot -> BuiltInPlaceholder.MEMBER_FIRST_NAME.key().equals(layout.memberKey(spot, false)));
        for (var spot : spots) {
            var rect = taken.places().get(spot.tag());
            if (rect == null) {
                layout.unplaced.add(labelOf(spot));
                continue;
            }
            layout.place(spot, rect, firstNameApart);
        }
        var widened = layout.laid.stream()
                .map(field -> widened(field, layout.laid, taken, rightMargin))
                .toList();
        return new Laid(
                widened, List.copyOf(layout.questions), List.copyOf(layout.recognised), List.copyOf(layout.unplaced));
    }

    private void place(FormSpot spot, FieldRect rect, boolean firstNameApart) {
        String label = labelOf(spot);
        if (spot.kind() == FormSpotKind.CHECK) {
            var question = ask(label, TemplateQuestionKind.CHECK, "true".equals(spot.preset()) ? YES : "");
            laid.add(field(PdfFieldKind.CHECK, rect, question.placeholderKey(), TextAlign.CENTER));
            return;
        }
        String key = memberKey(spot, firstNameApart);
        if (key != null) {
            recognised.add(label);
            laid.add(field(PdfFieldKind.TEXT, rect, key, TextAlign.LEFT));
            return;
        }
        var kind = spot.kind() == FormSpotKind.DATE || readsLikeADay(label)
                ? TemplateQuestionKind.DATE
                : TemplateQuestionKind.TEXT;
        String preset = spot.preset();
        var question = ask(label, kind, preset == null ? "" : preset);
        laid.add(field(PdfFieldKind.TEXT, rect, question.placeholderKey(), TextAlign.LEFT));
    }

    private @Nullable String memberKey(FormSpot spot, boolean firstNameApart) {
        if (spot.kind() == FormSpotKind.CHECK) return null;
        String key = GapWords.keyOf(spot.label(), fields);
        if (firstNameApart
                && BuiltInPlaceholder.MEMBER_FULL_NAME.key().equals(key)
                && "name".equalsIgnoreCase(spot.label())) {
            return BuiltInPlaceholder.MEMBER_LAST_NAME.key();
        }
        return key;
    }

    private TemplateQuestion ask(String label, TemplateQuestionKind kind, String suggestion) {
        var question = new TemplateQuestion(freeKey(label), label, kind, suggestion, false);
        questions.add(question);
        return question;
    }

    private static PdfField field(PdfFieldKind kind, FieldRect rect, String key, TextAlign align) {
        return new PdfField(kind, rect, "{{" + key + "}}", FONT_SIZE, align, false, null);
    }

    private static String labelOf(FormSpot spot) {
        return spot.label().isEmpty() ? "Feld " + spot.number() : spot.label();
    }

    private static boolean readsLikeADay(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.equals("am") || lower.endsWith(" am") || lower.contains("datum");
    }

    /** A key for a question made from its label, unique within the form. */
    private String freeKey(String label) {
        String base = Normalizer.normalize(
                        label.toLowerCase(Locale.ROOT)
                                .replace("ä", "ae")
                                .replace("ö", "oe")
                                .replace("ü", "ue")
                                .replace("ß", "ss"),
                        Normalizer.Form.NFKD)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "feld";
        if (base.length() > 36) base = base.substring(0, 36).replaceAll("_+$", "");
        String key = base;
        for (int count = 2; !keys.add(key); count++) key = base + "_" + count;
        return key;
    }

    /**
     * A text field stretched to the next field or printed letter on its line, or to the right margin where
     * nothing follows. A check field keeps its box.
     */
    private static PdfField widened(PdfField field, List<PdfField> all, FormWidgets.Taken taken, double rightMargin) {
        if (field.kind() != PdfFieldKind.TEXT) return field;
        var rect = field.rect();
        double[] pages = taken.pages();
        double pageWidth = rect.page() <= pages.length ? pages[rect.page() - 1] : rect.right();
        double bound = pageWidth - (rightMargin > 0 ? rightMargin : DEFAULT_MARGIN);
        var obstacles = new ArrayList<>(taken.text());
        all.stream().filter(other -> other != field).forEach(other -> obstacles.add(other.rect()));
        for (var next : obstacles) {
            if (next.page() != rect.page() || next.x() < rect.right() - 1) continue;
            double overlap = Math.min(rect.top(), next.top()) - Math.max(rect.y(), next.y());
            if (overlap > Math.min(rect.height(), next.height()) / 2) bound = Math.min(bound, next.x() - GAP);
        }
        double width = Math.max(rect.width(), bound - rect.x());
        return new PdfField(
                field.kind(),
                new FieldRect(rect.page(), rect.x(), rect.y(), width, rect.height()),
                field.text(),
                field.fontSize(),
                field.align(),
                field.wrap(),
                field.role());
    }
}
