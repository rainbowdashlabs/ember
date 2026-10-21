/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.QuestionLimits;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.service.GapWords;
import dev.chojo.ember.feature.generator.service.wordform.FormSpot.FormSpotKind;
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * nothing does. The box never reaches over words of the form, such as "übernommen worden" after a blank.
 *
 * <p>A text field prints in the size the form sets its place in, bold where the place is, and in the family
 * chosen for the place's family, so a filled in value looks like the text around it. Where no family is
 * chosen the field prints in the template's default font. It keeps that size for a value longer than its
 * box and runs on past it, over whatever follows, the way a value written in by hand would.
 */
final class FormLayout {
    /** The size text is printed in where the form sets none that could be read, and the size of a check box. */
    static final double FONT_SIZE = 10;

    /** The room left between a widened field and the next one on its line. */
    private static final double GAP = 4;

    /** The right margin taken where the form names none, about two centimetres. */
    private static final double DEFAULT_MARGIN = 56;

    /** What a suggestion that ticks a box says. */
    private static final String YES = "Ja";

    private final Map<String, String> fields;
    private final Map<String, TextFont> placeFonts;
    private final Map<String, String> fieldFamilies;
    private final Set<String> keys = new HashSet<>();
    private final List<PdfField> laid = new ArrayList<>();
    private final List<TemplateQuestion> questions = new ArrayList<>();
    private final List<String> recognised = new ArrayList<>();
    private final List<String> unplaced = new ArrayList<>();
    private final Map<String, String> samples = new LinkedHashMap<>();

    private FormLayout(
            Map<String, String> fields, Map<String, TextFont> placeFonts, Map<String, String> fieldFamilies) {
        this.fields = fields;
        this.placeFonts = placeFonts;
        this.fieldFamilies = fieldFamilies;
    }

    /**
     * The fields and questions of a form.
     *
     * @param fields     the fields in the order the form holds its places
     * @param questions  the questions, in the same order
     * @param recognised the labels of the places that print a value of the member
     * @param unplaced   the labels of the places LibreOffice made no form field for, which got no field
     * @param samples    the example value of each field for a preview, by the placeholder key it prints
     */
    record Laid(
            List<PdfField> fields,
            List<TemplateQuestion> questions,
            List<String> recognised,
            List<String> unplaced,
            Map<String, String> samples) {}

    /**
     * @param spots  the places of the form, in order
     * @param fields the keys of the owner's profile questions by their names in lower case
     * @return the example value of each place by {@link FormSpot#tag()}, the same {@link #lay} gives the
     *     field laid over it
     */
    static Map<String, String> samples(List<FormSpot> spots, Map<String, String> fields) {
        var layout = new FormLayout(fields, Map.of(), Map.of());
        boolean firstNameApart = layout.firstNameApart(spots);
        var samples = new LinkedHashMap<String, String>();
        for (var spot : spots) samples.put(spot.tag(), layout.sampleOf(spot, firstNameApart));
        return samples;
    }

    /**
     * @param spots         the places of the form, in order
     * @param taken         where the form fields of the places are on the converted PDF
     * @param rightMargin   the right margin of the page in points, or 0 where the form names none
     * @param placeFonts   the font each place is set in, by {@link FormSpot#tag()}
     * @param fieldFamilies the family a field prints in, by the family the form names for its place
     * @param fields        the keys of the owner's profile questions by their names in lower case
     * @return the fields and questions
     */
    static Laid lay(
            List<FormSpot> spots,
            FormWidgets.Taken taken,
            double rightMargin,
            Map<String, TextFont> placeFonts,
            Map<String, String> fieldFamilies,
            Map<String, String> fields) {
        var layout = new FormLayout(fields, placeFonts, fieldFamilies);
        boolean firstNameApart = layout.firstNameApart(spots);
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
                widened,
                List.copyOf(layout.questions),
                List.copyOf(layout.recognised),
                List.copyOf(layout.unplaced),
                Map.copyOf(layout.samples));
    }

    private boolean firstNameApart(List<FormSpot> spots) {
        return spots.stream()
                .anyMatch(spot -> BuiltInPlaceholder.MEMBER_FIRST_NAME.key().equals(memberKey(spot, false)));
    }

    private String sampleOf(FormSpot spot, boolean firstNameApart) {
        String key = memberKey(spot, firstNameApart);
        return FormSamples.of(spot, key, questionKind(spot, labelOf(spot)));
    }

    private void place(FormSpot spot, FieldRect rect, boolean firstNameApart) {
        String key = keyOf(spot, rect, firstNameApart);
        samples.put(key, sampleOf(spot, firstNameApart));
    }

    /** Lays the field of a place and asks its question where it has one; returns the placeholder key it prints. */
    private String keyOf(FormSpot spot, FieldRect rect, boolean firstNameApart) {
        String label = labelOf(spot);
        if (spot.kind() == FormSpotKind.CHECK) {
            var question = ask(
                    label, TemplateQuestionKind.CHECK, "true".equals(spot.preset()) ? YES : "", QuestionLimits.NONE);
            laid.add(new PdfField(
                    PdfFieldKind.CHECK,
                    rect,
                    placeholder(question.placeholderKey()),
                    FONT_SIZE,
                    TextAlign.CENTER,
                    false,
                    null));
            return question.placeholderKey();
        }
        String key = memberKey(spot, firstNameApart);
        if (key != null) {
            recognised.add(label);
        } else {
            String preset = spot.preset();
            key = ask(label, questionKind(spot, label), preset == null ? "" : preset, spot.limits())
                    .placeholderKey();
        }
        laid.add(textField(rect, key, placeFonts.get(spot.tag())));
        return key;
    }

    /**
     * A text field printing in the font of its place: its size, the family chosen for its family, bold where
     * it is. It keeps its size, since a place of a form is only as wide as its blank, and a value written
     * into it by hand runs on along the line rather than getting smaller.
     */
    private PdfField textField(FieldRect rect, String key, @Nullable TextFont font) {
        String family = font == null || font.family() == null ? null : fieldFamilies.get(font.family());
        return new PdfField(
                PdfFieldKind.TEXT,
                rect,
                placeholder(key),
                font == null ? FONT_SIZE : font.size(),
                TextAlign.LEFT,
                false,
                null,
                family,
                font != null && font.bold() ? FontStyle.BOLD : FontStyle.REGULAR,
                false,
                false,
                null,
                false,
                null,
                true);
    }

    private static String placeholder(String key) {
        return "{{" + key + "}}";
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

    /** A text that reads like a day by its label is asked as one; every other place is asked as Word describes it. */
    private static TemplateQuestionKind questionKind(FormSpot spot, String label) {
        return switch (spot.kind()) {
            case TEXT -> readsLikeADay(label) ? TemplateQuestionKind.DATE : TemplateQuestionKind.TEXT;
            case DATE -> TemplateQuestionKind.DATE;
            case NUMBER -> TemplateQuestionKind.NUMBER;
            case CHOICE -> TemplateQuestionKind.CHOICE;
            case CHECK -> TemplateQuestionKind.CHECK;
        };
    }

    private TemplateQuestion ask(String label, TemplateQuestionKind kind, String suggestion, QuestionLimits limits) {
        var question = new TemplateQuestion(freeKey(label), label, kind, suggestion, false, limits);
        questions.add(question);
        return question;
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
        return field.withRect(new FieldRect(rect.page(), rect.x(), rect.y(), width, rect.height()));
    }
}
