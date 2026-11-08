/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The example values the preview of a Word form fills its places with, so the text in a field can be
 * compared with the text around it. They are as long as real values usually are: a name, a date, a short
 * text. A place keeps what the form holds in it already, a box is ticked and a list shows its first entry.
 */
final class FormSamples {
    /** What a ticked box says. */
    static final String YES = "Ja";

    private static final String DAY = "30.09.2026";

    private static final Map<String, String> MEMBER = Map.of(
            BuiltInPlaceholder.MEMBER_FIRST_NAME.key(), "Max",
            BuiltInPlaceholder.MEMBER_LAST_NAME.key(), "Mustermann",
            BuiltInPlaceholder.MEMBER_FULL_NAME.key(), "Max Mustermann",
            BuiltInPlaceholder.MEMBER_BIRTH_DATE.key(), "01.04.2012",
            BuiltInPlaceholder.MEMBER_JOIN_DATE.key(), "15.09.2020",
            BuiltInPlaceholder.MEMBER_AGE.key(), "14",
            BuiltInPlaceholder.STATION_NAME.key(), "Jugendfeuerwehr Musterstadt",
            BuiltInPlaceholder.GENERATED_BY.key(), "Erika Musterfrau",
            BuiltInPlaceholder.TODAY.key(), DAY);

    private FormSamples() {}

    /**
     * @param spot      a place of the form
     * @param memberKey the member's value it prints, or null where it is asked
     * @param kind      how it is asked where it is
     * @return what the preview fills it with
     */
    static String of(FormSpot spot, @Nullable String memberKey, TemplateQuestionKind kind) {
        if (memberKey != null) return MEMBER.getOrDefault(memberKey, "A-12345");
        String preset = spot.preset();
        return switch (kind) {
            case CHECK -> YES;
            case DATE -> DAY;
            case NUMBER -> preset == null ? "12" : preset;
            case CHOICE ->
                preset != null
                        ? preset
                        : spot.limits().choices().stream().findFirst().orElse("Auswahl");
            case TEXT -> preset == null ? "Beispieltext" : preset;
        };
    }
}
